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
package org.sonar.plugins.python.api;

import com.sonar.sslr.api.RecognitionException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.sonar.api.SonarProduct;
import org.sonar.plugins.python.api.PythonCheck.PreciseIssue;
import org.sonar.plugins.python.api.caching.CacheContext;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.python.types.v2.TypeChecker;
import org.sonar.python.caching.CacheContextImpl;
import org.sonar.python.semantic.ProjectLevelSymbolTable;
import org.sonar.python.semantic.SymbolTableBuilder;
import org.sonar.python.semantic.v2.ProjectLevelTypeTable;
import org.sonar.python.semantic.v2.SymbolTableBuilderV2;
import org.sonar.python.semantic.v2.TypeInferenceV2;

public class PythonVisitorContext extends PythonInputFileContext {

  private final FileInput rootTree;
  private final RecognitionException parsingException;
  private List<PreciseIssue> issues = new ArrayList<>();
  private final TypeChecker typeChecker;

  public PythonVisitorContext(FileInput rootTree, PythonFile pythonFile, @Nullable File workingDirectory, @Nullable String packageName) {
    super(pythonFile, workingDirectory, CacheContextImpl.dummyCache());
    this.rootTree = rootTree;
    this.parsingException = null;
    SymbolTableBuilder symbolTableBuilder = packageName != null ? new SymbolTableBuilder(packageName, pythonFile) : new SymbolTableBuilder(pythonFile);
    symbolTableBuilder.visitFileInput(rootTree);
    var symbolTable = new SymbolTableBuilderV2(rootTree).build();
    var projectLevelTypeTable = new ProjectLevelTypeTable(ProjectLevelSymbolTable.empty());
    new TypeInferenceV2(projectLevelTypeTable, pythonFile, symbolTable).inferTypes(rootTree);
    this.typeChecker = new TypeChecker(projectLevelTypeTable);
  }

  public PythonVisitorContext(FileInput rootTree, PythonFile pythonFile, @Nullable File workingDirectory, String packageName,
    ProjectLevelSymbolTable projectLevelSymbolTable, CacheContext cacheContext) {
    super(pythonFile, workingDirectory, cacheContext);
    this.rootTree = rootTree;
    this.parsingException = null;
    new SymbolTableBuilder(packageName, pythonFile, projectLevelSymbolTable).visitFileInput(rootTree);

    var symbolTable = new SymbolTableBuilderV2(rootTree)
      .build();
    var projectLevelTypeTable = new ProjectLevelTypeTable(projectLevelSymbolTable);
    new TypeInferenceV2(projectLevelTypeTable, pythonFile, symbolTable).inferTypes(rootTree);
    this.typeChecker = new TypeChecker(projectLevelTypeTable);
  }

  public PythonVisitorContext(FileInput rootTree, PythonFile pythonFile, @Nullable File workingDirectory, String packageName,
    ProjectLevelSymbolTable projectLevelSymbolTable, CacheContext cacheContext, SonarProduct sonarProduct) {
    super(pythonFile, workingDirectory, cacheContext, sonarProduct);
    this.rootTree = rootTree;
    this.parsingException = null;
    new SymbolTableBuilder(packageName, pythonFile, projectLevelSymbolTable).visitFileInput(rootTree);
    var symbolTable = new SymbolTableBuilderV2(rootTree)
      .build();
    var projectLevelTypeTable = new ProjectLevelTypeTable(projectLevelSymbolTable);
    new TypeInferenceV2(projectLevelTypeTable, pythonFile, symbolTable).inferTypes(rootTree);
    this.typeChecker = new TypeChecker(projectLevelTypeTable);
  }

  public PythonVisitorContext(PythonFile pythonFile, RecognitionException parsingException) {
    super(pythonFile, null, CacheContextImpl.dummyCache());
    this.rootTree = null;
    this.parsingException = parsingException;
    this.typeChecker = new TypeChecker(new ProjectLevelTypeTable(ProjectLevelSymbolTable.empty()));
  }

  public PythonVisitorContext(PythonFile pythonFile, RecognitionException parsingException, SonarProduct sonarProduct) {
    super(pythonFile, null, CacheContextImpl.dummyCache(), sonarProduct);
    this.rootTree = null;
    this.parsingException = parsingException;
    this.typeChecker = new TypeChecker(new ProjectLevelTypeTable(ProjectLevelSymbolTable.empty()));
  }

  public FileInput rootTree() {
    return rootTree;
  }

  public TypeChecker typeChecker() {
    return typeChecker;
  }

  public RecognitionException parsingException() {
    return parsingException;
  }

  public void addIssue(PreciseIssue issue) {
    issues.add(issue);
  }

  public List<PreciseIssue> getIssues() {
    return issues;
  }
}
