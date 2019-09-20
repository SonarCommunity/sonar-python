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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.python.api.tree.PyToken;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.python.api.tree.PyExpressionTree;
import org.sonar.python.api.tree.PyIsExpressionTree;
import org.sonar.python.api.tree.Tree;

public class PyIsExpressionTreeImpl extends PyBinaryExpressionTreeImpl implements PyIsExpressionTree {

  private final PyToken notToken;

  public PyIsExpressionTreeImpl(PyExpressionTree leftOperand, PyToken operator, @Nullable PyToken not, PyExpressionTree rightOperand) {
    super(leftOperand, operator, rightOperand);
    this.notToken = not;
  }

  @Override
  public Kind getKind() {
    return Kind.IS;
  }

  @CheckForNull
  @Override
  public PyToken notToken() {
    return notToken;
  }

  @Override
  public List<Tree> children() {
    return Stream.of(Collections.singletonList(notToken), super.children()).flatMap(List::stream).filter(Objects::nonNull).collect(Collectors.toList());
  }
}
