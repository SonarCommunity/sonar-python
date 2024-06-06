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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.Beta;

@Beta
public final class ModuleType implements PythonType {
  private final String name;
  private final ModuleType parent;
  private final Map<String, PythonType> members;

  public ModuleType(@Nullable String name, @Nullable ModuleType parent, Map<String, PythonType> members) {
    this.name = name;
    this.parent = parent;
    this.members = members;
  }

  public ModuleType(@Nullable String name) {
    this(name, null);
  }

  public ModuleType(@Nullable String name, @Nullable ModuleType parent) {
    this(name, parent, new HashMap<>());
  }

  @Override
  public Optional<PythonType> resolveMember(String memberName) {
    return Optional.ofNullable(members.get(memberName));
  }

  @Override
  public String toString() {
    return "ModuleType{" +
      "name='" + name + '\'' +
      ", members=" + members +
      '}';
  }

  @Override
  @CheckForNull
  public String name() {
    return name;
  }

  @CheckForNull
  public ModuleType parent() {
    return parent;
  }

  public Map<String, PythonType> members() {
    return members;
  }

}
