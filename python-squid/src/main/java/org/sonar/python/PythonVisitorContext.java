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
package org.sonar.python;

import com.intellij.psi.PsiElement;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.RecognitionException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.sonar.python.PythonCheck.PreciseIssue;
import org.sonar.python.semantic.SymbolTable;
import org.sonar.python.semantic.SymbolTableBuilderVisitor;

public class PythonVisitorContext {

  private final AstNode rootTree;
  private final PythonFile pythonFile;
  private final RecognitionException parsingException;
  private SymbolTable symbolTable = null;
  private List<PreciseIssue> issues = new ArrayList<>();

  public PythonVisitorContext(AstNode rootTree, PythonFile pythonFile) {
    this(rootTree, pythonFile, null);
    SymbolTableBuilderVisitor symbolTableBuilderVisitor = new SymbolTableBuilderVisitor();
    symbolTableBuilderVisitor.scanFile(this);
    symbolTable = symbolTableBuilderVisitor.symbolTable();
  }

  public PythonVisitorContext(PythonFile pythonFile, RecognitionException parsingException) {
    this(null, pythonFile, parsingException);
  }

  private PythonVisitorContext(AstNode rootTree, PythonFile pythonFile, RecognitionException parsingException) {
    this.rootTree = rootTree;
    this.pythonFile = pythonFile;
    this.parsingException = parsingException;
  }

  public AstNode rootTree() {
    return rootTree;
  }

  public PythonFile pythonFile() {
    return pythonFile;
  }

  public RecognitionException parsingException() {
    return parsingException;
  }

  public SymbolTable symbolTable() {
    return symbolTable;
  }

  public void addIssue(PreciseIssue issue) {
    issues.add(issue);
  }

  public void addIssue(PythonCheck check, PsiElement element, @Nullable String message) {
    issues.add(new PreciseIssue(check, IssueLocation.preciseLocation(element, message)));
  }

  public List<PreciseIssue> getIssues() {
    return issues;
  }
}
