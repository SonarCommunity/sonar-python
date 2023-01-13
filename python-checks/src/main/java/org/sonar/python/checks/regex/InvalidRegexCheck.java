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
package org.sonar.python.checks.regex;

import java.util.List;
import java.util.stream.Collectors;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.tree.CallExpression;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.SyntaxError;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;

@Rule(key = "S5856")
public class InvalidRegexCheck extends AbstractRegexCheck {

  private static final String MESSAGE_FORMAT = "Fix the syntax error%s inside this regex.";

  @Override
  public void checkRegex(RegexParseResult regexParseResult, CallExpression regexFunctionCall) {
    List<SyntaxError> syntaxErrors = regexParseResult.getSyntaxErrors();
    if (!syntaxErrors.isEmpty()) {
      reportSyntaxErrors(syntaxErrors);
    }
  }

  private void reportSyntaxErrors(List<SyntaxError> syntaxErrors) {
    // report on the first issue
    RegexSyntaxElement tree = syntaxErrors.get(0).getOffendingSyntaxElement();
    List<RegexIssueLocation> secondaries = syntaxErrors.stream()
      .map(error -> new RegexIssueLocation(error.getOffendingSyntaxElement(), error.getMessage()))
      .collect(Collectors.toList());

    String msg = String.format(MESSAGE_FORMAT, secondaries.size() > 1 ? "s" : "");
    addIssue(tree, msg, null, secondaries);
  }
}
