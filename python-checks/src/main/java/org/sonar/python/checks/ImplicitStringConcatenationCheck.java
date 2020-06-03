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

import java.util.List;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.tree.StringElement;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Tree;

@Rule(key = "S5799")
public class ImplicitStringConcatenationCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Add a \"+\" operator to make the string concatenation explicit; or did you forget a comma?";

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.STRING_LITERAL, ctx -> {
      StringLiteral stringLiteral = (StringLiteral) ctx.syntaxNode();
      if (stringLiteral.parent().is(Tree.Kind.MODULO, Tree.Kind.QUALIFIED_EXPR)) {
        // if string formatting is used, explicit string concatenation with "+" might fail
        return;
      }
      List<StringElement> stringElements = stringLiteral.stringElements();
      if (stringElements.size() == 1) {
        return;
      }
      for (int i = 1; i < stringElements.size(); i++) {
        StringElement current = stringElements.get(i);
        StringElement previous = stringElements.get(i-1);
        if (!current.prefix().equalsIgnoreCase(previous.prefix()) || !haveSameQuotes(current, previous)) {
          continue;
        }
        if (current.firstToken().line() == previous.firstToken().line() || isWithinCollection(stringLiteral)) {
          ctx.addIssue(previous.firstToken(), MESSAGE).secondary(current.firstToken(), null);
          // Only raise 1 issue per string literal
          return;
        }
      }
    });
  }

  private static boolean isWithinCollection(StringLiteral stringLiteral) {
    return stringLiteral.parent().is(Tree.Kind.TUPLE, Tree.Kind.EXPRESSION_LIST);
  }

  private static boolean haveSameQuotes(StringElement first, StringElement second) {
    return first.isTripleQuoted() == second.isTripleQuoted() &&
      first.value().charAt(first.value().length() - 1) == second.value().charAt(second.value().length() - 1);
  }
}

