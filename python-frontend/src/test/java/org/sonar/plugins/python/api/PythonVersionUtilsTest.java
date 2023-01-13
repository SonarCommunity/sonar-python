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
package org.sonar.plugins.python.api;

import org.junit.Test;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.plugins.python.api.PythonVersionUtils.Version.V_27;
import static org.sonar.plugins.python.api.PythonVersionUtils.Version.V_310;
import static org.sonar.plugins.python.api.PythonVersionUtils.Version.V_35;
import static org.sonar.plugins.python.api.PythonVersionUtils.Version.V_36;
import static org.sonar.plugins.python.api.PythonVersionUtils.Version.V_37;
import static org.sonar.plugins.python.api.PythonVersionUtils.Version.V_38;
import static org.sonar.plugins.python.api.PythonVersionUtils.Version.V_39;

public class PythonVersionUtilsTest {

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  @Test
  public void supportedVersions() {
    assertThat(PythonVersionUtils.fromString("")).containsExactlyInAnyOrder(V_27, V_35, V_36, V_37, V_38, V_39, V_310);
    assertThat(PythonVersionUtils.fromString(",")).containsExactlyInAnyOrder(V_27, V_35, V_36, V_37, V_38, V_39, V_310);
    assertThat(PythonVersionUtils.fromString("2.7")).containsExactlyInAnyOrder(V_27);
    assertThat(PythonVersionUtils.fromString("2")).containsExactlyInAnyOrder(V_27);
    assertThat(PythonVersionUtils.fromString("3")).containsExactlyInAnyOrder(V_35);
    assertThat(PythonVersionUtils.fromString("3.8, 3.9")).containsExactlyInAnyOrder(V_38, V_39);
    assertThat(PythonVersionUtils.fromString("2.7, 3.9")).containsExactlyInAnyOrder(V_27, V_39);
    assertThat(PythonVersionUtils.fromString("3.10")).containsExactlyInAnyOrder(V_310);
  }

  @Test
  public void version_out_of_range() {
    assertThat(PythonVersionUtils.fromString("4")).containsExactlyInAnyOrder(V_310);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("No explicit support for version 4. Python version has been set to 3.10.");
    assertThat(PythonVersionUtils.fromString("1")).containsExactlyInAnyOrder(V_27);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("No explicit support for version 1. Python version has been set to 2.7.");
    assertThat(PythonVersionUtils.fromString("3.11")).containsExactlyInAnyOrder(V_310);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("No explicit support for version 3.11. Python version has been set to 3.10.");
  }

  @Test
  public void bugfix_versions() {
    assertThat(PythonVersionUtils.fromString("3.8.1")).containsExactlyInAnyOrder(V_38);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("No explicit support for version 3.8.1. Python version has been set to 3.8.");
    assertThat(PythonVersionUtils.fromString("3.11.1")).containsExactlyInAnyOrder(V_310);
  }

  @Test
  public void error_while_parsing_version() {
    assertThat(PythonVersionUtils.fromString("foo")).containsExactlyInAnyOrder(V_27, V_35, V_36, V_37, V_38, V_39, V_310);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("Error while parsing value of parameter 'sonar.python.version' (foo). Versions must be specified as MAJOR_VERSION.MIN.VERSION (e.g. \"3.7, 3.8\")");
  }
}
