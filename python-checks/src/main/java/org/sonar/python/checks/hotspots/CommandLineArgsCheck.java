/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2019 SonarSource SA
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

import java.util.Set;
import org.sonar.check.Rule;
import org.sonar.python.SubscriptionContext;
import org.sonar.python.api.tree.PyNameTree;
import org.sonar.python.api.tree.Tree;
import org.sonar.python.checks.AbstractCallExpressionCheck;
import org.sonar.python.semantic.Symbol;

@Rule(key = CommandLineArgsCheck.CHECK_KEY)
public class CommandLineArgsCheck extends AbstractCallExpressionCheck {
  public static final String CHECK_KEY = "S4823";
  private static final String MESSAGE = "Make sure that command line arguments are used safely here.";
  private static final Set<String> questionableFunctions = immutableSet("argparse.ArgumentParser", "optparse.OptionParser");

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.NAME, CommandLineArgsCheck::checkSysArgNode);
    super.initialize(context);
  }

  private static void checkSysArgNode(SubscriptionContext ctx) {
    PyNameTree nameTree = (PyNameTree) ctx.syntaxNode();
    Tree parent = nameTree.parent();
    Symbol symbol = nameTree.symbol();
    if (symbol != null && "sys.argv".equals(symbol.fullyQualifiedName())) {
      if (isWithinImport(parent)) {
        return;
      }
      ctx.addIssue(nameTree, MESSAGE);
    }
  }

  @Override
  protected Set<String> functionsToCheck() {
    return questionableFunctions;
  }

  @Override
  protected String message() {
    return MESSAGE;
  }
}
