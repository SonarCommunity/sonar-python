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
package org.sonar.python.tree;

import java.util.ArrayList;
import java.util.List;
import org.sonar.plugins.python.api.tree.FormatSpecifier;
import org.sonar.plugins.python.api.tree.FormattedExpression;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TreeVisitor;

public class FormatSpecifierImpl extends PyTree implements FormatSpecifier {

  private Token columnToken;
  private List<FormattedExpression> formatExpressions;

  public FormatSpecifierImpl(Token columnToken, List<FormattedExpression> formatExpressions) {
    this.columnToken = columnToken;
    this.formatExpressions = formatExpressions;
  }

  @Override
  public Token columnToken() {
    return columnToken;
  }

  @Override
  public List<FormattedExpression> formatExpressions() {
    return formatExpressions;
  }

  @Override
  List<Tree> computeChildren() {
    List<Tree> children = new ArrayList<>();
    children.add(columnToken);
    children.addAll(formatExpressions);
    return children;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitFormatSpecifier(this);
  }

  @Override
  public Kind getKind() {
    return Kind.FORMAT_SPECIFIER;
  }
}
