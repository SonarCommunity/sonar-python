/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2021 SonarSource SA
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
package org.sonar.python.checks.hotspots;

import org.junit.Test;
import org.sonar.python.checks.utils.PythonCheckVerifier;

public class DebugModeCheckTest {

  @Test
  public void test() {
    PythonCheckVerifier.verify("src/test/resources/checks/hotspots/debugMode/debugModeActivated.py", new DebugModeCheck());
  }

  @Test
  public void test_globalSettings_file() {
    PythonCheckVerifier.verify("src/test/resources/checks/hotspots/debugMode/global_settings.py", new DebugModeCheck());
  }

  @Test
  public void test_settings_file() {
    PythonCheckVerifier.verify("src/test/resources/checks/hotspots/debugMode/settings.py", new DebugModeCheck());
  }
}
