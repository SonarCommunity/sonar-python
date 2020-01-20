/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
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
package org.sonar.plugins.python.bandit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.ExternalIssue;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class BanditSensorTest {

  private static final String BANDIT_FILE = "python-project:bandit/file1.py";
  private static final String BANDIT_B413 = "external_bandit:B413";
  private static final String BANDIT_REPORT_JSON = "bandit-report.json";

  private static final Path PROJECT_DIR = Paths.get("src", "test", "resources", "org", "sonar", "plugins", "python", "bandit");

  private static BanditSensor banditSensor = new BanditSensor();

  @Rule
  public LogTester logTester = new LogTester();

  @Test
  public void test_descriptor() {
    DefaultSensorDescriptor sensorDescriptor = new DefaultSensorDescriptor();
    banditSensor.describe(sensorDescriptor);
    assertThat(sensorDescriptor.name()).isEqualTo("Import of Bandit issues");
    assertThat(sensorDescriptor.languages()).containsOnly("py");
    assertThat(sensorDescriptor.configurationPredicate()).isNotNull();
    assertNoErrorWarnDebugLogs(logTester);
  }


  @Test
  public void issues_with_sonarqube_72() throws IOException {
    List<ExternalIssue> externalIssues = executeSensorImporting(7, 9, BANDIT_REPORT_JSON);
    assertThat(externalIssues).hasSize(4);

    ExternalIssue first = externalIssues.get(0);
    assertThat(first.ruleKey().toString()).isEqualTo(BANDIT_B413);
    assertThat(first.type()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(first.severity()).isEqualTo(Severity.CRITICAL);
    IssueLocation firstPrimaryLoc = first.primaryLocation();
    assertThat(firstPrimaryLoc.inputComponent().key()).isEqualTo(BANDIT_FILE);
    assertThat(firstPrimaryLoc.message())
      .isEqualTo("The pyCrypto library and its module Util are no longer actively maintained and have been deprecated. Consider using pyca/cryptography library.");
    TextRange firstTextRange = firstPrimaryLoc.textRange();
    assertThat(firstTextRange).isNotNull();
    assertThat(firstTextRange.start().line()).isEqualTo(2);

    ExternalIssue second = externalIssues.get(1);
    assertThat(second.ruleKey().toString()).isEqualTo("external_bandit:B107");
    assertThat(second.type()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(second.severity()).isEqualTo(Severity.MINOR);
    IssueLocation secondPrimaryLoc = second.primaryLocation();
    assertThat(secondPrimaryLoc.inputComponent().key()).isEqualTo(BANDIT_FILE);
    assertThat(secondPrimaryLoc.message()).isEqualTo("Possible hardcoded password: 'secret'");
    TextRange secondTextRange = secondPrimaryLoc.textRange();
    assertThat(secondTextRange).isNotNull();
    assertThat(secondTextRange.start().line()).isEqualTo(5);

    ExternalIssue third = externalIssues.get(2);
    assertThat(third.ruleKey().toString()).isEqualTo("external_bandit:B605");
    assertThat(third.type()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(third.severity()).isEqualTo(Severity.BLOCKER);
    IssueLocation thirdPrimaryLoc = third.primaryLocation();
    assertThat(thirdPrimaryLoc.inputComponent().key()).isEqualTo(BANDIT_FILE);
    assertThat(thirdPrimaryLoc.message()).isEqualTo("Starting a process with a shell, possible injection detected, security issue.");
    TextRange thirdTextRange = thirdPrimaryLoc.textRange();
    assertThat(thirdTextRange).isNotNull();
    assertThat(thirdTextRange.start().line()).isEqualTo(6);

    ExternalIssue fourth = externalIssues.get(3);
    assertThat(fourth.ruleKey().toString()).isEqualTo("external_bandit:B311");
    assertThat(fourth.type()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(fourth.severity()).isEqualTo(Severity.MAJOR);
    IssueLocation fourthPrimaryLoc = fourth.primaryLocation();
    assertThat(fourthPrimaryLoc.inputComponent().key()).isEqualTo(BANDIT_FILE);
    assertThat(fourthPrimaryLoc.message()).isEqualTo("Standard pseudo-random generators are not suitable for security/cryptographic purposes.");
    TextRange fourthTextRange = fourthPrimaryLoc.textRange();
    assertThat(fourthTextRange).isNotNull();
    assertThat(fourthTextRange.start().line()).isEqualTo(7);

    assertNoErrorWarnDebugLogs(logTester);
  }


  @Test
  public void no_issues_without_report_paths_property() throws IOException {
    List<ExternalIssue> externalIssues = executeSensorImporting(7, 9, null);
    assertThat(externalIssues).isEmpty();
    assertNoErrorWarnDebugLogs(logTester);
  }

  @Test
  public void no_issues_with_invalid_report_path() throws IOException {
    List<ExternalIssue> externalIssues = executeSensorImporting(7, 9, "invalid-path.txt");
    assertThat(externalIssues).isEmpty();
    assertThat(onlyOneLogElement(logTester.logs(LoggerLevel.ERROR)))
      .startsWith("No issues information will be saved as the report file '")
      .contains("invalid-path.txt' can't be read.");
  }

  @Test
  public void no_issues_with_invalid_bandit_file() throws IOException {
    List<ExternalIssue> externalIssues = executeSensorImporting(7, 9, "not-bandit-file.json");
    assertThat(externalIssues).isEmpty();
    assertThat(onlyOneLogElement(logTester.logs(LoggerLevel.ERROR)))
      .startsWith("No issues information will be saved as the report file '")
      .contains("not-bandit-file.json' can't be read.");
  }

  @Test
  public void no_issues_with_empty_bandit_file() throws IOException {
    List<ExternalIssue> externalIssues = executeSensorImporting(7, 9, "bandit-report-empty.json");
    assertThat(externalIssues).isEmpty();
    assertNoErrorWarnDebugLogs(logTester);
  }

  @Test
  public void issues_when_bandit_file_has_errors() throws IOException {
    List<ExternalIssue> externalIssues = executeSensorImporting(7, 9, "bandit-report-with-errors.json");
    assertThat(externalIssues).hasSize(1);

    ExternalIssue first = externalIssues.get(0);
    assertThat(first.primaryLocation().inputComponent().key()).isEqualTo(BANDIT_FILE);
    assertThat(first.ruleKey().toString()).isEqualTo(BANDIT_B413);
    assertThat(first.type()).isEqualTo(RuleType.VULNERABILITY);
    assertThat(first.severity()).isEqualTo(Severity.MINOR);
    assertThat(first.primaryLocation().message()).isEqualTo("A message");
    assertThat(first.primaryLocation().textRange()).isNull();

    assertThat(logTester.logs(LoggerLevel.ERROR)).isEmpty();
    assertThat(onlyOneLogElement(logTester.logs(LoggerLevel.WARN)))
      .isEqualTo("Fail to resolve 22 file path(s) in Bandit report. No issues imported related to file(s): " +
        "bandit/unknown01.py;bandit/unknown02.py;bandit/unknown03py;bandit/unknown04.py;bandit/unknown05.py;" +
        "bandit/unknown06.py;bandit/unknown07.py;bandit/unknown08.py;bandit/unknown09.py;bandit/unknown10.py;" +
        "bandit/unknown11.py;bandit/unknown12.py;bandit/unknown13.py;bandit/unknown14.py;bandit/unknown15.py;" +
        "bandit/unknown16.py;bandit/unknown17.py;bandit/unknown18.py;bandit/unknown19.py;bandit/unknown20.py;...");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsExactly(
      "Missing information for ruleKey:'null', filePath:'null', message:'null'",
      "Missing information for ruleKey:'B413', filePath:'null', message:'null'",
      "Missing information for ruleKey:'B413', filePath:'bandit/file1.py', message:'null'",
      "Missing information for ruleKey:'B413', filePath:'', message:'null'",
      "Missing information for ruleKey:'B413', filePath:'bandit/file1.py', message:''");
  }

  @Test
  public void issues_when_bandit_file_and_line_errors() throws IOException {
    List<ExternalIssue> externalIssues = executeSensorImporting(7, 9, "bandit-report-with-file-and-line-errors.json");
    assertThat(externalIssues).hasSize(0);

    assertThat(onlyOneLogElement(logTester.logs(LoggerLevel.ERROR)))
      .contains("100 is not a valid line for pointer. File bandit/file1.py has 8 line(s)");
    assertThat(onlyOneLogElement(logTester.logs(LoggerLevel.WARN)))
      .contains("Fail to resolve 1 file path(s) in Bandit report. No issues imported related to file(s): bandit/unknown.py");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).isEmpty();
  }

  private static List<ExternalIssue> executeSensorImporting(int majorVersion, int minorVersion, @Nullable String fileName) throws IOException {
    Path baseDir = PROJECT_DIR.getParent();
    SensorContextTester context = SensorContextTester.create(baseDir);
    try (Stream<Path> fileStream = Files.list(PROJECT_DIR)) {
      fileStream.forEach(file -> addFileToContext(context, baseDir, file));
      context.setRuntime(SonarRuntimeImpl.forSonarQube(Version.create(majorVersion, minorVersion), SonarQubeSide.SERVER, SonarEdition.DEVELOPER));
      if (fileName != null) {
        String path = PROJECT_DIR.resolve(fileName).toAbsolutePath().toString();
        context.settings().setProperty("sonar.python.bandit.reportPaths", path);
      }
      banditSensor.execute(context);
      return new ArrayList<>(context.allExternalIssues());
    }
  }

  private static void addFileToContext(SensorContextTester context, Path projectDir, Path file) {
    try {
      String projectId = projectDir.getFileName().toString() + "-project";
      context.fileSystem().add(TestInputFileBuilder.create(projectId, projectDir.toFile(), file.toFile())
        .setCharset(UTF_8)
        .setLanguage(language(file))
        .setContents(new String(Files.readAllBytes(file), UTF_8))
        .setType(InputFile.Type.MAIN)
        .build());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String language(Path file) {
    String path = file.toString();
    return path.substring(path.lastIndexOf('.') + 1);
  }

  public static String onlyOneLogElement(List<String> elements) {
    assertThat(elements).hasSize(1);
    return elements.get(0);
  }

  public static void assertNoErrorWarnDebugLogs(LogTester logTester) {
    org.assertj.core.api.Assertions.assertThat(logTester.logs(LoggerLevel.ERROR)).isEmpty();
    org.assertj.core.api.Assertions.assertThat(logTester.logs(LoggerLevel.WARN)).isEmpty();
    org.assertj.core.api.Assertions.assertThat(logTester.logs(LoggerLevel.DEBUG)).isEmpty();
  }
}
