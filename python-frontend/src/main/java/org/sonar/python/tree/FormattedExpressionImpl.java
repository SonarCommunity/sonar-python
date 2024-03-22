/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2024 SonarSource SA
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
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.FormatSpecifier;
import org.sonar.plugins.python.api.tree.FormattedExpression;
import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TreeVisitor;

public class FormattedExpressionImpl extends PyTree implements FormattedExpression {

  private final Expression expression;
  private final Token equalToken;
  private final FormatSpecifier formatSpecifier;

  private final Token lCurlyBrace;
  private final Token rCurlyBrace;
  private final Token fstringConversionToken;
  private final Token fstringConversionName;

  public FormattedExpressionImpl(Expression expression, @Nullable Token lCurlyBrace, @Nullable Token rCurlyBrace,
    @Nullable Token equalToken, @Nullable FormatSpecifier formatSpecifier,
    @Nullable Token fstringConversionToken, @Nullable Token fstringConversionName) {

    this.expression = expression;
    this.equalToken = equalToken;
    this.formatSpecifier = formatSpecifier;
    this.lCurlyBrace = lCurlyBrace;
    this.rCurlyBrace = rCurlyBrace;
    this.fstringConversionToken = fstringConversionToken;
    this.fstringConversionName = fstringConversionName;
  }

  @Override
  public Expression expression() {
    return this.expression;
  }

  @Override
  public Token equalToken() {
    return this.equalToken;
  }

  @Override
  public FormatSpecifier formatSpecifier() {
    return this.formatSpecifier;
  }

  @Override
  List<Tree> computeChildren() {
    return Stream.of(lCurlyBrace, expression, equalToken, fstringConversionToken, fstringConversionName, formatSpecifier, rCurlyBrace)
      .filter(Objects::nonNull).toList();
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitFormattedExpression(this);
  }

  @Override
  public Kind getKind() {
    return Kind.FORMATTED_EXPRESSION;
  }
}
