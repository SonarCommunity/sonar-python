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
package org.sonar.plugins.python.indexer;

import com.sonar.sslr.api.AstNode;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.python.Scanner;
import org.sonar.plugins.python.SonarQubePythonFile;
import org.sonar.plugins.python.api.PythonFile;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.python.parser.PythonParser;
import org.sonar.python.semantic.ProjectLevelSymbolTable;
import org.sonar.python.tree.PythonTreeMaker;

import static org.sonar.python.semantic.SymbolUtils.pythonPackageName;

public abstract class PythonIndexer {

  private static final Logger LOG = Loggers.get(PythonIndexer.class);

  protected String projectBaseDirAbsolutePath;

  private final Map<URI, String> packageNames = new HashMap<>();
  private final PythonParser parser = PythonParser.create();
  private final ProjectLevelSymbolTable projectLevelSymbolTable = new ProjectLevelSymbolTable();

  public ProjectLevelSymbolTable projectLevelSymbolTable() {
    return projectLevelSymbolTable;
  }

  public String packageName(InputFile inputFile) {
    return packageNames.computeIfAbsent(inputFile.uri(), k -> pythonPackageName(inputFile.file(), projectBaseDirAbsolutePath));
  }

  void removeFile(InputFile inputFile) {
    String packageName = packageNames.get(inputFile.uri());
    if (packageName == null) {
      LOG.debug("Failed to remove file \"{}\" from project-level symbol table (file not indexed)", inputFile.filename());
      return;
    }
    packageNames.remove(inputFile.uri());
    projectLevelSymbolTable.removeModule(packageName, inputFile.filename());
  }

  void addFile(InputFile inputFile) throws IOException {
    AstNode astNode = parser.parse(inputFile.contents());
    FileInput astRoot = new PythonTreeMaker().fileInput(astNode);
    String packageName = pythonPackageName(inputFile.file(), projectBaseDirAbsolutePath);
    packageNames.put(inputFile.uri(), packageName);
    PythonFile pythonFile = SonarQubePythonFile.create(inputFile);
    projectLevelSymbolTable.addModule(astRoot, packageName, pythonFile);
  }

  public abstract void buildOnce(SensorContext context);

  class GlobalSymbolsScanner extends Scanner {

    protected GlobalSymbolsScanner(SensorContext context) {
      super(context);
    }

    @Override
    protected String name() {
      return "global symbols computation";
    }

    @Override
    protected void scanFile(InputFile inputFile) throws IOException {
      addFile(inputFile);
    }

    @Override
    protected void processException(Exception e, InputFile file) {
      LOG.debug("Unable to construct project-level symbol table for file: " + file);
      LOG.debug(e.getMessage());
    }
  }
}
