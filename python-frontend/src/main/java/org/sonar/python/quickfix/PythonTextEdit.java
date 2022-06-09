/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2022 SonarSource SA
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
package org.sonar.python.quickfix;

import org.sonar.plugins.python.api.tree.Token;
import org.sonar.plugins.python.api.tree.Tree;

/**
 * For internal use only. Can not be used outside SonarPython analyzer.
 */
public class PythonTextEdit {

  private final String message;
  private final int startLine;
  private final int startLineOffset;
  private final int endLine;
  private final int endLineOffset;

  public PythonTextEdit(String message, int startLine, int startLineOffset, int endLine, int endLineOffset) {
    this.message = message;
    this.startLine = startLine;
    this.startLineOffset = startLineOffset;
    this.endLine = endLine;
    this.endLineOffset = endLineOffset;
  }

  /**
   * Insert a line with the same offset as the given tree, before the given tree.
   * Offset is applied to multiline insertions.
   */
  public static PythonTextEdit insertLineBefore(Tree tree, String textToInsert) {
    String lineOffset = " ".repeat(tree.firstToken().column());
    String textWithOffset = textToInsert.replace("\n", "\n" + lineOffset);
    return insertBefore(tree, textWithOffset);
  }

  public static PythonTextEdit insertBefore(Tree tree, String textToInsert) {
    Token token = tree.firstToken();
    return insertAtPosition(token.line(), token.column(), textToInsert);
  }

  public static PythonTextEdit insertAfter(Tree tree, String textToInsert) {
    Token token = tree.firstToken();
    int lengthToken = token.value().length();
    return insertAtPosition(token.line(), token.column() + lengthToken, textToInsert);
  }

  private static PythonTextEdit insertAtPosition(int line, int column, String textToInsert) {
    return new PythonTextEdit(textToInsert, line, column, line, column);
  }

  public static PythonTextEdit replace(Tree toReplace, String replacementText) {
    return replaceRange(toReplace, toReplace, replacementText);
  }

  public static PythonTextEdit replaceRange(Tree start, Tree end, String replacementText) {
    Token first = start.firstToken();
    Token last = end.lastToken();
    return new PythonTextEdit(replacementText, first.line(), first.column(), last.line(), last.column() + last.value().length());
  }

  public static PythonTextEdit remove(Tree toRemove) {
    return replace(toRemove, "");
  }

  public String replacementText() {
    return message;
  }

  public int startLine() {
    return startLine;
  }

  public int startLineOffset() {
    return startLineOffset;
  }

  public int endLine() {
    return endLine;
  }

  public int endLineOffset() {
    return endLineOffset;
  }
}
