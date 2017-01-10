/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2017 SonarSource SA
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
package org.sonar.python.checks;

import org.junit.Test;
import org.sonar.python.PythonAstScanner;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.checks.CheckMessagesVerifier;

public class OneStatementPerLineCheckTest {

  @Test
  public void test() {
    SourceFile file = PythonAstScanner.scanSingleFile("src/test/resources/checks/oneStatementPerLine.py", new OneStatementPerLineCheck());
    CheckMessagesVerifier.verify(file.getCheckMessages())
        .next().atLine(1).withMessage("At most one statement is allowed per line, but 2 statements were found on this line.")
        .next().atLine(2).withMessage("At most one statement is allowed per line, but 3 statements were found on this line.")
        .noMore();
  }

}
