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
import org.junit.Test;
import org.sonar.python.PythonAstScanner;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.checks.CheckMessagesVerifier;

public class PreIncrementDecrementCheckTest {

  PreIncrementDecrementCheck check = new PreIncrementDecrementCheck();

  @Test
  public void test() {
    SourceFile file = PythonAstScanner.scanSingleFile(new File("src/test/resources/checks/preIncrementDecrement.py"), check);
    CheckMessagesVerifier.verify(file.getCheckMessages())
        .next().atLine(2).withMessage("This statement doesn't produce the expected result, replace use of non-existent pre-increment operator")
        .next().atLine(4).withMessage("This statement doesn't produce the expected result, replace use of non-existent pre-decrement operator")
        .noMore();
  }

}
