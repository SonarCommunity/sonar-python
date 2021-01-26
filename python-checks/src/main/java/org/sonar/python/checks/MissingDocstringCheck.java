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

import javax.annotation.CheckForNull;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.ClassDef;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.Tree.Kind;

@Rule(key = MissingDocstringCheck.CHECK_KEY)
public class MissingDocstringCheck extends PythonSubscriptionCheck {

  public static final String CHECK_KEY = "S1720";

  private static final String MESSAGE_NO_DOCSTRING = "Add a docstring to this %s.";
  private static final String MESSAGE_EMPTY_DOCSTRING = "The docstring for this %s should not be empty.";

  private enum DeclarationType {
    MODULE("module"),
    CLASS("class"),
    METHOD("method"),
    FUNCTION("function");

    private final String value;

    DeclarationType(String value) {
      this.value = value;
    }
  }

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Kind.FILE_INPUT, ctx -> checkFileInput(ctx, (FileInput) ctx.syntaxNode()));
    context.registerSyntaxNodeConsumer(Kind.FUNCDEF, ctx -> checkDocString(ctx, ((FunctionDef) ctx.syntaxNode()).docstring()));
    context.registerSyntaxNodeConsumer(Kind.CLASSDEF, ctx -> checkDocString(ctx, ((ClassDef) ctx.syntaxNode()).docstring()));
  }

  private static void checkFileInput(SubscriptionContext ctx, FileInput fileInput) {
    if ("__init__.py".equals(ctx.pythonFile().fileName()) && fileInput.statements() == null) {
      return;
    }
    checkDocString(ctx, fileInput.docstring());
  }

  private static void checkDocString(SubscriptionContext ctx, @CheckForNull StringLiteral docstring) {
    Tree tree = ctx.syntaxNode();
    DeclarationType type = getType(tree);
    if (docstring == null) {
      raiseIssueNoDocstring(tree, type, ctx);
    } else if (docstring.trimmedQuotesValue().trim().length() == 0) {
      raiseIssue(tree, MESSAGE_EMPTY_DOCSTRING, type, ctx);
    }
  }

  private static DeclarationType getType(Tree tree) {
    if (tree.is(Kind.FUNCDEF)) {
      if (((FunctionDef) tree).isMethodDefinition()) {
        return DeclarationType.METHOD;
      } else {
        return DeclarationType.FUNCTION;
      }
    } else if (tree.is(Kind.CLASSDEF)) {
      return DeclarationType.CLASS;
    } else {
      // tree is FILE_INPUT
      return DeclarationType.MODULE;
    }
  }

  private static void raiseIssueNoDocstring(Tree tree, DeclarationType type, SubscriptionContext ctx) {
    if (type != DeclarationType.METHOD) {
      raiseIssue(tree, MESSAGE_NO_DOCSTRING, type, ctx);
    }
  }

  private static void raiseIssue(Tree tree, String message, DeclarationType type, SubscriptionContext ctx) {
    String finalMessage = String.format(message, type.value);
    if (type != DeclarationType.MODULE) {
      ctx.addIssue(getNameNode(tree), finalMessage);
    } else {
      ctx.addFileIssue(finalMessage);
    }
  }

  private static Name getNameNode(Tree tree) {
    if (tree.is(Kind.FUNCDEF)) {
      return ((FunctionDef) tree).name();
    }
    return ((ClassDef) tree).name();
  }

}
