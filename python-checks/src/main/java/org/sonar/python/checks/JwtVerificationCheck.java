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

import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.DictionaryLiteral;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.KeyValuePair;
import org.sonar.plugins.python.api.tree.ListLiteral;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tree.Kind;
import org.sonar.plugins.python.api.tree.Tuple;
import org.sonar.python.checks.utils.Expressions;
import org.sonar.python.tree.TreeUtils;

@Rule(key = "S5659")
public class JwtVerificationCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Don't use a JWT token without verifying its signature.";

  // https://github.com/davedoesdev/python-jwt
  // "From version 2.0.1 the namespace has changed from jwt to python_jwt, in order to avoid conflict with PyJWT"
  private static final Set<String> PROCESS_JWT_FQNS = Set.of(
    "python_jwt.process_jwt",
    "jwt.process_jwt");

  private static final Set<String> VERIFY_JWT_FQNS = Set.of(
    "python_jwt.verify_jwt",
    "jwt.verify_jwt");

  private static final Set<String> WHERE_VERIFY_KWARG_SHOULD_BE_TRUE_FQNS = Set.of(
    "jwt.decode",
    "jose.jws.verify");

  private static final Set<String> UNVERIFIED_FQNS = Set.of(
    "jwt.get_unverified_header",
    "jose.jwt.get_unverified_header",
    "jose.jwt.get_unverified_headers",
    "jose.jws.get_unverified_header",
    "jose.jws.get_unverified_headers",
    "jose.jwt.get_unverified_claims",
    "jose.jws.get_unverified_claims"
  );

  private static final String VERIFY_SIGNATURE_KEYWORD = "verify_signature";

  public static final Set<String> VERIFY_SIGNATURE_OPTION_SUPPORTING_FUNCTION_FQNS = Set.of("jose.jwt.decode", "jwt.decode");

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Kind.CALL_EXPR, JwtVerificationCheck::verifyCallExpression);
  }

  private static void verifyCallExpression(SubscriptionContext ctx) {
    CallExpression call = (CallExpression) ctx.syntaxNode();

    Symbol calleeSymbol = call.calleeSymbol();
    if (calleeSymbol == null || calleeSymbol.fullyQualifiedName() == null) {
      return;
    }

    String calleeFqn = calleeSymbol.fullyQualifiedName();
    if (WHERE_VERIFY_KWARG_SHOULD_BE_TRUE_FQNS.contains(calleeFqn)) {
      RegularArgument verifyArg = TreeUtils.argumentByKeyword("verify", call.arguments());
      if (verifyArg != null && Expressions.isFalsy(verifyArg.expression())) {
        ctx.addIssue(verifyArg, MESSAGE);
        return;
      }
    } else if (PROCESS_JWT_FQNS.contains(calleeFqn)) {
      Optional.ofNullable(TreeUtils.firstAncestorOfKind(call, Kind.FILE_INPUT, Kind.FUNCDEF))
        .filter(scriptOrFunction -> !TreeUtils.hasDescendant(scriptOrFunction, JwtVerificationCheck::isCallToVerifyJwt))
        .ifPresent(scriptOrFunction -> ctx.addIssue(call, MESSAGE));
    } else if (UNVERIFIED_FQNS.contains(calleeFqn)) {
      Optional.ofNullable(TreeUtils.nthArgumentOrKeyword(0, "", call.arguments()))
        .flatMap(TreeUtils.toOptionalInstanceOfMapper(RegularArgument.class))
        .map(RegularArgument::expression)
        .ifPresent(argument -> ctx.addIssue(argument, MESSAGE));
    }
    if (VERIFY_SIGNATURE_OPTION_SUPPORTING_FUNCTION_FQNS.contains(calleeFqn)) {
      Optional.ofNullable(TreeUtils.argumentByKeyword("options", call.arguments()))
        .map(RegularArgument::expression)
        .filter(JwtVerificationCheck::isListOrDictWithSensitiveEntry)
        .ifPresent(expression -> ctx.addIssue(expression, MESSAGE));
    }
  }

  private static boolean isListOrDictWithSensitiveEntry(@Nullable Expression expression) {
    if (expression == null) {
      return false;
    } else if (expression.is(Tree.Kind.NAME)) {
      return isListOrDictWithSensitiveEntry(Expressions.singleAssignedNonNameValue((Name) expression));
    } else if (expression.is(Kind.DICTIONARY_LITERAL)) {
      return hasTrueVerifySignatureEntry((DictionaryLiteral) expression);
    } else if (expression.is(Kind.LIST_LITERAL)) {
      return hasTrueVerifySignatureEntry((ListLiteral) expression);
    } else if (expression.is(Kind.CALL_EXPR)) {
      return isCallToDict((CallExpression) expression)
        && hasIllegalDictKWArgument((CallExpression) expression);
    }
    return false;
  }

  private static boolean hasIllegalDictKWArgument(CallExpression expression) {
    return Optional.of(expression)
      .map(CallExpression::arguments)
      .map(arguments -> TreeUtils.argumentByKeyword(VERIFY_SIGNATURE_KEYWORD, arguments))
      .map((RegularArgument::expression))
      .filter(Expressions::isFalsy)
      .isPresent();
  }

  private static boolean isCallToDict(CallExpression expression) {
    return Optional.of(expression)
      .map(CallExpression::calleeSymbol)
      .map(Symbol::fullyQualifiedName)
      .filter("dict"::equals).isPresent();
  }

  private static boolean hasTrueVerifySignatureEntry(DictionaryLiteral dictionaryLiteral) {
    return dictionaryLiteral.elements().stream()
      .filter(KeyValuePair.class::isInstance)
      .map(KeyValuePair.class::cast)
      .filter(keyValuePair -> isSensitiveKey(keyValuePair.key()))
      .map(KeyValuePair::value)
      .anyMatch(Expressions::isFalsy);
  }

  private static boolean hasTrueVerifySignatureEntry(ListLiteral listLiteral) {
    return listLiteral.elements().expressions().stream()
      .filter(Tuple.class::isInstance)
      .map(Tuple.class::cast)
      .map(Tuple::elements)
      .filter(list -> list.size() == 2)
      .filter(list -> isSensitiveKey(list.get(0)))
      .map(list -> list.get(1))
      .anyMatch(Expressions::isFalsy);
  }

  private static boolean isSensitiveKey(Expression key) {
    return key.is(Kind.STRING_LITERAL) && VERIFY_SIGNATURE_KEYWORD.equals(((StringLiteral) key).trimmedQuotesValue());
  }

  private static boolean isCallToVerifyJwt(Tree t) {
    return TreeUtils.toOptionalInstanceOf(CallExpression.class, t)
      .map(CallExpression::calleeSymbol)
      .map(Symbol::fullyQualifiedName)
      .filter(VERIFY_JWT_FQNS::contains)
      .isPresent();
  }

}
