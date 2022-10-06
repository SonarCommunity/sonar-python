/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2022 SonarSource SA
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
package org.sonar.python.checks.cdk;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.sonar.plugins.python.api.PythonCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.DictionaryLiteral;
import org.sonar.plugins.python.api.tree.DictionaryLiteralElement;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.KeyValuePair;
import org.sonar.plugins.python.api.tree.ListLiteral;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.NumericLiteral;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.UnpackingExpression;
import org.sonar.python.checks.Expressions;

import static org.sonar.python.checks.cdk.CdkPredicate.isListLiteral;

public class CdkUtils {

  private CdkUtils() {
  }

  public static Optional<Integer> getInt(Expression expression) {
    try {
      return Optional.of((int)((NumericLiteral) expression).valueAsLong());
    } catch (ClassCastException e) {
      return Optional.empty();
    }
  }

  public static Optional<String> getString(Expression expression) {
    try {
      return Optional.of(((StringLiteral) expression).trimmedQuotesValue());
    } catch (ClassCastException e) {
      return Optional.empty();
    }
  }

  public static Optional<CallExpression> getCall(Expression expression, String fqn) {
    if (expression.is(Tree.Kind.CALL_EXPR) && CdkPredicate.isFqn(fqn).test(expression)) {
      return Optional.of((CallExpression) expression);
    }
    return Optional.empty();
  }

  public static Optional<ListLiteral> getListExpression(ExpressionFlow expression) {
    return expression.getExpression(isListLiteral()).map(ListLiteral.class::cast);
  }

  public static Optional<DictionaryLiteral> getDictionary(Expression expression) {
    if (expression.is(Tree.Kind.DICTIONARY_LITERAL)) {
      return Optional.of((DictionaryLiteral) expression);
    }
    return Optional.empty();
  }

  /**
   * Resolve a particular argument of a call or get an empty optional if the argument is not set.
   */
  protected static Optional<ExpressionFlow> getArgument(SubscriptionContext ctx, CallExpression callExpression, String argumentName) {
    List<Argument> arguments = callExpression.arguments();
    Optional<ExpressionFlow> argument = arguments.stream()
      .filter(RegularArgument.class::isInstance)
      .map(RegularArgument.class::cast)
      .filter(regularArgument -> regularArgument.keywordArgument() != null)
      .filter(regularArgument -> argumentName.equals(regularArgument.keywordArgument().name()))
      .map(regularArgument -> ExpressionFlow.build(ctx, regularArgument.expression()))
      .findAny();

    if (argument.isEmpty() && arguments.stream().anyMatch(UnpackingExpression.class::isInstance)) {
      return Optional.of(new UnresolvedExpressionFlow(ctx));
    }
    return argument;
  }

  /**
   * Resolve the key and value of a dictionary element or get an empty optional if the element is an UnpackingExpression.
   */
  public static Optional<ResolvedKeyValuePair> getKeyValuePair(SubscriptionContext ctx, DictionaryLiteralElement element) {
    return element.is(Tree.Kind.KEY_VALUE_PAIR) ? Optional.of(ResolvedKeyValuePair.build(ctx, (KeyValuePair) element)) : Optional.empty();
  }

  /**
   * The expression flow represents the propagation of an expression.
   * It serves as a resolution path from the use of the expression up to the value assignment.
   * For example, if the value of an argument expression did not occur directly in the function call, the value can be tracked back.
   * The flow allows on the one hand to check the assigned value
   * and on the other hand to display the assignment path of the relevant value when creating an issue.
   */
  static class ExpressionFlow {

    private static final String TAIL_MESSAGE = "Propagated setting.";

    private final SubscriptionContext ctx;
    private final Deque<Expression> locations;

    private ExpressionFlow(SubscriptionContext ctx, Deque<Expression> locations) {
      this.ctx = ctx;
      this.locations = locations;
    }

    protected static ExpressionFlow build(SubscriptionContext ctx, Expression expression) {
      Deque<Expression> locations = new LinkedList<>();
      resolveLocations(expression, locations);
      return new ExpressionFlow(ctx, locations);
    }

    static void resolveLocations(Expression expression, Deque<Expression> locations) {
      locations.add(expression);
      if (expression.is(Tree.Kind.NAME)) {
        Expression singleAssignedValue = Expressions.singleAssignedValue(((Name) expression));
        if (singleAssignedValue != null && !locations.contains(singleAssignedValue)) {
          resolveLocations(singleAssignedValue, locations);
        }
      }
    }

    public void addIssue(String primaryMessage) {
      PythonCheck.PreciseIssue issue = ctx.addIssue(locations.getFirst().parent(), primaryMessage);
      locations.stream().skip(1).forEach(expression -> issue.secondary(expression.parent(), TAIL_MESSAGE));
    }

    public void addIssueIf(Predicate<Expression> predicate, String primaryMessage) {
      if (hasExpression(predicate)) {
        addIssue(primaryMessage);
      }
    }

    public void addIssueIf(Predicate<Expression> predicate, String primaryMessage, CallExpression call) {
      if (hasExpression(predicate)) {
        ctx.addIssue(call.callee(), primaryMessage);
      }
    }

    public boolean hasExpression(Predicate<Expression> predicate) {
      return locations.stream().anyMatch(predicate);
    }

    public Optional<Expression> getExpression(Predicate<Expression> predicate) {
      return locations.stream().filter(predicate).findFirst();
    }

    public Deque<Expression> locations() {
      return locations;
    }

    public Expression getLast() {
      return locations().getLast();
    }
  }

  /**
   * In the case of unpacking expression, we cannot generate flows at the moment.
   * However, to avoid a wrong interpretation of the unpacked expression in the context of absent arguments,
   * an alternative dummy must be returned, which should not lead to false positives.
   * The resolving of such expressions can be improved by <a href="https://sonarsource.atlassian.net/browse/SONARPY-1164">SONARPY-1164</a> if necessary.
   */
  static class UnresolvedExpressionFlow extends ExpressionFlow {

    private UnresolvedExpressionFlow(SubscriptionContext ctx) {
      super(ctx, new LinkedList<>());
    }
  }

  /**
   * Dataclass to store a resolved KeyValuePair structure
   */
  static class ResolvedKeyValuePair {

    final ExpressionFlow key;
    final ExpressionFlow value;

    private ResolvedKeyValuePair(ExpressionFlow key, ExpressionFlow value) {
      this.key = key;
      this.value = value;
    }

    static ResolvedKeyValuePair build(SubscriptionContext ctx, KeyValuePair pair) {
      return new ResolvedKeyValuePair(ExpressionFlow.build(ctx, pair.key()), ExpressionFlow.build(ctx, pair.value()));
    }
  }
}
