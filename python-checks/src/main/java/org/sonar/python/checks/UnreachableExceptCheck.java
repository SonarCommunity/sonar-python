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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.ExceptClause;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.HasSymbol;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TryStatement;
import org.sonar.plugins.python.api.tree.Tuple;

@Rule(key = "S1045")
public class UnreachableExceptCheck extends PythonSubscriptionCheck {

  private static final String SECONDARY_MESSAGE = "Exceptions will be caught here.";

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.TRY_STMT, ctx -> {

      TryStatement tryStatement = (TryStatement) ctx.syntaxNode();
      Map<String, Expression> caughtTypes = new HashMap<>();

      for (ExceptClause exceptClause : tryStatement.exceptClauses()) {
        handleExceptClause(ctx, caughtTypes, exceptClause);
      }
    });
  }

  private static void handleExceptClause(SubscriptionContext ctx, Map<String, Expression> caughtTypes, ExceptClause exceptClause) {
    Map<String, Expression> caughtInExceptClause = new HashMap<>();
    Expression exceptionExpression = exceptClause.exception();
    if (exceptionExpression == null) {
      Expression baseExceptionExpression = caughtTypes.get("BaseException");
      if (baseExceptionExpression != null) {
        ctx.addIssue(exceptClause.exceptKeyword(), "Merge this bare \"except:\" with the \"BaseException\" one.")
          .secondary(baseExceptionExpression, SECONDARY_MESSAGE);
      }
      return;
    }
    if (exceptionExpression.is(Tree.Kind.TUPLE)) {
      Tuple tuple = (Tuple) exceptionExpression;
      for (Expression expression : tuple.elements()) {
        handleExceptionExpression(ctx, caughtTypes, expression, caughtInExceptClause);
      }
    } else {
      handleExceptionExpression(ctx, caughtTypes, exceptionExpression, caughtInExceptClause);
    }
    caughtInExceptClause.forEach(caughtTypes::putIfAbsent);
  }

  private static void handleExceptionExpression(SubscriptionContext ctx, Map<String, Expression> caughtTypes,
                                         Expression exceptionExpression, Map<String, Expression> caughtInExceptClause) {
    if (exceptionExpression instanceof HasSymbol) {
      Symbol symbol = ((HasSymbol) exceptionExpression).symbol();
      if (symbol != null && symbol.kind().equals(Symbol.Kind.CLASS)) {
        ClassSymbol classSymbol = (ClassSymbol) symbol;
        List<Expression> handledExceptions = retrieveAlreadyHandledExceptions(classSymbol, caughtTypes);
        if (!handledExceptions.isEmpty()) {
          PreciseIssue issue = ctx.addIssue(exceptionExpression, "Catch this exception only once; it is already handled by a previous except clause.");
          handledExceptions.forEach(h -> issue.secondary(h, SECONDARY_MESSAGE));
        }
      }
      if (symbol != null) {
        caughtInExceptClause.put(symbol.fullyQualifiedName(), exceptionExpression);
      }
    }
  }

  private static List<Expression> retrieveAlreadyHandledExceptions(ClassSymbol classSymbol, Map<String, Expression> caughtTypes) {
    return caughtTypes.keySet().stream().filter(classSymbol::isOrExtends).map(caughtTypes::get).collect(Collectors.toList());
  }
}
