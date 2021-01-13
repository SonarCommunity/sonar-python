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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.StatementList;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TreeVisitor;
import org.sonar.plugins.python.api.tree.WithItem;
import org.sonar.plugins.python.api.tree.WithStatement;

public class WithStatementImpl extends PyTree implements WithStatement {

  private final Token withKeyword;
  private final List<WithItem> withItems;
  private final List<Token> commas;
  private final Token newLine;
  private final Token indent;
  private final StatementList statements;
  private final Token dedent;
  private final Token asyncKeyword;
  private final boolean isAsync;
  private final Token colon;

  public WithStatementImpl(Token withKeyword, List<WithItem> withItems, List<Token> commas, Token colon, @Nullable Token newLine, @Nullable Token indent, StatementList statements,
                           @Nullable Token dedent, @Nullable Token asyncKeyword) {
    this.withKeyword = withKeyword;
    this.withItems = withItems;
    this.commas = commas;
    this.colon = colon;
    this.newLine = newLine;
    this.indent = indent;
    this.statements = statements;
    this.dedent = dedent;
    this.asyncKeyword = asyncKeyword;
    this.isAsync = asyncKeyword != null;
  }

  @Override
  public Token withKeyword() {
    return withKeyword;
  }

  @Override
  public List<WithItem> withItems() {
    return withItems;
  }

  @Override
  public Token colon() {
    return colon;
  }

  @Override
  public StatementList statements() {
    return statements;
  }

  @Override
  public boolean isAsync() {
    return isAsync;
  }

  @CheckForNull
  @Override
  public Token asyncKeyword() {
    return asyncKeyword;
  }

  @Override
  public Kind getKind() {
    return Kind.WITH_STMT;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitWithStatement(this);
  }

  @Override
  public List<Tree> computeChildren() {
    List<Tree> children = new ArrayList<>(Arrays.asList(asyncKeyword, withKeyword));
    int i = 0;
    for (Tree item : withItems) {
      children.add(item);
      if (i < commas.size()) {
        children.add(commas.get(i));
      }
      i++;
    }
    children.addAll(Arrays.asList(colon, newLine, indent, statements, dedent));
    return children.stream().filter(Objects::nonNull).collect(Collectors.toList());
  }

  public static class WithItemImpl extends PyTree implements WithItem {

    private final Expression test;
    private final Token as;
    private final Expression expr;

    public WithItemImpl(Expression test, @Nullable Token as, @Nullable Expression expr) {
      this.test = test;
      this.as = as;
      this.expr = expr;
    }

    @Override
    public Expression test() {
      return test;
    }

    @CheckForNull
    @Override
    public Token as() {
      return as;
    }

    @CheckForNull
    @Override
    public Expression expression() {
      return expr;
    }

    @Override
    public Kind getKind() {
      return Kind.WITH_ITEM;
    }

    @Override
    public void accept(TreeVisitor visitor) {
      visitor.visitWithItem(this);
    }

    @Override
    public List<Tree> computeChildren() {
      return Stream.of(test, as, expr).filter(Objects::nonNull).collect(Collectors.toList());
    }
  }
}
