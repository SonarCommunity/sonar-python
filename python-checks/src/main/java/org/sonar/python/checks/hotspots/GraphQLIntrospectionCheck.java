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
package org.sonar.python.checks.hotspots;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.ExpressionList;
import org.sonar.plugins.python.api.tree.ListLiteral;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tuple;
import org.sonar.python.tree.TreeUtils;

@Rule(key = "S6786")
public class GraphQLIntrospectionCheck extends PythonSubscriptionCheck {

  private static final Set<String> GRAPHQL_VIEWS_FQNS = Set.of(
    "flask_graphql.GraphQLView.as_view",
    "graphql_server.flask.GraphQLView.as_view");

  private static final Set<String> SAFE_VALIDATION_RULE_FQNS = Set.of(
    "graphene.validation.DisableIntrospection",
    "graphql.validation.NoSchemaIntrospectionCustomRule");

  private static final String MESSAGE = "Disable introspection on this \"GraphQL\" server endpoint.";

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, GraphQLIntrospectionCheck::checkGraphQLIntrospection);
  }

  private static void checkGraphQLIntrospection(SubscriptionContext ctx) {
    CallExpression callExpression = (CallExpression) ctx.syntaxNode();
    Optional.ofNullable(callExpression.calleeSymbol())
      .map(Symbol::fullyQualifiedName)
      .filter(GRAPHQL_VIEWS_FQNS::contains)
      .filter(fqn -> !hasSafeMiddlewares(callExpression.arguments()))
      .filter(fqn -> !hasSafeValidationRules(callExpression.arguments()))
      .ifPresent(fqn -> ctx.addIssue(callExpression.callee(), MESSAGE));
  }

  private static boolean hasSafeMiddlewares(List<Argument> arguments) {
    RegularArgument argument = TreeUtils.argumentByKeyword("middleware", arguments);
    if (argument == null) {
      return false;
    }

    return extractArgumentValues(argument)
      .map(values -> !values.isEmpty() && expressionsNameContainIntrospection(values))
      .orElse(true);
  }

  private static boolean hasSafeValidationRules(List<Argument> arguments) {
    RegularArgument argument = TreeUtils.argumentByKeyword("validation_rules", arguments);
    if (argument == null) {
      return false;
    }

    return extractArgumentValues(argument)
      .map(values -> !values.isEmpty() &&
        (expressionsNameContainIntrospection(values) || expressionsContainsSafeRuleFQN(values)))
      .orElse(true);
  }

  private static Optional<List<Expression>> extractArgumentValues(RegularArgument argument) {
    return Optional.of(argument)
      .map(RegularArgument::expression)
      .flatMap(GraphQLIntrospectionCheck::expressionsFromListOrTuple);
  }

  private static Optional<List<Expression>> expressionsFromListOrTuple(Expression expression) {
    return TreeUtils.toOptionalInstanceOf(ListLiteral.class, expression)
      .map(ListLiteral::elements)
      .map(ExpressionList::expressions)
      .or(() -> TreeUtils.toOptionalInstanceOf(Tuple.class, expression)
        .map(Tuple::elements));
  }

  private static boolean expressionsNameContainIntrospection(List<Expression> expressions) {
    return expressions.stream()
      .map(GraphQLIntrospectionCheck::nameFromIdentifierOrCallExpression)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(String::toUpperCase)
      .anyMatch(name -> name.contains("INTROSPECTION"));
  }

  private static boolean expressionsContainsSafeRuleFQN(List<Expression> expressions) {
    return expressions.stream()
      .map(TreeUtils::getSymbolFromTree)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(Symbol::fullyQualifiedName)
      .filter(Objects::nonNull)
      .anyMatch(SAFE_VALIDATION_RULE_FQNS::contains);
  }

  private static Optional<String> nameFromIdentifierOrCallExpression(Expression expression) {
    return Optional.ofNullable(TreeUtils.nameFromExpression(expression))
      .or(() -> TreeUtils.toOptionalInstanceOf(CallExpression.class, expression)
        .map(CallExpression::callee)
        .map(TreeUtils::nameFromExpression));
  }
}
