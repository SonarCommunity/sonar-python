/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2023 SonarSource SA
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
package org.sonar.python.types;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.types.InferredType;
import org.sonar.python.semantic.SymbolImpl;

public class UnknownClassType implements InferredType {

  private final Symbol typeClass;
  private final Map<String, Symbol> members;

  public UnknownClassType(Symbol typeClass) {
    this.typeClass = typeClass;
    this.members = new HashMap<>();
  }

  @Override
  public boolean isIdentityComparableWith(InferredType other) {
    return true;
  }

  @Override
  public boolean canHaveMember(String memberName) {
    return true;
  }

  @Override
  public boolean declaresMember(String memberName) {
    return true;
  }

  @Override
  public Optional<Symbol> resolveMember(String memberName) {
    var member = members.computeIfAbsent(memberName, n -> Optional.of(typeClass)
      .map(Symbol::fullyQualifiedName)
      .map(fqn -> new SymbolImpl(memberName, fqn + "." + memberName))
      .orElse(null));
    return Optional.ofNullable(member);
  }

  @Override
  public Optional<Symbol> resolveDeclaredMember(String memberName) {
    return Optional.empty();
  }

  @Override
  public boolean canOnlyBe(String typeName) {
    return false;
  }

  @Override
  public boolean canBeOrExtend(String typeName) {
    return true;
  }

  @Override
  public boolean isCompatibleWith(InferredType other) {
    return InferredTypes.isTypeClassCompatibleWith(typeClass, other);
  }

  @Override
  public boolean mustBeOrExtend(String typeName) {
    return false;
  }

  public Symbol typeClass() {
    return typeClass;
  }
}
