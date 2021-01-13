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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.plugins.python.api.tree.StringElement;
import org.sonar.plugins.python.api.tree.StringLiteral;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TreeVisitor;
import org.sonar.plugins.python.api.types.InferredType;
import org.sonar.python.types.InferredTypes;

public class StringLiteralImpl extends PyTree implements StringLiteral {

  private final List<StringElement> stringElements;
  private static final Set<String> BYTES_PREFIXES = new HashSet<>(Arrays.asList("b", "B", "br", "Br", "bR", "BR", "rb", "rB", "Rb", "RB"));

  StringLiteralImpl(List<StringElement> stringElements) {
    this.stringElements = stringElements;
  }

  @Override
  public Kind getKind() {
    return Kind.STRING_LITERAL;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitStringLiteral(this);
  }

  @Override
  public List<Tree> computeChildren() {
    return Collections.unmodifiableList(stringElements);
  }

  @Override
  public List<StringElement> stringElements() {
    return stringElements;
  }

  @Override
  public String trimmedQuotesValue() {
    return stringElements().stream()
      .map(StringElement::trimmedQuotesValue)
      .collect(Collectors.joining());
  }

  // https://docs.python.org/3/reference/lexical_analysis.html#string-and-bytes-literals
  @Override
  public InferredType type() {
    if (stringElements.size() == 1 && BYTES_PREFIXES.contains(stringElements.get(0).prefix())) {
      // Python 3: bytes, Python 2: str
      return InferredTypes.anyType();
    }
    return InferredTypes.STR;
  }
}
