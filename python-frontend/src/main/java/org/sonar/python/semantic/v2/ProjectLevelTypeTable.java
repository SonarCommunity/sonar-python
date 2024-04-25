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
package org.sonar.python.semantic.v2;

import java.util.List;
import java.util.stream.IntStream;
import org.sonar.python.semantic.ProjectLevelSymbolTable;
import org.sonar.python.types.v2.ModuleType;
import org.sonar.python.types.v2.PythonType;

public class ProjectLevelTypeTable {

  private final SymbolsModuleTypeProvider symbolsModuleTypeProvider;
  private final ModuleType rootModule;

  public ProjectLevelTypeTable(ProjectLevelSymbolTable projectLevelSymbolTable) {
    this.symbolsModuleTypeProvider = new SymbolsModuleTypeProvider(projectLevelSymbolTable);
    this.rootModule = this.symbolsModuleTypeProvider.createBuiltinModule();
  }

  public ModuleType getModule(String... moduleName) {
    return getModule(List.of(moduleName));
  }

  public ModuleType getModule(List<String> moduleNameParts) {
    var parent = rootModule;
    for (int i = 0; i < moduleNameParts.size(); i++) {
      var existing = parent.resolveMember(moduleNameParts.get(i)).orElse(PythonType.UNKNOWN);

      if (existing instanceof ModuleType existingModule) {
        parent = existingModule;
        continue;
      }

      var moduleFqn = IntStream.rangeClosed(0, i)
        .mapToObj(moduleNameParts::get)
        .toList();

      parent = symbolsModuleTypeProvider.createModuleType(moduleFqn, parent);
    }
    return parent;
  }
}
