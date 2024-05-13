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
package org.sonar.python.types.v2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record UnionType(Set<PythonType> candidates) implements PythonType {

  @Override
  public Optional<String> displayName() {
    List<String> candidateNames = new ArrayList<>();
    for (PythonType candidate : candidates) {
      Optional<String> s = candidate.displayName();
      s.ifPresent(candidateNames::add);
      if (s.isEmpty()) {
        return Optional.empty();
      }
    }
    String name = candidateNames.stream().sorted().collect(Collectors.joining(", ", "Union[", "]"));
    return Optional.of(name);
  }


  /**
   * For UnionType, hasMember will return true if all alternatives have the member
   * It will return false if all alternatives DON'T have the member
   * It will return unknown in all other cases
   */
  @Override
  public TriBool hasMember(String memberName) {
    Set<TriBool> uniqueResult = candidates.stream().map(c -> c.hasMember(memberName)).collect(Collectors.toSet());
    return uniqueResult.size() == 1 ? uniqueResult.iterator().next() : TriBool.UNKNOWN;
  }

  @Override
  public boolean isCompatibleWith(PythonType another) {
    return candidates.isEmpty() || candidates.stream()
      .anyMatch(candidate -> candidate.isCompatibleWith(another));
  }

  public static PythonType or(PythonType type1, PythonType type2) {
    if (type1.equals(PythonType.UNKNOWN) || type2.equals(PythonType.UNKNOWN)) {
      return PythonType.UNKNOWN;
    }
    if (type1.equals(type2)) {
      return type1;
    }
    Set<PythonType> types = new HashSet<>();
    addTypes(type1, types);
    addTypes(type2, types);
    return new UnionType(types);
  }

  private static void addTypes(PythonType type, Set<PythonType> types) {
    if (type instanceof UnionType unionType) {
      types.addAll(unionType.candidates());
    } else {
      types.add(type);
    }
  }
}
