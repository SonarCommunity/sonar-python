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
package org.sonar.python.tree;

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Token;
import java.util.List;
import org.sonar.python.api.tree.PyDelStatementTree;
import org.sonar.python.api.tree.PyExpressionTree;
import org.sonar.python.api.tree.PyTreeVisitor;

public class PyDelStatementTreeImpl extends PyTree implements PyDelStatementTree {
  private final Token delKeyword;
  private final List<PyExpressionTree> expressionTrees;

  public PyDelStatementTreeImpl(AstNode astNode, Token delKeyword, List<PyExpressionTree> expressionTrees) {
    super(astNode);
    this.delKeyword = delKeyword;
    this.expressionTrees = expressionTrees;
  }

  @Override
  public Token delKeyword() {
    return delKeyword;
  }

  @Override
  public List<PyExpressionTree> expressions() {
    return expressionTrees;
  }

  @Override
  public Kind getKind() {
    return Kind.DEL_STMT;
  }

  @Override
  public void accept(PyTreeVisitor visitor) {
    visitor.visitDelStatement(this);
  }
}
