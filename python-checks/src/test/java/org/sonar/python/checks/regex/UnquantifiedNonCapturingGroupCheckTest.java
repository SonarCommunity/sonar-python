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

import org.junit.jupiter.api.Test;
import org.sonar.python.checks.quickfix.PythonQuickFixVerifier;
import org.sonar.python.checks.utils.PythonCheckVerifier;

public class UnquantifiedNonCapturingGroupCheckTest {

  @Test
  public void test() {
    PythonCheckVerifier.verify("src/test/resources/checks/regex/unquantifiedNonCapturingGroupCheck.py", new UnquantifiedNonCapturingGroupCheck());
  }

  @Test
  public void quickFixTest() {
    var before = "import re\n" +
      "re.match(r\"(?:number)abc\", input)";
    var after = "import re\n" +
      "re.match(r\"numberabc\", input)";
    var check = new UnquantifiedNonCapturingGroupCheck();
    PythonQuickFixVerifier.verify(check, before, after);
    PythonQuickFixVerifier.verifyQuickFixMessages(check, before, "Unwrap subpattern");
  }

}
