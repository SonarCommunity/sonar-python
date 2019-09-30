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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.python.api.tree.AssertStatement;
import org.sonar.python.api.tree.Expression;
import org.sonar.python.api.tree.Token;
import org.sonar.python.api.tree.TreeVisitor;
import org.sonar.python.api.tree.Tree;

public class AssertStatementImpl extends PyTree implements AssertStatement {
  private final Token assertKeyword;
  private final Expression condition;
  @Nullable
  private final Expression message;
  private final Token separator;

  public AssertStatementImpl(AstNode astNode, Token assertKeyword, Expression condition, @Nullable Expression message, @Nullable Token separator) {
    super(astNode);
    this.assertKeyword = assertKeyword;
    this.condition = condition;
    this.message = message;
    this.separator = separator;
  }

  @Override
  public Token assertKeyword() {
    return assertKeyword;
  }

  @Override
  public Expression condition() {
    return condition;
  }

  @Override
  @Nullable
  public Expression message() {
    return message;
  }

  @Override
  @Nullable
  public Token separator() {
    return separator;
  }

  @Override
  public Kind getKind() {
    return Kind.ASSERT_STMT;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitAssertStatement(this);
  }

  @Override
  public List<Tree> children() {
    return Stream.of(assertKeyword, condition, message, separator).filter(Objects::nonNull).collect(Collectors.toList());
  }
}
