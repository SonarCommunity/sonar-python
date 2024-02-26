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
package org.sonar.python.checks;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.PythonVisitorContext;
import org.sonar.python.SubscriptionVisitor;
import org.sonar.python.TestPythonVisitorRunner;
import org.sonar.python.checks.utils.PythonCheckVerifier;

class FileHeaderCopyrightCheckTest {

  @Test
  void test_copyright() {
    FileHeaderCopyrightCheck fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.headerFormat = "# Copyright FOO\n";
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/copyright.py", fileHeaderCopyrightCheck);
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/copyrightAndComments.py", fileHeaderCopyrightCheck);
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/commentAndDocstring.py", fileHeaderCopyrightCheck);
  }

  @Test
  void test_noncompliant() {
    FileHeaderCopyrightCheck fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.headerFormat = "# Copyright FOO";
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/copyrightNonCompliant.py", fileHeaderCopyrightCheck);
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/noHeaderNonCompliant.py", fileHeaderCopyrightCheck);
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/emptyFileButCopyright.py", fileHeaderCopyrightCheck);
  }

  @Test
  void test_NoCopyright() {
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/headerNoCopyright.py", new FileHeaderCopyrightCheck());
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/emptyFileNoCopyright.py", new FileHeaderCopyrightCheck());
  }


  @Test
  void test_copyright_docstring() {
    FileHeaderCopyrightCheck fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.headerFormat = "\"\"\"\n" +
      " SonarQube, open source software quality management tool.\n" +
      " Copyright (C) 2008-2018 SonarSource\n" +
      " mailto:contact AT sonarsource DOT com\n" +
      "\n" +
      " SonarQube is free software; you can redistribute it and/or\n" +
      " modify it under the terms of the GNU Lesser General Public\n" +
      " License as published by the Free Software Foundation; either\n" +
      " version 3 of the License, or (at your option) any later version.\n" +
      "\n" +
      " SonarQube is distributed in the hope that it will be useful,\n" +
      " but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
      " MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU\n" +
      " Lesser General Public License for more details.\n" +
      "\n" +
      " You should have received a copy of the GNU Lesser General Public License\n" +
      " along with this program; if not, write to the Free Software Foundation,\n" +
      " Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.\n" +
      "\"\"\"";
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/docstring.py", fileHeaderCopyrightCheck);
  }

  @Test
  void test_copyright_docstring_noncompliant() {
    FileHeaderCopyrightCheck fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.headerFormat = "\"\"\"\n" +
      " SonarQube, open source software quality management tool.\n" +
      " Copyright (C) 2008-2018 SonarSource\n" +
      " mailto:contact AT sonarsource DOT com\n" +
      "\n" +
      " SonarQube is free software; you can redistribute it and/or\n" +
      " modify it under the terms of the GNU Lesser General Public\n" +
      " License as published by the Free Software Foundation; either\n" +
      " version 3 of the License, or (at your option) any later version.\n" +
      "\n" +
      " SonarQube is distributed in the hope that it will be useful,\n" +
      " but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
      " MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU\n" +
      " Lesser General Public License for more details.\n" +
      "\n" +
      " You should have received a copy of the GNU Lesser General Public License\n" +
      " along with this program; if not, write to the Free Software Foundation,\n" +
      " Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.\n" +
      "\"\"\"";
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/docstringNonCompliant.py", fileHeaderCopyrightCheck);
  }

  @Test
  void test_searchPattern() {
    FileHeaderCopyrightCheck fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.headerFormat = "^#\\sCopyright[ ]20[0-9]{2}\\n#\\sAll rights reserved\\.\\n";
    fileHeaderCopyrightCheck.isRegularExpression = true;
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/copyrightNonCompliant.py", fileHeaderCopyrightCheck);
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/searchPatternNonCompliant.py", fileHeaderCopyrightCheck);
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/searchPattern.py", fileHeaderCopyrightCheck);
    fileHeaderCopyrightCheck.headerFormat = "";
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/searchPatternNonCompliant.py", fileHeaderCopyrightCheck);
  }

  @Test
  void test_misplaced_copyright_searchPattern() {
    FileHeaderCopyrightCheck fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.isRegularExpression = true;
    fileHeaderCopyrightCheck.headerFormat = "Copyright[ ]20[0-9]{2}";
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/searchPatternMisplacedCopyright.py", fileHeaderCopyrightCheck);
  }

  @Test
  void test_searchPattern_exception() {
    FileHeaderCopyrightCheck fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.headerFormat = "**";
    fileHeaderCopyrightCheck.isRegularExpression = true;
    PythonVisitorContext context = TestPythonVisitorRunner.createContext(new File("src/test/resources/checks/fileHeaderCopyright/searchPatternThrowsError.py"));
    Collection<PythonSubscriptionCheck> check = Collections.singletonList(fileHeaderCopyrightCheck);

    Assertions.assertThatThrownBy(() -> SubscriptionVisitor.analyze(check, context)).isInstanceOf(IllegalArgumentException.class);

    IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> SubscriptionVisitor.analyze(check, context));
    Assertions.assertThat(e.getMessage()).isEqualTo("[FileHeaderCopyrightCheck] Unable to compile the regular expression: "+fileHeaderCopyrightCheck.headerFormat);
  }

  @Test
  void shebangTest() {
    var fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.headerFormat = "# Copyright FOO\n";
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/shebangCopyright.py", fileHeaderCopyrightCheck);
  }

  @Test
  void shebangPatternTest() {
    var fileHeaderCopyrightCheck = new FileHeaderCopyrightCheck();
    fileHeaderCopyrightCheck.isRegularExpression = true;
    fileHeaderCopyrightCheck.headerFormat = "# Copyright[ ]20[0-9]{2}";
    PythonCheckVerifier.verifyNoIssue("src/test/resources/checks/fileHeaderCopyright/searchPatternShebangCopyright.py",
      fileHeaderCopyrightCheck);
    PythonCheckVerifier.verify("src/test/resources/checks/fileHeaderCopyright/searchPatternShebangCopyrightNonCompliant.py",
      fileHeaderCopyrightCheck);
  }
}
