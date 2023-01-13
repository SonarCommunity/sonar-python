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
package org.sonar.plugins.python;

import java.util.List;
import org.junit.Test;
import org.sonar.api.Plugin;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.plugins.python.warnings.AnalysisWarningsWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PythonPluginTest {

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  @Test
  public void testGetExtensions() {
    Version v79 = Version.create(7, 9);
    SonarRuntime runtime = SonarRuntimeImpl.forSonarQube(v79, SonarQubeSide.SERVER, SonarEdition.DEVELOPER);
    assertThat(extensions(runtime)).hasSize(21);
    assertThat(extensions(runtime)).contains(AnalysisWarningsWrapper.class);
    assertThat(extensions(SonarRuntimeImpl.forSonarLint(v79))).hasSize(7);
  }

  @Test
  public void classNotAvailable() {
    PythonPlugin.SonarLintPluginAPIVersion sonarLintPluginAPIVersion = mock(PythonPlugin.SonarLintPluginAPIVersion.class);
    when(sonarLintPluginAPIVersion.isDependencyAvailable()).thenReturn(false);
    PythonPlugin.SonarLintPluginAPIManager sonarLintPluginAPIManager = new PythonPlugin.SonarLintPluginAPIManager();
    Plugin.Context context = mock(Plugin.Context.class);
    sonarLintPluginAPIManager.addSonarlintPythonIndexer(context, sonarLintPluginAPIVersion);
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsExactly("Error while trying to inject SonarLintPythonIndexer");
  }

  private static List extensions(SonarRuntime runtime) {
    Plugin.Context context = new Plugin.Context(runtime);
    new PythonPlugin().define(context);
    return context.getExtensions();
  }

}
