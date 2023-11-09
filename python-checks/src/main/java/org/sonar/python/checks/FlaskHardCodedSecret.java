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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.DictionaryLiteral;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.ExpressionList;
import org.sonar.plugins.python.api.tree.KeyValuePair;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.SubscriptionExpression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.python.tree.TreeUtils;


public abstract class FlaskHardCodedSecret extends PythonSubscriptionCheck {
  private static final String MESSAGE = "Don't disclose %s secret keys.";
  private static final String SECONDARY_MESSAGE = "Assignment to sensitive property.";
  private static final Set<String> FLASK_APP_CONFIG_QUALIFIER_FQNS = Set.of(
    "flask.app.Flask.config",
    "flask.globals.current_app.config"
  );

  protected abstract String getSecretKeyKeyword();

  protected abstract String getSecretKeyType();

  protected abstract Set<String> getFlaskAppConfigQualifierFqns();

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, this::verifyCallExpression);
    context.registerSyntaxNodeConsumer(Tree.Kind.ASSIGNMENT_STMT, this::verifyAssignmentStatement);
  }

  private void verifyCallExpression(SubscriptionContext ctx) {
    CallExpression callExpression = (CallExpression) ctx.syntaxNode();
    Optional.of(callExpression)
      .map(CallExpression::callee)
      .flatMap(TreeUtils.toOptionalInstanceOfMapper(QualifiedExpression.class))
      .filter(qualiExpr -> "update".equals(qualiExpr.name().name()))
      .map(QualifiedExpression::qualifier)
      .flatMap(TreeUtils.toOptionalInstanceOfMapper(QualifiedExpression.class))
      .map(QualifiedExpression::name)
      .map(Name::symbol)
      .map(Symbol::fullyQualifiedName)
      .filter(FLASK_APP_CONFIG_QUALIFIER_FQNS::contains)
      .ifPresent(fqn -> verifyUpdateCallArgument(ctx, callExpression));
  }

  private void verifyUpdateCallArgument(SubscriptionContext ctx, CallExpression callExpression) {
    Optional.of(callExpression.arguments())
      .filter(arguments -> arguments.size() == 1)
      .map(arguments -> arguments.get(0))
      .flatMap(TreeUtils.toOptionalInstanceOfMapper(RegularArgument.class))
      .map(RegularArgument::expression)
      .map(FlaskHardCodedSecret::getAssignedValue)
      .filter(this::isIllegalDictArgument)
      .ifPresent(expr -> ctx.addIssue(callExpression, String.format(MESSAGE, this.getSecretKeyType())));

  }

  private static Expression getAssignedValue(Expression expression) {
    if (expression.is(Tree.Kind.NAME)) {
      return Expressions.singleAssignedValue((Name) expression);
    }
    return expression;
  }

  private boolean isIllegalDictArgument(Expression expression) {
    if (expression.is(Tree.Kind.CALL_EXPR)) {
      return isCallToDictConstructor((CallExpression) expression) && hasIllegalKeywordArgument((CallExpression) expression);
    } else if (expression.is(Tree.Kind.DICTIONARY_LITERAL)) {
      return hasIllegalKeyValuePair((DictionaryLiteral) expression);
    }
    return false;
  }

  private static boolean isCallToDictConstructor(CallExpression callExpression) {
    return Optional.of(callExpression)
      .map(CallExpression::callee)
      .flatMap(TreeUtils.toOptionalInstanceOfMapper(Name.class))
      .map(Name::symbol)
      .map(Symbol::fullyQualifiedName)
      .filter("dict"::equals)
      .isPresent();
  }

  private boolean hasIllegalKeyValuePair(DictionaryLiteral dictionaryLiteral) {
    return dictionaryLiteral.elements().stream()
      .filter(KeyValuePair.class::isInstance)
      .map(KeyValuePair.class::cast)
      .anyMatch(this::isIllegalKeyValuePair);
  }

  private boolean isIllegalKeyValuePair(KeyValuePair keyValuePair) {
    return Optional.of(keyValuePair.key())
      .filter(StringLiteral.class::isInstance)
      .map(StringLiteral.class::cast)
      .map(StringLiteral::trimmedQuotesValue)
      .filter(this.getSecretKeyKeyword()::equals)
      .isPresent() && isStringLiteral(keyValuePair.value());
  }

  private boolean hasIllegalKeywordArgument(CallExpression callExpression) {
    return Optional.ofNullable(TreeUtils.argumentByKeyword(this.getSecretKeyKeyword(), callExpression.arguments()))
      .map(RegularArgument::expression)
      .filter(FlaskHardCodedSecret::isStringLiteral)
      .isPresent();
  }

  private void verifyAssignmentStatement(SubscriptionContext ctx) {
    AssignmentStatement assignmentStatementTree = (AssignmentStatement) ctx.syntaxNode();
    if (!isStringLiteral(assignmentStatementTree.assignedValue())) {
      return;
    }
    List<Expression> expressionList = assignmentStatementTree.lhsExpressions().stream()
      .map(ExpressionList::expressions)
      .flatMap(List::stream)
      .filter(this::isSensitiveProperty)
      .collect(Collectors.toList());
    if (!expressionList.isEmpty()) {
      PreciseIssue issue = ctx.addIssue(assignmentStatementTree.assignedValue(), String.format(MESSAGE, this.getSecretKeyType()));
      expressionList.forEach(expr -> issue.secondary(expr, SECONDARY_MESSAGE));
    }
  }

  private boolean isSensitiveProperty(Expression expression) {
    if (expression.is(Tree.Kind.SUBSCRIPTION)) {
      return Optional.of((SubscriptionExpression) expression)
        .map(SubscriptionExpression::object)
        .flatMap(TreeUtils.toOptionalInstanceOfMapper(QualifiedExpression.class))
        .map(QualifiedExpression::symbol)
        .map(Symbol::fullyQualifiedName)
        .filter(FLASK_APP_CONFIG_QUALIFIER_FQNS::contains)
        .map(fqn -> ((SubscriptionExpression) expression).subscripts())
        .map(ExpressionList::expressions)
        .filter(list -> list.size() == 1)
        .map(list -> list.get(0))
        .map(FlaskHardCodedSecret::getAssignedValue)
        .flatMap(TreeUtils.toOptionalInstanceOfMapper(StringLiteral.class))
        .map(StringLiteral::trimmedQuotesValue)
        .filter(this.getSecretKeyKeyword()::equals)
        .isPresent();
    } else if (expression.is(Tree.Kind.QUALIFIED_EXPR)) {
      return Optional.of((QualifiedExpression) expression)
        .map(QualifiedExpression::symbol)
        .map(Symbol::fullyQualifiedName)
        .filter(this.getFlaskAppConfigQualifierFqns()::contains)
        .isPresent();
    }
    return false;
  }

  private static boolean isStringLiteral(@Nullable Expression expr) {
    if (expr == null) {
      return false;
    } else if (expr.is(Tree.Kind.NAME)) {
      return isStringLiteral(Expressions.singleAssignedValue((Name) expr));
    } else {
      return expr.is(Tree.Kind.STRING_LITERAL);
    }
  }
}