/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.BinaryExpression;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.DictionaryLiteral;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.KeyValuePair;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.NumericLiteral;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.UnpackingExpression;
import org.sonar.python.tree.RegularArgumentImpl;
import static java.util.Optional.ofNullable;

// https://jira.sonarsource.com/browse/SONARPY-357
// https://jira.sonarsource.com/browse/RSPEC-4830
// https://jira.sonarsource.com/browse/MMF-1872
@Rule(key = "S4830")
public class VerifiedSslTlsCertificateCheck extends PythonSubscriptionCheck {

  private static final String VERIFY_NONE = Fqn.ssl("VERIFY_NONE");

  /**
   * Searches for `set_verify` invocations on instances of `OpenSSL.SSL.Context`,
   * extracts the flags from the first argument, checks that the combination of flags is secure.
   *
   * @param context {@inheritDoc}
   */
  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, VerifiedSslTlsCertificateCheck::sslSetVerifyCheck);
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, VerifiedSslTlsCertificateCheck::requestsCheck);
  }

  /** Fully qualified name of the <code>set_verify</code> used in <code>sslSetVerifyCheck</code>. */
  private static final String SET_VERIFY = Fqn.context("set_verify");

  /**
   * Check for the <code>OpenSSL.SSL.Context.set_verify</code> flag settings.
   *
   * @param subscriptionContext the subscription context passed by <code>Context.registerSyntaxNodeConsumer</code>.
   */
  private static void sslSetVerifyCheck(SubscriptionContext subscriptionContext) {

    CallExpression callExpr = (CallExpression) subscriptionContext.syntaxNode();

    boolean isSetVerifyInvocation =
      ofNullable(callExpr.calleeSymbol()).map(Symbol::fullyQualifiedName).filter(SET_VERIFY::equals).isPresent();

    if (isSetVerifyInvocation) {
      List<Argument> args = callExpr.arguments();
      if (!args.isEmpty()) {
        Tree flagsArgument = args.get(0);
        if (flagsArgument.is(Tree.Kind.REGULAR_ARGUMENT)) {
          Set<QualifiedExpression> flags = extractFlags(((RegularArgumentImpl) flagsArgument).expression());
          checkFlagSettings(flags).ifPresent(issue -> subscriptionContext.addIssue(issue.token, issue.message));
        }
      }
    }
  }

  /** Helper methods for generating FQNs frequently used in this check. */
  private static class Fqn {
    private static String context(@SuppressWarnings("SameParameterValue") String method) {
      return ssl("Context." + method);
    }

    private static String ssl(String property) {
      return "OpenSSL.SSL." + property;
    }
  }

  /**
   * Recursively deconstructs binary trees of expressions separated with `|`-ors,
   * and collects the leafs that look like qualified expressions.
   */
  private static HashSet<QualifiedExpression> extractFlags(Tree flagsSubexpr) {
    if (flagsSubexpr.is(Tree.Kind.QUALIFIED_EXPR)) {
      // Base case: e.g. `SSL.VERIFY_NONE`
      return new HashSet<>(Collections.singletonList((QualifiedExpression) flagsSubexpr));
    } else if (flagsSubexpr.is(Tree.Kind.BITWISE_OR)) {
      // recurse into left and right branch
      BinaryExpression orExpr = (BinaryExpression) flagsSubexpr;
      HashSet<QualifiedExpression> flags = extractFlags(orExpr.leftOperand());
      flags.addAll(extractFlags(orExpr.rightOperand()));
      return flags;
    } else {
      // failed to interpret. Ignore leaf.
      return new HashSet<>();
    }
  }

  /**
   * Checks whether a combination of flags is valid,
   * optionally returns a message and a token if there is something wrong.
   */
  private static Optional<IssueReport> checkFlagSettings(Set<QualifiedExpression> flags) {
    for (QualifiedExpression qe : flags) {
      Symbol symb = qe.symbol();
      if (symb != null) {
        String fqn = symb.fullyQualifiedName();
        if (VERIFY_NONE.equals(fqn)) {
          return Optional.of(new IssueReport(
            "Omitting the check of the peer certificate is dangerous.",
            qe.lastToken()));
        }
      }
    }
    return Optional.empty();
  }

  /** Message and a token closest to the problematic position. Glorified <code>Pair&lt;A,B&gt;</code>. */
  private static class IssueReport {
    final String message;
    final Token token;

    private IssueReport(String message, Token token) {
      this.message = message;
      this.token = token;
    }
  }

  /**
   * Set of FQNs of methods in <code>requests</code>-module that have the vulnerable <code>verify</code>-option.
   */
  private static Set<String> requestsMethods = Stream
    .of("request", "get", "head", "post", "put", "delete", "patch", "options")
    .map(method -> "requests." + method)
    .collect(Collectors.toSet());

  private static void requestsCheck(SubscriptionContext subscriptionContext) {
    CallExpression callExpr = (CallExpression) subscriptionContext.syntaxNode();
    boolean isVulnerableMethod =
      ofNullable(callExpr.calleeSymbol()).map(Symbol::fullyQualifiedName).filter(requestsMethods::contains).isPresent();

    if(isVulnerableMethod) {
      // Apparently, this is as close as one can get to short-circuiting `opt1.or(opt2)` before Java 9.
      // https://stackoverflow.com/a/24600021/2707792
      // All it does is first attempting to get `verify = rhs` from named parameter, and then `verify: rhs` from kwargs.
      // In any case, we want the `rhs`, so we can check later whether it's a falsy value.
      Optional<Expression> verifyRhs =
        searchVerifyAssignment(callExpr).map(Optional::of).orElseGet(() -> searchVerifyInKwargs(callExpr));

      verifyRhs.ifPresent(rhs -> {
        if (Expressions.isFalsy(rhs) || isFalsyCollection(rhs)) {
          subscriptionContext.addIssue(
            rhs.firstToken(),
            rhs.lastToken(),
            "Disabling certificate verification is dangerous."
          );
        }
      });
    }
  }

  /**
   * Attempts to find the expression in <code>verify = expr</code> explicitly keyworded parameter assignment.
   *
   * @return The <code>expr</code> part on the right hand side of the assignment.
   */
  private static Optional<Expression> searchVerifyAssignment(CallExpression callExpr) {
    for (Argument a : callExpr.arguments()) {
      if (a instanceof RegularArgumentImpl) {
        RegularArgumentImpl regArg = (RegularArgumentImpl) a;
        Name keywordArgument = regArg.keywordArgument();
        if (keywordArgument != null && "verify".equals(keywordArgument.name())) {
          return Optional.of(regArg.expression());
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Attempts to find the <code>rhs</code> in some definition <code>kwargs = { 'verify': rhs }</code>
   * of <code>kwargs</code> used in the arguments of the given <code>callExpression</code>.
   */
  private static Optional<Expression> searchVerifyInKwargs(CallExpression callExpression) {
    return callExpression
      .arguments()
      .stream()
      .filter(UnpackingExpression.class::isInstance)
      .map(arg -> ((UnpackingExpression) arg).expression())
      .filter(Name.class::isInstance)
      .map(name -> Expressions.singleAssignedValue((Name) name))
      .filter(DictionaryLiteral.class::isInstance)
      .flatMap(dict -> ((DictionaryLiteral) dict).elements().stream()
        .filter(KeyValuePair.class::isInstance)
        .map(KeyValuePair.class::cast)
        .filter(kvp -> Optional.of(kvp.key())
          .filter(StringLiteral.class::isInstance)
          .map(StringLiteral.class::cast)
          .filter(strLit -> "verify".equals(strLit.trimmedQuotesValue()))
          .isPresent()
        )
        .map(KeyValuePair::value)
      ).findFirst();
  }



  /**
   * Checks whether an expression is obviously a falsy collection (e.g. <code>set()</code> or <code>range(0)</code>).
   */
  private static boolean isFalsyCollection(Expression expr) {
    if (expr instanceof CallExpression) {
      CallExpression callExpr = (CallExpression) expr;
      Optional<String> fqnOpt = Optional.ofNullable(callExpr.calleeSymbol()).map(Symbol::fullyQualifiedName);
      if (fqnOpt.isPresent()) {
        String fqn = fqnOpt.get();
        return isFalsyNoArgCollectionConstruction(callExpr, fqn) || isFalsyRange(callExpr, fqn);
      }
    }
    return false;
  }

  /** FQNs of collection constructors that yield a falsy collection if invoked without arguments. */
  private static final Set<String> NO_ARG_FALSY_COLLECTION_CONSTRUCTORS = new HashSet<>(Arrays.asList(
    "set", "list", "dict"
  ));

  /** Detects expressions like <code>dict()</code> or <code>list()</code>. */
  private static boolean isFalsyNoArgCollectionConstruction(CallExpression callExpr, String fqn) {
    return NO_ARG_FALSY_COLLECTION_CONSTRUCTORS.contains(fqn) && callExpr.arguments().isEmpty();
  }

  private static boolean isFalsyRange(CallExpression callExpr, String fqn) {
    if ("range".equals(fqn) && callExpr.arguments().size() == 1) {
      // `range(0)` is also falsy
      Argument firstArg = callExpr.arguments().get(0);
      if (firstArg instanceof RegularArgument) {
        RegularArgument regArg = (RegularArgument) firstArg;
        Expression firstArgExpr = regArg.expression();
        if (firstArgExpr.is(Tree.Kind.NUMERIC_LITERAL)) {
          NumericLiteral num = (NumericLiteral) firstArgExpr;
          return num.valueAsLong() == 0L;
        }
      }
    }
    return false;
  }
}
