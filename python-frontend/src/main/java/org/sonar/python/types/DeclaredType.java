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
package org.sonar.python.types;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.plugins.python.api.symbols.AmbiguousSymbol;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.types.BuiltinTypes;
import org.sonar.plugins.python.api.types.InferredType;

import static org.sonar.plugins.python.api.symbols.Symbol.Kind.CLASS;

public class DeclaredType implements InferredType {

  private final Symbol typeClass;
  private final List<DeclaredType> typeArgs;
  private final Set<Symbol> alternativeTypeSymbols;

  public DeclaredType(Symbol typeClass, List<DeclaredType> typeArgs) {
    this.typeClass = typeClass;
    this.typeArgs = typeArgs;
    alternativeTypeSymbols = resolveAlternativeSymbols(typeClass, typeArgs);
  }

  private static Set<Symbol> resolveAlternativeSymbols(Symbol typeClass, List<DeclaredType> typeArgs) {
    Set<Symbol> symbols = new HashSet<>();
    if ("typing.Optional".equals(typeClass.fullyQualifiedName()) && typeArgs.size() == 1) {
      Symbol noneType = TypeShed.typeShedClass(BuiltinTypes.NONE_TYPE);
      symbols.add(noneType);
      DeclaredType argType = typeArgs.get(0);
      symbols.addAll(resolveAlternativeSymbols(argType.typeClass, argType.typeArgs));
    } else if ("typing.Union".equals(typeClass.fullyQualifiedName())) {
      symbols.addAll(typeArgs.stream().flatMap(arg -> resolveAlternativeSymbols(arg.typeClass, arg.typeArgs).stream()).collect(Collectors.toSet()));
    } else if ("typing.Text".equals(typeClass.fullyQualifiedName())) {
      symbols.add(TypeShed.typeShedClass("str"));
    } else {
      symbols.add(typeClass);
    }
    return symbols;
  }

  DeclaredType(Symbol typeClass) {
    this(typeClass, Collections.emptyList());
  }

  @Override
  public boolean canHaveMember(String memberName) {
    return true;
  }

  @Override
  public boolean declaresMember(String memberName) {
    if (hasUnresolvedHierarchy()) {
      return true;
    }
    return alternativeTypeSymbols.stream().anyMatch(symbol -> !symbol.is(CLASS) || ((ClassSymbol) symbol).canHaveMember(memberName));
  }

  @Override
  public boolean isIdentityComparableWith(InferredType other) {
    return true;
  }

  @Override
  public Optional<Symbol> resolveMember(String memberName) {
    return Optional.empty();
  }

  @Override
  public Optional<Symbol> resolveDeclaredMember(String memberName) {
    if (hasUnresolvedHierarchy()) {
      return Optional.empty();
    }
    Set<Optional<Symbol>> resolvedMembers = alternativeTypeSymbols().stream()
      .filter(s -> s.is(CLASS))
      .map(ClassSymbol.class::cast)
      .map(t -> t.resolveMember(memberName))
      .filter(Optional::isPresent)
      .collect(Collectors.toSet());
    return resolvedMembers.size() == 1 ? resolvedMembers.iterator().next() : Optional.empty();
  }

  @Override
  public boolean canBeOrExtend(String typeName) {
    return true;
  }

  @Override
  public boolean canOnlyBe(String typeName) {
    return false;
  }

  @Override
  public boolean isCompatibleWith(InferredType other) {
    if (alternativeTypeSymbols().isEmpty()) {
      return true;
    }
    return alternativeTypeSymbols().stream().anyMatch(typeSymbol -> InferredTypes.isTypeClassCompatibleWith(typeSymbol, other));
  }

  @Override
  public boolean mustBeOrExtend(String typeName) {
    return alternativeTypeSymbols().stream().flatMap(a -> {
      if (a.is(Symbol.Kind.AMBIGUOUS)) {
        return ((AmbiguousSymbol) a).alternatives().stream().filter(alternative -> alternative.is(CLASS));
      }
      return Stream.of(a);
    }).filter(a -> a.is(CLASS)).allMatch(a -> ((ClassSymbol) a).isOrExtends(typeName));
  }

  @Override
  public String toString() {
    return "DeclaredType(" + typeName() + ')';
  }

  public String typeName() {
    StringBuilder str = new StringBuilder(typeClass.name());
    if (!typeArgs.isEmpty()) {
      str.append("[");
      str.append(typeArgs.stream().map(DeclaredType::typeName).collect(Collectors.joining(", ")));
      str.append("]");
    }
    return str.toString();
  }

  Symbol getTypeClass() {
    return typeClass;
  }

  public Set<Symbol> alternativeTypeSymbols() {
    return alternativeTypeSymbols;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DeclaredType that = (DeclaredType) o;
    return Objects.equals(typeClass.name(), that.typeClass.name()) &&
      Objects.equals(typeClass.fullyQualifiedName(), that.typeClass.fullyQualifiedName()) &&
      Objects.equals(typeArgs, that.typeArgs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(typeClass.name(), typeClass.fullyQualifiedName(), typeArgs);
  }

  public static InferredType fromInferredType(InferredType inferredType) {
    if (inferredType instanceof RuntimeType) {
      ClassSymbol typeClass = ((RuntimeType) inferredType).getTypeClass();
      return new DeclaredType(typeClass);
    }
    if (inferredType instanceof DeclaredType) {
      return inferredType;
    }
    return InferredTypes.anyType();
  }

  boolean hasUnresolvedHierarchy() {
    if (alternativeTypeSymbols.isEmpty()) {
      return true;
    }
    for (Symbol alternativeTypeSymbol : alternativeTypeSymbols) {
      if (!alternativeTypeSymbol.is(CLASS) || ((ClassSymbol) alternativeTypeSymbol).hasUnresolvedTypeHierarchy()) {
        return true;
      }
    }
    return false;
  }
}
