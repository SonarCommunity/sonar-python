/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.python.checks;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.python.PythonAstScanner;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.checks.CheckMessagesVerifierRule;

public class AfterJumpStatementCheckTest {

  @Rule
  public CheckMessagesVerifierRule checkMessagesVerifier = new CheckMessagesVerifierRule();

  @Test
  public void test() {
    AfterJumpStatementCheck check = new AfterJumpStatementCheck();
    SourceFile file = PythonAstScanner.scanSingleFile(new File("src/test/resources/checks/afterJumpStatement.py"), check);
    String message = "Remove the code after this \"%s\".";
    checkMessagesVerifier.verify(file.getCheckMessages())
        .next().atLine(5).withMessage(String.format(message, "break"))
        .next().atLine(8).withMessage(String.format(message, "continue"))
        .next().atLine(13).withMessage(String.format(message, "raise"))
        .next().atLine(24).withMessage(String.format(message, "return"))
        .next().atLine(31).withMessage(String.format(message, "return"));
  }
}
