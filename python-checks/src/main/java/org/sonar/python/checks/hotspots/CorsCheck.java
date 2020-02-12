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
package org.sonar.python.checks.hotspots;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.ArgList;
import org.sonar.plugins.python.api.tree.Argument;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonar.plugins.python.api.tree.Decorator;
import org.sonar.plugins.python.api.tree.DictionaryLiteral;
import org.sonar.plugins.python.api.tree.DictionaryLiteralElement;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.ExpressionList;
import org.sonar.plugins.python.api.tree.KeyValuePair;
import org.sonar.plugins.python.api.tree.ListLiteral;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.RegularArgument;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.SubscriptionExpression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tuple;

import static org.sonar.plugins.python.api.tree.Tree.Kind.ASSIGNMENT_STMT;
import static org.sonar.plugins.python.api.tree.Tree.Kind.CALL_EXPR;
import static org.sonar.plugins.python.api.tree.Tree.Kind.DECORATOR;
import static org.sonar.plugins.python.api.tree.Tree.Kind.DICTIONARY_LITERAL;
import static org.sonar.plugins.python.api.tree.Tree.Kind.KEY_VALUE_PAIR;
import static org.sonar.plugins.python.api.tree.Tree.Kind.LIST_LITERAL;
import static org.sonar.plugins.python.api.tree.Tree.Kind.NAME;
import static org.sonar.plugins.python.api.tree.Tree.Kind.REGULAR_ARGUMENT;
import static org.sonar.plugins.python.api.tree.Tree.Kind.STRING_LITERAL;
import static org.sonar.plugins.python.api.tree.Tree.Kind.SUBSCRIPTION;
import static org.sonar.plugins.python.api.tree.Tree.Kind.TUPLE;
import static org.sonar.python.semantic.SymbolUtils.getTypeName;

@Rule(key = "S5122")
public class CorsCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Make sure this permissive CORS policy is safe here.";

  private static final String DJANGO_ALLOW_ALL = "CORS_ORIGIN_ALLOW_ALL";
  private static final String DJANGO_WHITELIST = "CORS_ORIGIN_REGEX_WHITELIST";

  private static final String STAR = "*";

  private static final List<String> REGEX_TO_REPORT = Arrays.asList(
    ".*",
    ".+",
    "^.*$",
    "^.+$",
    ".*$",
    ".+$",
    "^.*",
    "^.+",
    STAR
  );

  private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  private static final String ORIGINS = "origins";

  private static final List<String> TYPES_TO_CHECK = Arrays.asList(
    "django.http.HttpResponse",
    "django.http.response.HttpResponse",
    "werkzeug.datastructures.Headers"
  );

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(ASSIGNMENT_STMT, CorsCheck::checkDjangoSettings);

    context.registerSyntaxNodeConsumer(ASSIGNMENT_STMT, CorsCheck::checkAllowOriginProperty);
    context.registerSyntaxNodeConsumer(CALL_EXPR, CorsCheck::checkDjangoResponseSetItem);

    context.registerSyntaxNodeConsumer(CALL_EXPR, CorsCheck::checkFlaskCorsCall);
    context.registerSyntaxNodeConsumer(DECORATOR, CorsCheck::checkFlaskDecorator);
    context.registerSyntaxNodeConsumer(CALL_EXPR, CorsCheck::checkFlaskResponse);

    context.registerSyntaxNodeConsumer(CALL_EXPR, CorsCheck::checkWerkzeugHeaders);
  }

  private static void checkDjangoSettings(SubscriptionContext ctx) {
    if (!ctx.pythonFile().fileName().equals("settings.py")) {
      return;
    }
    AssignmentStatement assignment = (AssignmentStatement) ctx.syntaxNode();

    if (isVarAssignment(assignment, DJANGO_ALLOW_ALL)
      && assignment.assignedValue().is(NAME)
      && ((Name) assignment.assignedValue()).name().equals("True")) {
      ctx.addIssue(assignment, MESSAGE);

    } else if (isVarAssignment(assignment, DJANGO_WHITELIST)) {
      getSingleElementInList(assignment.assignedValue()).ifPresent(element -> {
        if (isString(element, REGEX_TO_REPORT)) {
          ctx.addIssue(assignment, MESSAGE);
        }
      });
    }
  }

  private static void checkAllowOriginProperty(SubscriptionContext ctx) {
    AssignmentStatement assignment = (AssignmentStatement) ctx.syntaxNode();
    Optional<Expression> lhs = getOnlyAssignedLhs(assignment);

    if (lhs.isPresent() && lhs.get().is(SUBSCRIPTION)) {
      SubscriptionExpression subscription = (SubscriptionExpression) lhs.get();

      if (!subscription.object().is(NAME)) {
        return;
      }

      Symbol symbol = ((Name) subscription.object()).symbol();
      String typeName = getTypeName(symbol);

      List<Expression> subscripts = subscription.subscripts().expressions();
      if (typeName == null || subscripts.size() != 1) {
        return;
      }

      if (TYPES_TO_CHECK.contains(typeName) && isString(subscripts.get(0), ALLOW_ORIGIN) && isString(assignment.assignedValue(), STAR)) {
        ctx.addIssue(assignment, MESSAGE);
      }
    }
  }

  private static void checkDjangoResponseSetItem(SubscriptionContext ctx) {
    reportOnSetMethod(ctx, "django.http.HttpResponse.__setitem__");
  }

  private static void reportOnSetMethod(SubscriptionContext ctx, String fqn) {
    CallExpression callExpression = (CallExpression) ctx.syntaxNode();
    Symbol calleeSymbol = callExpression.calleeSymbol();
    if (callExpression.arguments().size() == 2 && isSymbol(calleeSymbol, fqn)) {
      Argument arg0 = callExpression.arguments().get(0);
      Argument arg1 = callExpression.arguments().get(1);
      if (isString(arg0, ALLOW_ORIGIN) && isString(arg1, STAR)) {
        ctx.addIssue(callExpression, MESSAGE);
      }
    }
  }

  private static void checkFlaskDecorator(SubscriptionContext ctx) {
    Decorator decorator = (Decorator) ctx.syntaxNode();
    List<Name> names = decorator.name().names();

    if (names.size() == 1) {
      Symbol symbol = names.get(0).symbol();
      if (isSymbol(symbol, "flask_cors.cross_origin")) {
        ArgList arguments = decorator.arguments();
        if (arguments == null) {
          ctx.addIssue(decorator, MESSAGE);
          return;
        }

        getArgument(arguments.arguments(), ORIGINS).ifPresent(argument -> {
          if (originsToReport(argument)) {
            ctx.addIssue(decorator, MESSAGE);
          }
        });

      }
    }
  }

  private static void checkWerkzeugHeaders(SubscriptionContext ctx) {
    CallExpression callExpression = (CallExpression) ctx.syntaxNode();
    Symbol symbol = callExpression.calleeSymbol();

    if (isSymbol(symbol, "werkzeug.datastructures.Headers") && callExpression.arguments().size() == 1) {
      reportOnHeader(ctx, callExpression.arguments().get(0));

    } else {
      reportOnSetMethod(ctx, "werkzeug.datastructures.Headers.set");
      reportOnSetMethod(ctx, "werkzeug.datastructures.Headers.setdefault");
      reportOnSetMethod(ctx, "werkzeug.datastructures.Headers.__setitem__");
    }
  }

  private static void checkFlaskResponse(SubscriptionContext ctx) {
    CallExpression callExpression = (CallExpression) ctx.syntaxNode();
    Symbol symbol = callExpression.calleeSymbol();

    if (isSymbol(symbol, "flask.Response") || isSymbol(symbol, "flask.wrappers.Response")) {
      if (callExpression.arguments().size() > 2) {
        Argument argument = callExpression.arguments().get(2);
        reportOnHeader(ctx, argument);
      }

    } else if (isSymbol(symbol, "flask.make_response") || isSymbol(symbol, "flask.helpers.make_response")) {
      if (callExpression.arguments().size() != 1) {
        return;
      }
      Argument argument = callExpression.arguments().get(0);
      if (argument.is(REGULAR_ARGUMENT) && ((RegularArgument) argument).expression().is(TUPLE)) {
        List<Expression> elements = ((Tuple) ((RegularArgument) argument).expression()).elements();
        if (!elements.isEmpty()) {
          reportOnHeader(ctx, elements.get(elements.size() - 1));
        }
      }
    }
  }

  private static <T extends Tree> void reportOnHeader(SubscriptionContext ctx, T element) {
    getValueInDictionary(element, ALLOW_ORIGIN).ifPresent(value -> {
      if (isString(value, STAR)) {
        ctx.addIssue(element, MESSAGE);
      }
    });
  }

  private static void checkFlaskCorsCall(SubscriptionContext ctx) {
    CallExpression callExpression = (CallExpression) ctx.syntaxNode();
    Symbol calleeSymbol = callExpression.calleeSymbol();
    if (!isSymbol(calleeSymbol, "flask_cors.CORS")) {
      return;
    }

    if (callExpression.arguments().size() == 1) {
      ctx.addIssue(callExpression, MESSAGE);
      return;
    }

    Optional<Expression> originsArgument = getArgument(callExpression.arguments(), ORIGINS);
    originsArgument.ifPresent(argument -> {
      if (originsToReport(argument)) {
        ctx.addIssue(callExpression, MESSAGE);
      }
    });

    Optional<Expression> resourcesArgumentOpt = getArgument(callExpression.arguments(), "resources");
    if (!resourcesArgumentOpt.isPresent()) {
      return;
    }

    Expression resourcesArgument = resourcesArgumentOpt.get();
    if (resourcesArgument.is(STRING_LITERAL) && !originsArgument.isPresent()) {
      ctx.addIssue(callExpression, MESSAGE);

    } else if (resourcesArgument.is(DICTIONARY_LITERAL)) {
      List<DictionaryLiteralElement> elements = ((DictionaryLiteral) resourcesArgument).elements();
      checkResourcesElements(ctx, callExpression, elements);
    }

  }

  private static void checkResourcesElements(SubscriptionContext ctx, CallExpression callExpression, List<DictionaryLiteralElement> elements) {
    for (DictionaryLiteralElement element : elements) {
      if (!element.is(KEY_VALUE_PAIR)) {
        continue;
      }
      Optional<Expression> originsValue = getValueInDictionary(((KeyValuePair) element).value(), ORIGINS);
      if (originsValue.isPresent() && originsToReport(originsValue.get())) {
        ctx.addIssue(callExpression, MESSAGE);
        return;
      }
    }
  }

  private static boolean originsToReport(Expression origins) {
    if (origins.is(STRING_LITERAL) && REGEX_TO_REPORT.contains(((StringLiteral) origins).trimmedQuotesValue())) {
      return true;
    } else {
      Optional<Expression> element = getSingleElementInList(origins);
      return element.isPresent() && isString(element.get(), STAR);
    }
  }

  private static Optional<Expression> getValueInDictionary(Tree tree, String key) {
    if (tree.is(DICTIONARY_LITERAL)) {
      List<DictionaryLiteralElement> elements = ((DictionaryLiteral) tree).elements();

      for (DictionaryLiteralElement element : elements) {
        if (element.is(KEY_VALUE_PAIR)) {
          KeyValuePair keyValuePair = (KeyValuePair) elements.get(0);
          if (isString(keyValuePair.key(), key)) {
            return Optional.of(keyValuePair.value());
          }
        }
      }
    } else if (tree.is(REGULAR_ARGUMENT)) {
      return getValueInDictionary(((RegularArgument) tree).expression(), key);
    }

    return Optional.empty();
  }

  private static Optional<Expression> getSingleElementInList(Expression expression) {
    if (expression.is(LIST_LITERAL)) {
      ListLiteral listLiteral = (ListLiteral) expression;
      if (listLiteral.elements().expressions().size() == 1) {
        return Optional.of(listLiteral.elements().expressions().get(0));
      }
    }

    return Optional.empty();
  }

  private static boolean isString(Tree tree, String value) {
    return isString(tree, Collections.singletonList(value));
  }

  private static boolean isString(Tree tree, List<String> values) {
    if (tree.is(STRING_LITERAL)) {
      return values.contains(((StringLiteral) tree).trimmedQuotesValue());
    } else if (tree.is(REGULAR_ARGUMENT)) {
      return isString(((RegularArgument) tree).expression(), values);
    }
    return false;
  }

  private static boolean isVarAssignment(AssignmentStatement assignment, String nameValue) {
    Optional<Expression> lhs = getOnlyAssignedLhs(assignment);
    return lhs.isPresent() && lhs.get().is(NAME) && ((Name) lhs.get()).name().equals(nameValue);
  }

  private static Optional<Expression> getOnlyAssignedLhs(AssignmentStatement assignment) {
    List<ExpressionList> lhs = assignment.lhsExpressions();
    if (lhs.size() == 1 && lhs.get(0).expressions().size() == 1) {
      return Optional.of(lhs.get(0).expressions().get(0));
    }

    return Optional.empty();
  }

  private static Optional<Expression> getArgument(List<Argument> arguments, String keyword) {
    return arguments.stream()
      .filter(a -> a.is(REGULAR_ARGUMENT))
      .map(a -> (RegularArgument) a)
      .filter(a -> a.keywordArgument() != null && a.keywordArgument().name().equals(keyword))
      .map(RegularArgument::expression)
      .findAny();
  }

  private static boolean isSymbol(@Nullable Symbol symbol, String fqn) {
    return symbol != null && symbol.fullyQualifiedName() != null && fqn.equals(symbol.fullyQualifiedName());
  }

}
