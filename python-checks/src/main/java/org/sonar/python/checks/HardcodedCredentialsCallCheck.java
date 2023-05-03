/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.python.checks;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.tree.TreeUtils;

@Rule(key = "S6437")
public class HardcodedCredentialsCallCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Revoke and change this password, as it is compromised.";
  private final Map<String, CredentialMethod> methods;

  public HardcodedCredentialsCallCheck() {
    methods = new CredentialMethodsLoader().load();
  }

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, this::processCallExpression);
  }

  private void processCallExpression(SubscriptionContext ctx) {
    Optional.of(ctx.syntaxNode())
      .filter(CallExpression.class::isInstance)
      .map(CallExpression.class::cast)
      .filter(this::callHasToBeChecked)
      .ifPresent(call -> {
        var method = getMethod(call);
        method.indices()
          .forEach(argumentIndex -> {
            var argumentName = method.args().get(argumentIndex);
            var argument = TreeUtils.nthArgumentOrKeyword(argumentIndex, argumentName, call.arguments());
            if (argument != null) {
              checkArgument(ctx, argument);
            }
          });
      });
  }

  private static void checkArgument(SubscriptionContext ctx, RegularArgument argument) {
    var argExp = argument.expression();
    if (argExp.is(Tree.Kind.STRING_LITERAL)) {
      Optional.of(argExp)
        .filter(StringLiteral.class::isInstance)
        .map(StringLiteral.class::cast)
        .map(StringLiteral::trimmedQuotesValue)
        .filter(Predicate.not(String::isEmpty))
        .ifPresent(value -> ctx.addIssue(argument, MESSAGE));
    } else if (argExp.is(Tree.Kind.NAME)) {
      findAssignment((Name) argExp)
        .filter(StringLiteral.class::isInstance)
        .map(StringLiteral.class::cast)
        .filter(string -> Optional.of(string)
          .map(StringLiteral::trimmedQuotesValue)
          .filter(Predicate.not(String::isEmpty))
          .isPresent())
        .ifPresent(assignedValue -> ctx.addIssue(argument, MESSAGE).secondary(assignedValue, MESSAGE));
    }
  }

  private static Optional<Tree> findAssignment(Name name) {
    return Optional.of(name)
      .map(Expressions::singleAssignedValue)
      .map(assignedValue -> {
        if (assignedValue.is(Tree.Kind.NAME)) {
          return findAssignment((Name) assignedValue).orElse(null);
        } else {
          return assignedValue;
        }
      });
  }

  private Boolean callHasToBeChecked(CallExpression call) {
    return Optional.of(call)
      .map(CallExpression::calleeSymbol)
      .map(Symbol::fullyQualifiedName)
      .map(methods::containsKey)
      .orElse(false);
  }

  private CredentialMethod getMethod(CallExpression call) {
    return Optional.of(call)
      .map(CallExpression::calleeSymbol)
      .map(Symbol::fullyQualifiedName)
      .map(methods::get)
      .orElse(null);
  }

  public static class CredentialMethod {
    private String name;
    private List<String> args;
    private List<Integer> indices;

    public String name() {
      return name;
    }

    public List<String> args() {
      return args;
    }

    public List<Integer> indices() {
      return indices;
    }
  }

  private static class CredentialMethodsLoader {
    private static final String METHODS_RESOURCE_PATH = "/org/sonar/python/checks/hardcoded_credentials_call_check_meta.json";
    private final Gson gson;

    private CredentialMethodsLoader() {
      gson = new Gson();
    }

    private Map<String, CredentialMethod> load() {
      try (var is = HardcodedCredentialsCallCheck.class.getResourceAsStream(METHODS_RESOURCE_PATH)) {
        return Optional.ofNullable(is)
          .map(InputStreamReader::new)
          .map(r -> gson.fromJson(r, CredentialMethod[].class))
          .stream()
          .flatMap(Stream::of)
          .collect(Collectors.toMap(CredentialMethod::name, Function.identity()));
      } catch (IOException e) {
        throw new IllegalStateException("Unable to read methods metadata from " + METHODS_RESOURCE_PATH, e);
      }
    }
  }

}
