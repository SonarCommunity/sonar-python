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
package org.sonar.python.checks.quickfix;

import com.sonar.sslr.api.AstNode;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.sonar.plugins.python.api.PythonCheck;
import org.sonar.plugins.python.api.PythonCheck.PreciseIssue;
import org.sonar.plugins.python.api.PythonFile;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.PythonVisitorContext;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.python.SubscriptionVisitor;
import org.sonar.python.parser.PythonParser;
import org.sonar.python.quickfix.IssueWithQuickFix;
import org.sonar.python.quickfix.PythonTextEdit;
import org.sonar.python.semantic.ProjectLevelSymbolTable;
import org.sonar.python.tree.PythonTreeMaker;

import static org.assertj.core.api.Assertions.assertThat;

public class PythonQuickFixVerifier {
  private PythonQuickFixVerifier() {
  }

  public static void verify(PythonCheck check, String codeWithIssue, String codeFixed) {
    List<PythonCheck.PreciseIssue> issues = PythonQuickFixVerifier
      .getIssuesWithQuickFix(check, codeWithIssue);

    assertThat(issues)
      .as("Number of issues")
      .overridingErrorMessage("Expected 1 issue but found %d", issues.size())
      .hasSize(1);
    IssueWithQuickFix issue = (IssueWithQuickFix) issues.get(0);

    assertThat(issue.getQuickFixes())
      .as("Number of quickfixes")
      .overridingErrorMessage("Expected 1 quickfix but found %d", issue.getQuickFixes().size())
      .hasSize(1);

    String codeQFApplied = PythonQuickFixVerifier.applyQuickFix(codeWithIssue, issue);
    assertThat(codeQFApplied)
      .as("Application of the quickfix")
      .overridingErrorMessage("The code with the quickfix applied is not the expected result : %s instead of %s", codeQFApplied, codeFixed)
      .isEqualTo(codeFixed);
  }

  private static List<PreciseIssue> scanFileForIssues(PythonCheck check, PythonVisitorContext context) {
    check.scanFile(context);
    if (check instanceof PythonSubscriptionCheck) {
      SubscriptionVisitor.analyze(Collections.singletonList((PythonSubscriptionCheck) check), context);
    }
    return context.getIssues();
  }

  private static List<PreciseIssue> getIssuesWithQuickFix(PythonCheck check, String codeWithIssue) {
    PythonParser parser = PythonParser.create();
    PythonQuickFixFile pythonFile = new PythonQuickFixFile(codeWithIssue);
    AstNode astNode = parser.parse(pythonFile.content());
    FileInput parse = new PythonTreeMaker().fileInput(astNode);

    PythonVisitorContext visitorContext = new PythonVisitorContext(parse,
      pythonFile, null, "",
      ProjectLevelSymbolTable.empty());

    return scanFileForIssues(check, visitorContext);
  }

  private static String applyQuickFix(String codeWithIssue, IssueWithQuickFix issueWithQuickFix) {
    assertThat(issueWithQuickFix.getQuickFixes()).hasSize(1);
    PythonTextEdit loc = issueWithQuickFix.getQuickFixes().get(0).getTextEdits().get(0);
    String replacement = loc.replacementText();
    int start = convertPositionToIndex(codeWithIssue, loc.startLine(), loc.startLineOffset());
    int end = convertPositionToIndex(codeWithIssue, loc.endLine(), loc.endLineOffset());
    return codeWithIssue.substring(0, start) + replacement + codeWithIssue.substring(end);
  }

  private static int convertPositionToIndex(String fileContent, int line, int lineOffset) {
    int currentLine = 1;
    int currentIndex = 0;
    while (currentLine < line) {
      currentIndex = fileContent.indexOf("\n", currentIndex) + 1;
      currentLine++;
    }
    return currentIndex + lineOffset;
  }

  private static class PythonQuickFixFile implements PythonFile {

    private final String content;

    public PythonQuickFixFile(String content) {
      this.content = content;
    }

    @Override
    public String content() {
      return this.content;
    }

    @Override
    public String fileName() {
      return "PythonQuickFixFile";
    }

    @Override
    public URI uri() {
      return URI.create(this.fileName());
    }
  }
}
