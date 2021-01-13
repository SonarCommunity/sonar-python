/*
 * SonarQube Python Plugin
 * Copyright (C) 2012-2021 SonarSource SA
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
package com.sonar.python.it.plugin;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarScanner;
import java.io.File;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.client.issues.SearchRequest;

import static com.sonar.python.it.plugin.Tests.newWsClient;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class BanditReportTest {

  private static final String PROJECT = "bandit_project";

  @ClassRule
  public static final Orchestrator ORCHESTRATOR = Tests.ORCHESTRATOR;

  @Test
  public void import_report() {
    ORCHESTRATOR.getServer().provisionProject(PROJECT, PROJECT);
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT, "py", "no_rule");
    ORCHESTRATOR.executeBuild(
      SonarScanner.create()
        .setProjectDir(new File("projects/bandit_project")));

    List<Issues.Issue> issues = issues();
    assertThat(issues).hasSize(1);
    Issues.Issue issue = issues.get(0);
    assertThat(issue.getComponent()).isEqualTo("bandit_project:src/file1.py");
    assertThat(issue.getRule()).isEqualTo("external_bandit:B107");
    assertThat(issue.getMessage()).isEqualTo("Possible hardcoded password: 'secret'");
    assertThat(issue.getType()).isEqualTo(Common.RuleType.VULNERABILITY);
    assertThat(issue.getSeverity()).isEqualTo(Common.Severity.MINOR);
    assertThat(issue.getEffort()).isEqualTo("5min");
  }

  private static List<Issues.Issue> issues() {
    return newWsClient().issues().search(new SearchRequest().setProjects(singletonList(PROJECT))).getIssuesList();
  }

}
