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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.python.api.tree.Token;
import org.sonar.python.api.tree.Tree;
import org.sonar.python.api.tree.TreeVisitor;
import org.sonar.python.api.tree.YieldExpression;
import org.sonar.python.api.tree.YieldStatement;

public class YieldStatementImpl extends PyTree implements YieldStatement {
  private final YieldExpression yieldExpression;
  private final Token separator;

  public YieldStatementImpl(YieldExpression yieldExpression, @Nullable Token separator) {
    this.yieldExpression = yieldExpression;
    this.separator = separator;
  }

  @Override
  public YieldExpression yieldExpression() {
    return yieldExpression;
  }

  @Override
  public Token separator() {
    return separator;
  }

  @Override
  public Kind getKind() {
    return Kind.YIELD_STMT;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitYieldStatement(this);
  }

  @Override
  public List<Tree> childs() {
    return Stream.of(yieldExpression, separator).filter(Objects::nonNull).collect(Collectors.toList());
  }
}
