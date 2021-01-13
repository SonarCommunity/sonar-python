/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2021 SonarSource SA
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.BinaryExpression;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.DictionaryLiteral;
import org.sonar.plugins.python.api.tree.DictionaryLiteralElement;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.KeyValuePair;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;

@Rule(key = "S3457")
public class StringFormatCorrectnessCheck extends AbstractStringFormatCheck {

  private static final Set<String> LOGGER_FULL_NAMES = new HashSet<>(Arrays.asList(
    "logging.debug", "logging.info", "logging.warning", "logging.error", "logging.critical",
    "logging.Logger.debug", "logging.Logger.info", "logging.Logger.warning", "logging.Logger.error", "logging.Logger.critical"
  ));
  private static final Set<String> LOGGER_METHOD_NAMES = new HashSet<>(Arrays.asList(
    "debug", "info", "warning", "error", "critical"
  ));

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.MODULO, this::checkPrintfStyle);
    context.registerSyntaxNodeConsumer(Tree.Kind.CALL_EXPR, this::checkCallExpression);
  }

  private void checkCallExpression(SubscriptionContext ctx) {
    CallExpression callExpression = (CallExpression) ctx.syntaxNode();
    if (isCallToLog(callExpression)) {
      checkLoggerLog(ctx, callExpression);
    } else {
      this.checkStrFormatStyle(ctx);
    }
  }

  private static boolean isCallToLog(CallExpression callExpression) {
    Symbol symbol = callExpression.calleeSymbol();

    if (symbol != null && LOGGER_FULL_NAMES.contains(symbol.fullyQualifiedName())) {
      return true;
    }

    return isQualifiedCallToLogger(callExpression);
  }

  private static boolean isQualifiedCallToLogger(CallExpression callExpression) {
    if (!callExpression.callee().is(Tree.Kind.QUALIFIED_EXPR)) {
      return false;
    }

    QualifiedExpression qualifiedExpression = (QualifiedExpression) callExpression.callee();
    if (!LOGGER_METHOD_NAMES.contains(qualifiedExpression.name().name())) {
      return false;
    }

    Expression qualifier = qualifiedExpression.qualifier();
    if (!qualifier.is(Tree.Kind.NAME)) {
      return false;
    }

    Expression singleAssignedValue = Expressions.singleAssignedValue((Name) qualifier);
    if (singleAssignedValue == null || !singleAssignedValue.is(Tree.Kind.CALL_EXPR)) {
      return false;
    }

    CallExpression call = (CallExpression) singleAssignedValue;
    Symbol symbol = call.calleeSymbol();
    if (symbol == null) {
      return false;
    }

    return "logging.getLogger".equals(symbol.fullyQualifiedName());
  }

  private static void checkLoggerLog(SubscriptionContext ctx, CallExpression callExpression) {
    if (callExpression.arguments().isEmpty() || callExpression.arguments().stream().anyMatch(argument -> !argument.is(Tree.Kind.REGULAR_ARGUMENT))) {
      return;
    }

    List<RegularArgument> arguments = callExpression.arguments().stream()
      .map(RegularArgument.class::cast)
      .filter(argument -> argument.keywordArgument() == null)
      .collect(Collectors.toList());

    if (arguments.isEmpty()) {
      // Out of scope
      return;
    }

    Expression firstArgument = arguments.get(0).expression();
    StringLiteral literal = extractStringLiteral(firstArgument);
    if (literal == null) {
      return;
    }

    if (arguments.size() == 1) {
      // If the logger is called without additional arguments, the message will not be parsed as a string format.
      // However, we still want to report if we see a valid string format without any format arguments.
      StringFormat.createFromPrintfStyle(IGNORE_SYNTAX_ERRORS, literal.trimmedQuotesValue()).ifPresent(format -> {
        if (format.numExpectedArguments() != 0) {
          reportIssue(ctx, firstArgument, literal, "Add argument(s) corresponding to the message's replacement field(s).");
        }
      });
      return;
    }

    Optional<StringFormat> formatOptional = StringFormat.createFromPrintfStyle(
      syntaxIssueReporter(ctx, firstArgument, literal), literal.trimmedQuotesValue());
    if (!formatOptional.isPresent()) {
      return;
    }

    StringFormat format = formatOptional.get();
    Token argIssueFrom = arguments.get(1).firstToken();
    Token argIssueTo = arguments.get(arguments.size() - 1).lastToken();

    if (format.hasNamedFields()) {
      checkNamed(ctx, arguments, format, argIssueFrom, argIssueTo);
    } else {
      List<Expression> expressions = arguments.subList(1, arguments.size()).stream()
        .map(RegularArgument::expression)
        .collect(Collectors.toList());

      checkPrintfExpressionList(ctx, format, argIssueFrom, argIssueTo, expressions);
    }
  }

  private static void checkNamed(SubscriptionContext ctx, List<RegularArgument> arguments, StringFormat format, Token argIssueFrom, Token argIssueTo) {
    // We can only have a second argument which should be a mapping.
    Expression second = arguments.get(1).expression();
    if (!isMapping(second)) {
      ctx.addIssue(argIssueFrom, argIssueTo,"Replace formatting argument(s) with a mapping; Replacement fields are named.");
      return;
    }

    if (arguments.size() > 2) {
      ctx.addIssue(argIssueFrom, argIssueTo, "Change formatting arguments; the formatted string expects a single mapping.");
      return;
    }

    if (second.is(Tree.Kind.DICTIONARY_LITERAL)) {
      checkPrintfDictionary(ctx, format, ((DictionaryLiteral) second));
    }
  }

  @Override
  protected void checkPrintfStyle(SubscriptionContext ctx, BinaryExpression modulo, StringLiteral literal) {
    Optional<StringFormat> formatOptional = StringFormat.createFromPrintfStyle(IGNORE_SYNTAX_ERRORS, literal.trimmedQuotesValue());
    if (!formatOptional.isPresent()) {
      // The string format contains invalid syntax.
      return;
    }

    StringFormat format = formatOptional.get();
    if (!isInterestingDictLiteral(modulo.rightOperand())) {
      return;
    }

    DictionaryLiteral dict = (DictionaryLiteral) modulo.rightOperand();
    List<String> allNames = format.replacementFields().stream()
      .filter(StringFormat.ReplacementField::isNamed)
      .map(StringFormat.ReplacementField::name)
      .collect(Collectors.toList());
    dict.elements().stream()
      .map(KeyValuePair.class::cast)
      .map(kv -> (StringLiteral) kv.key())
      .filter(key -> !allNames.contains(key.trimmedQuotesValue()))
      .forEach(key -> ctx.addIssue(key, "Remove this unused argument or add a replacement field."));
  }

  private static boolean isInterestingDictLiteral(Expression expression) {
    if (!expression.is(Tree.Kind.DICTIONARY_LITERAL)) {
      return false;
    }

    DictionaryLiteral dict = (DictionaryLiteral) expression;
    for (DictionaryLiteralElement element : dict.elements()) {
      if (!element.is(Tree.Kind.KEY_VALUE_PAIR)) {
        return false;
      }

      if (!((KeyValuePair) element).key().is(Tree.Kind.STRING_LITERAL)) {
        return false;
      }
    }

    return true;
  }

  @Override
  protected void checkStrFormatStyle(SubscriptionContext ctx, CallExpression callExpression, Expression qualifier, StringLiteral literal) {
    if (callExpression.arguments().stream().anyMatch(argument -> !argument.is(Tree.Kind.REGULAR_ARGUMENT))) {
      return;
    }

    Optional<StringFormat> formatOptional = StringFormat.createFromStrFormatStyle(IGNORE_SYNTAX_ERRORS, literal.trimmedQuotesValue());
    if (!formatOptional.isPresent()) {
      return;
    }

    StringFormat format = formatOptional.get();
    List<RegularArgument> arguments = callExpression.arguments().stream()
      .map(RegularArgument.class::cast)
      .collect(Collectors.toList());

    int firstKwIdx = IntStream.range(0, arguments.size())
      .filter(idx -> arguments.get(idx).keywordArgument() != null)
      .findFirst()
      .orElse(arguments.size());

    // Check the positional arguments.
    IntStream.range((int) format.numExpectedPositional(), firstKwIdx)
      .mapToObj(arguments::get)
      .forEach(argument -> ctx.addIssue(argument, "Remove this unused argument."));

    // Check unmatched keyword arguments.
    Set<RegularArgument> unmatchedKeywordArgs = new HashSet<>(arguments.subList(firstKwIdx, arguments.size()));
    format.replacementFields().stream()
      .filter(StringFormat.ReplacementField::isNamed)
      .map(StringFormat.ReplacementField::name)
      .forEach(name -> unmatchedKeywordArgs.removeIf(argument -> name.equals(argument.keywordArgument().name())));

    unmatchedKeywordArgs.forEach(argument -> ctx.addIssue(argument, "Remove this unused argument."));
  }
}
