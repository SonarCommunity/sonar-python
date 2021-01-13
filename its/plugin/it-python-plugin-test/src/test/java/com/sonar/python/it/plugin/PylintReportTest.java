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
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.SonarScanner;
import java.io.File;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonarqube.ws.Issues.Issue;
import org.sonarqube.ws.client.issues.SearchRequest;

import static com.sonar.python.it.plugin.Tests.newWsClient;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class PylintReportTest {

  private static final String DEFAULT_PROPERTY = "sonar.python.pylint.reportPaths";
  private static final String LEGACY_PROPERTY = "sonar.python.pylint.reportPath";

  @ClassRule
  public static final Orchestrator ORCHESTRATOR = Tests.ORCHESTRATOR;

  @Test
  public void import_report() {
    final String projectKey = "pylint_project";
    analyseProjectWithReport(projectKey, DEFAULT_PROPERTY, "pylint-report.txt");
    assertThat(issues(projectKey)).hasSize(4);
  }

  @Test
  public void import_report_legacy_key() {
    final String projectKey = "pylint_project_legacy_key";
    analyseProjectWithReport(projectKey, LEGACY_PROPERTY, "pylint-report.txt");
    assertThat(issues(projectKey)).hasSize(4);
  }

  @Test
  public void missing_report() {
    final String projectKey = "pylint_project_missing_report";
    analyseProjectWithReport(projectKey, DEFAULT_PROPERTY, "missing");
    assertThat(issues(projectKey)).isEmpty();
  }

  @Test
  public void invalid_report() {
    final String projectKey = "pylint_project_invalid_report";
    BuildResult result = analyseProjectWithReport(projectKey, DEFAULT_PROPERTY, "invalid.txt");
    assertThat(result.getLogs()).contains("Cannot parse the line: trash");
    assertThat(issues(projectKey)).isEmpty();
  }

  @Test
  public void unknown_rule() {
    final String projectKey = "pylint_project_unknown_rule";
    analyseProjectWithReport(projectKey, DEFAULT_PROPERTY, "rule-unknown.txt");
    assertThat(issues(projectKey)).hasSize(4);
  }

  @Test
  public void multiple_reports() {
    final String projectKey = "pylint_project_multiple_reports";
    analyseProjectWithReport(projectKey, DEFAULT_PROPERTY, "pylint-report.txt, rule-unknown.txt");
    assertThat(issues(projectKey)).hasSize(8);
  }

  private static List<Issue> issues(String projectKey) {
    return newWsClient().issues().search(new SearchRequest().setProjects(singletonList(projectKey))).getIssuesList();
  }

  private static BuildResult analyseProjectWithReport(String projectKey, String property, String reportPaths) {
    ORCHESTRATOR.getServer().provisionProject(projectKey, projectKey);
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(projectKey, "py", "no_rule");

    return ORCHESTRATOR.executeBuild(
      SonarScanner.create()
        .setDebugLogs(true)
        .setProjectKey(projectKey)
        .setProjectName(projectKey)
        .setProjectDir(new File("projects/pylint_project"))
        .setProperty(property, reportPaths));
  }

}
