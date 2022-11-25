/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2022 SonarSource SA
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.api.SonarProduct;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.TextPointer;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.internal.ActiveRulesBuilder;
import org.sonar.api.batch.rule.internal.NewActiveRule;
import org.sonar.api.batch.sensor.error.AnalysisError;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.issue.IssueLocation;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.plugins.python.api.ProjectPythonVersion;
import org.sonar.plugins.python.api.PythonCheck;
import org.sonar.plugins.python.api.PythonCustomRuleRepository;
import org.sonar.plugins.python.api.PythonVersionUtils;
import org.sonar.plugins.python.api.PythonVisitorContext;
import org.sonar.plugins.python.caching.TestReadCache;
import org.sonar.plugins.python.caching.TestWriteCache;
import org.sonar.plugins.python.indexer.PythonIndexer;
import org.sonar.plugins.python.indexer.SonarLintPythonIndexer;
import org.sonar.plugins.python.indexer.TestModuleFileSystem;
import org.sonar.plugins.python.warnings.AnalysisWarningsWrapper;
import org.sonar.python.checks.CheckList;

import org.sonar.python.index.DescriptorUtils;
import org.sonar.python.index.VariableDescriptor;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.analysis.api.QuickFix;
import org.sonarsource.sonarlint.core.analysis.api.TextEdit;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextPointer;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.DefaultTextRange;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.FileMetadata;
import org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem.SonarLintInputFile;
import org.sonarsource.sonarlint.core.analysis.container.analysis.issue.DefaultQuickFix;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.plugin.api.issue.NewQuickFix;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonar.python.index.DescriptorsToProtobuf.toProtobufModuleDescriptor;
import static org.sonar.plugins.python.caching.Caching.IMPORTS_MAP_CACHE_KEY_PREFIX;
import static org.sonar.plugins.python.caching.Caching.PROJECT_SYMBOL_TABLE_CACHE_KEY_PREFIX;

public class PythonSensorTest {

  private static final String FILE_1 = "file1.py";
  private static final String FILE_2 = "file2.py";
  private static final String FILE_QUICKFIX = "file_quickfix.py";
  private static final String FILE_TEST_FILE = "test_file.py";
  private static final String ONE_STATEMENT_PER_LINE_RULE_KEY = "OneStatementPerLine";
  private static final String FILE_COMPLEXITY_RULE_KEY = "FileComplexity";
  private static final String CUSTOM_REPOSITORY_KEY = "customKey";
  private static final String CUSTOM_RULE_KEY = "key";

  private static final Version SONARLINT_DETECTABLE_VERSION = Version.create(6, 0);

  static final SonarRuntime SONARLINT_RUNTIME = SonarRuntimeImpl.forSonarLint(SONARLINT_DETECTABLE_VERSION);

  private static final PythonCustomRuleRepository[] CUSTOM_RULES = {new PythonCustomRuleRepository() {
    @Override
    public String repositoryKey() {
      return CUSTOM_REPOSITORY_KEY;
    }

    @Override
    public List<Class> checkClasses() {
      return Collections.singletonList(MyCustomRule.class);
    }
  }};
  private static Path workDir;

  @Rule(
    key = CUSTOM_RULE_KEY,
    name = "name",
    description = "desc",
    tags = {"bug"})
  public static class MyCustomRule implements PythonCheck {
    @RuleProperty(
      key = "customParam",
      description = "Custom parameter",
      defaultValue = "value")
    public String customParam = "value";

    @Override
    public void scanFile(PythonVisitorContext visitorContext) {
      // do nothing
    }

    @Override
    public boolean scanWithoutParsing(InputFile inputFile) {
      return false;
    }
  }

  private final File baseDir = new File("src/test/resources/org/sonar/plugins/python/sensor").getAbsoluteFile();

  private SensorContextTester context;

  private ActiveRules activeRules;

  private final AnalysisWarningsWrapper analysisWarning = mock(AnalysisWarningsWrapper.class);

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  @Before
  public void init() throws IOException {
    context = SensorContextTester.create(baseDir);
    workDir = Files.createTempDirectory("workDir");
    context.fileSystem().setWorkDir(workDir);
  }

  @Test
  public void sensor_descriptor() {
    activeRules = new ActiveRulesBuilder().build();
    DefaultSensorDescriptor descriptor = new DefaultSensorDescriptor();
    sensor().describe(descriptor);

    assertThat(descriptor.name()).isEqualTo("Python Sensor");
    assertThat(descriptor.languages()).containsOnly("py");
    assertThat(descriptor.type()).isNull();
  }

  @Test
  public void test_execute_on_sonarlint() {
    context.setRuntime(SONARLINT_RUNTIME);

    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "PrintStatementUsage"))
        .setName("Print Statement Usage")
        .build())
      .build();

    inputFile(FILE_1);

    sensor().execute(context);

    String key = "moduleKey:file1.py";
    assertThat(context.measure(key, CoreMetrics.NCLOC)).isNull();
    assertThat(context.allIssues()).hasSize(1);
    assertThat(context.highlightingTypeAt(key, 15, 2)).isEmpty();
    assertThat(context.allAnalysisErrors()).isEmpty();

    assertThat(PythonScanner.getWorkingDirectory(context)).isNull();
  }

  @Test
  public void test_execute_on_sonarlint_quickfix() throws IOException {
    context.setRuntime(SONARLINT_RUNTIME);
    context = Mockito.spy(context);
    when(context.newIssue()).thenReturn(new MockSonarLintIssue(context));

    activate_rule_S2710();
    setup_quickfix_sensor();

    assertThat(context.allIssues()).hasSize(1);

    Issue issue = context.allIssues().iterator().next();

    List<DefaultQuickFix> quickFixes = ((MockSonarLintIssue) issue).quickFixes;
    assertThat(quickFixes).hasSize(2);

    QuickFix quickfix = quickFixes.get(0);
    assertThat(quickfix.message()).isEqualTo("Add 'cls' as the first argument.");
    assertThat(quickfix.inputFileEdits()).hasSize(1);
    QuickFix quickfix2 = quickFixes.get(1);
    assertThat(quickfix2.message()).isEqualTo("Rename 'bob' to 'cls'");
    assertThat(quickfix2.inputFileEdits()).hasSize(1);

    List<TextEdit> textEdits = quickfix.inputFileEdits().get(0).textEdits();
    assertThat(textEdits).hasSize(1);
    assertThat(textEdits.get(0).newText()).isEqualTo("cls, ");

    org.sonarsource.sonarlint.core.commons.TextRange textRange = new org.sonarsource.sonarlint.core.commons.TextRange(4, 13, 4, 13);
    assertThat(textEdits.get(0).range()).usingRecursiveComparison().isEqualTo(textRange);
  }

  @Test
  public void test_execute_on_sonarlint_quickfix_broken() throws IOException {
    context.setRuntime(SONARLINT_RUNTIME);
    context = Mockito.spy(context);
    when(context.newIssue()).thenReturn(new MockSonarLintIssue(context) {
      @Override
      public NewQuickFix newQuickFix() {
        throw new RuntimeException("Exception message");
      }
    });

    activate_rule_S2710();
    setup_quickfix_sensor();

    Collection<Issue> issues = context.allIssues();
    assertThat(issues).hasSize(1);
    MockSonarLintIssue issue = (MockSonarLintIssue) issues.iterator().next();

    assertThat(issue.quickFixes).isEmpty();
    assertThat(issue.getSaved()).isTrue();

    assertThat(logTester.logs(LoggerLevel.WARN)).contains("Could not report quick fixes for rule: python:S2710. java.lang.RuntimeException: Exception message");
  }

  @Test
  public void test_symbol_visitor() {
    activeRules = new ActiveRulesBuilder().build();
    inputFile(FILE_2);
    inputFile("symbolVisitor.py");
    sensor().execute(context);

    String key = "moduleKey:file2.py";
    assertThat(context.referencesForSymbolAt(key, 1, 10)).isNull();
    verifyUsages(key, 3, 4, reference(4, 10, 4, 11),
      reference(6, 15, 6, 16), reference(7, 19, 7, 20));
    verifyUsages(key, 5, 12, reference(6, 19, 6, 20));

    key = "moduleKey:symbolVisitor.py";
    assertThat(context.referencesForSymbolAt(key, 1, 10)).isNull();
    verifyUsages(key, 1, 0, reference(29, 14, 29, 15), reference(30, 18, 30, 19));
    verifyUsages(key, 2, 0, reference(3, 6, 3, 7), reference(10, 4, 10, 5), reference(32, 1, 32, 2));
    verifyUsages(key, 5, 4, reference(6, 4, 6, 5), reference(7, 4, 7, 5),
      reference(8, 8, 8, 9), reference(13, 9, 13, 10));
    verifyUsages(key, 47, 5, reference(48, 14, 48, 17));
  }

  @Test
  public void test_issues() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, ONE_STATEMENT_PER_LINE_RULE_KEY))
        .build())
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S134"))
        .build())
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, FILE_COMPLEXITY_RULE_KEY))
        .setParam("maximumFileComplexityThreshold", "2")
        .build())
      .build();

    InputFile inputFile = inputFile(FILE_2);
    sensor().execute(context);

    assertThat(context.allIssues()).hasSize(3);
    Iterator<Issue> issuesIterator = context.allIssues().iterator();

    int checkedIssues = 0;

    while (issuesIterator.hasNext()) {
      Issue issue = issuesIterator.next();
      IssueLocation issueLocation = issue.primaryLocation();
      assertThat(issueLocation.inputComponent()).isEqualTo(inputFile);

      switch (issue.ruleKey().rule()) {
        case "S134":
          assertThat(issueLocation.message()).isEqualTo("Refactor this code to not nest more than 4 \"if\", \"for\", \"while\", \"try\" and \"with\" statements.");
          assertThat(issueLocation.textRange()).isEqualTo(inputFile.newRange(7, 16, 7, 18));
          assertThat(issue.flows()).hasSize(4);
          assertThat(issue.gap()).isNull();
          checkedIssues++;
          break;
        case ONE_STATEMENT_PER_LINE_RULE_KEY:
          assertThat(issueLocation.message()).isEqualTo("At most one statement is allowed per line, but 2 statements were found on this line.");
          assertThat(issueLocation.textRange()).isEqualTo(inputFile.newRange(1, 0, 1, 50));
          assertThat(issue.flows()).isEmpty();
          assertThat(issue.gap()).isNull();
          checkedIssues++;
          break;
        case FILE_COMPLEXITY_RULE_KEY:
          assertThat(issueLocation.message()).isEqualTo("File has a complexity of 5 which is greater than 2 authorized.");
          assertThat(issueLocation.textRange()).isNull();
          assertThat(issue.flows()).isEmpty();
          assertThat(issue.gap()).isEqualTo(3.0);
          checkedIssues++;
          break;
        default:
          throw new IllegalStateException();
      }
    }

    assertThat(checkedIssues).isEqualTo(3);
    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Starting global symbols computation");
    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Starting rules execution");
    assertThat(logTester.logs(LoggerLevel.INFO).stream().filter(line -> line.equals("1 source file to be analyzed")).count()).isEqualTo(2);

    assertThat(PythonScanner.getWorkingDirectory(context)).isEqualTo(workDir.toFile());
  }

  @Test
  public void cross_files_secondary_locations() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S930"))
        .build())
      .build();

    InputFile mainFile = inputFile("main.py");
    InputFile modFile = inputFile("mod.py");
    sensor().execute(context);

    assertThat(context.allIssues()).hasSize(1);
    Issue issue = context.allIssues().iterator().next();
    assertThat(issue.flows()).hasSize(1);
    Issue.Flow flow = issue.flows().get(0);
    assertThat(flow.locations()).hasSize(2);
    assertThat(flow.locations().get(0).inputComponent()).isEqualTo(mainFile);
    assertThat(flow.locations().get(1).inputComponent()).isEqualTo(modFile);
  }

  @Test
  public void no_cross_file_issues_only_one_file() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S930"))
        .build())
      .build();

    inputFile("main.py");
    sensor().execute(context);
    assertThat(context.allIssues()).isEmpty();
  }

  @Test
  public void cross_files_issues_only_one_file_analyzed() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S930"))
        .build())
      .build();

    InputFile mainFile = inputFile("main.py");
    // "mod.py" created but not added to context
    InputFile modFile = createInputFile("mod.py");
    PythonIndexer pythonIndexer = pythonIndexer(Arrays.asList(mainFile, modFile));
    sensor(null, pythonIndexer, analysisWarning).execute(context);
    assertThat(context.allIssues()).hasSize(1);
    Issue issue = context.allIssues().iterator().next();
    assertThat(issue.primaryLocation().inputComponent()).isEqualTo(mainFile);
    assertThat(issue.flows()).hasSize(1);
    Issue.Flow flow = issue.flows().get(0);
    assertThat(flow.locations()).hasSize(2);
    assertThat(flow.locations().get(0).inputComponent()).isEqualTo(mainFile);
    assertThat(flow.locations().get(1).inputComponent()).isEqualTo(modFile);
  }

  @Test
  public void no_indexer_when_project_too_large_sonarlint() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S930"))
        .build())
      .build();
    context.setSettings(new MapSettings().setProperty("sonar.python.sonarlint.indexing.maxlines", 1));

    InputFile mainFile = inputFile("main.py");
    PythonIndexer pythonIndexer = pythonIndexer(Collections.singletonList(mainFile));
    sensor(CUSTOM_RULES, pythonIndexer, analysisWarning).execute(context);
    assertThat(context.allIssues()).isEmpty();
    assertThat(logTester.logs(LoggerLevel.DEBUG)).contains("Project symbol table deactivated due to project size (total number of lines is 4, maximum for indexing is 1)");
    assertThat(logTester.logs(LoggerLevel.DEBUG)).contains("Update \"sonar.python.sonarlint.indexing.maxlines\" to set a different limit.");
  }

  @Test
  public void loop_in_class_hierarchy() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S2710"))
        .build())
      .build();

    InputFile mainFile = inputFile("modA.py");
    InputFile modFile = inputFile("modB.py");
    PythonIndexer pythonIndexer = pythonIndexer(Arrays.asList(mainFile, modFile));
    sensor(null, pythonIndexer, analysisWarning).execute(context);

    assertThat(context.allIssues()).hasSize(1);
  }


  @Test
  public void test_issues_on_test_files() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S5905"))
        .build())
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S1226"))
        .build())
      .build();

    InputFile inputFile = inputFile(FILE_TEST_FILE, Type.TEST);
    sensor().execute(context);

    assertThat(context.allIssues()).hasSize(1);
    Issue issue = context.allIssues().iterator().next();
    assertThat(issue.primaryLocation().inputComponent()).isEqualTo(inputFile);
    assertThat(issue.ruleKey().rule()).isEqualTo("S5905");
  }

  @Test
  public void test_test_file_highlighting() throws IOException {
    activeRules = new ActiveRulesBuilder().build();

    DefaultInputFile inputFile1 = spy(TestInputFileBuilder.create("moduleKey", FILE_1)
      .setModuleBaseDir(baseDir.toPath())
      .setCharset(UTF_8)
      .setType(Type.TEST)
      .setLanguage(Python.KEY)
      .initMetadata(TestUtils.fileContent(new File(baseDir, FILE_1), UTF_8))
      .build());

    DefaultInputFile inputFile2 = spy(TestInputFileBuilder.create("moduleKey", FILE_2)
      .setModuleBaseDir(baseDir.toPath())
      .setCharset(UTF_8)
      .setType(Type.TEST)
      .setLanguage(Python.KEY)
      .build());

    DefaultInputFile inputFile3 = spy(TestInputFileBuilder.create("moduleKey", "parse_error.py")
      .setModuleBaseDir(baseDir.toPath())
      .setCharset(UTF_8)
      .setType(Type.TEST)
      .setLanguage(Python.KEY)
      .build());

    context.fileSystem().add(inputFile1);
    context.fileSystem().add(inputFile2);
    context.fileSystem().add(inputFile3);
    sensor().execute(context);
    assertThat(logTester.logs()).contains("Unable to parse file: parse_error.py");
    assertThat(logTester.logs()).contains("Unable to analyze file: file2.py");
    assertThat(context.highlightingTypeAt(inputFile1.key(), 1, 2)).isNotEmpty();
  }

  @Test
  public void test_exception_does_not_fail_analysis() throws IOException {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, ONE_STATEMENT_PER_LINE_RULE_KEY))
        .build())
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, FILE_COMPLEXITY_RULE_KEY))
        .setParam("maximumFileComplexityThreshold", "2")
        .build())
      .build();

    DefaultInputFile inputFile = spy(TestInputFileBuilder.create("moduleKey", FILE_1)
      .setModuleBaseDir(baseDir.toPath())
      .setCharset(UTF_8)
      .setType(Type.MAIN)
      .setLanguage(Python.KEY)
      .initMetadata(TestUtils.fileContent(new File(baseDir, FILE_1), UTF_8))
      .setStatus(InputFile.Status.ADDED)
      .build());
    when(inputFile.contents()).thenThrow(RuntimeException.class);

    context.fileSystem().add(inputFile);
    inputFile(FILE_2);

    sensor().execute(context);

    assertThat(context.allIssues()).hasSize(2);
  }

  @Test
  public void test_exception_should_fail_analysis_if_configured_so() throws IOException {
    DefaultInputFile inputFile = spy(createInputFile(FILE_1));
    when(inputFile.contents()).thenThrow(FileNotFoundException.class);
    context.fileSystem().add(inputFile);

    activeRules = new ActiveRulesBuilder().build();
    context.setSettings(new MapSettings().setProperty("sonar.internal.analysis.failFast", "true"));
    PythonSensor sensor = sensor();

    assertThatThrownBy(() -> sensor.execute(context))
      .isInstanceOf(IllegalStateException.class)
      .hasCauseInstanceOf(FileNotFoundException.class);
  }

  @Test
  public void test_python_version_parameter_warning() {
    context.fileSystem().add(inputFile(FILE_1));

    activeRules = new ActiveRulesBuilder().build();

    sensor().execute(context);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains(PythonSensor.UNSET_VERSION_WARNING);
    verify(analysisWarning, times(1)).addUnique(PythonSensor.UNSET_VERSION_WARNING);
  }

  @Test
  public void test_python_version_parameter_no_warning() {
    context.fileSystem().add(inputFile(FILE_1));

    activeRules = new ActiveRulesBuilder().build();

    context.setSettings(new MapSettings().setProperty("sonar.python.version", "3.8"));
    sensor().execute(context);
    assertThat(ProjectPythonVersion.currentVersions()).containsExactly(PythonVersionUtils.Version.V_38);
    assertThat(logTester.logs(LoggerLevel.WARN)).doesNotContain(PythonSensor.UNSET_VERSION_WARNING);
    verify(analysisWarning, times(0)).addUnique(PythonSensor.UNSET_VERSION_WARNING);
  }

  @Test
  public void parse_error() {
    inputFile("parse_error.py");
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "ParsingError"))
        .build())
      .build();

    sensor().execute(context);
    assertThat(context.allIssues()).hasSize(1);
    String log = String.join("\n", logTester.logs());
    assertThat(log).contains("Parse error at line 2")
      .doesNotContain("java.lang.NullPointerException");
    assertThat(context.allAnalysisErrors()).hasSize(1);
    AnalysisError analysisError = context.allAnalysisErrors().iterator().next();
    assertThat(analysisError.inputFile().filename()).isEqualTo("parse_error.py");
    TextPointer location = analysisError.location();
    assertThat(location).isNotNull();
    assertThat(location.line()).isEqualTo(2);
  }

  @Test
  public void cancelled_analysis() {
    InputFile inputFile = inputFile(FILE_1);
    activeRules = (new ActiveRulesBuilder()).build();
    context.setCancelled(true);
    sensor(null, null, analysisWarning).execute(context);
    assertThat(context.measure(inputFile.key(), CoreMetrics.NCLOC)).isNull();
    assertThat(context.allAnalysisErrors()).isEmpty();
  }

  @Test
  public void saving_performance_measure_not_activated_by_default() throws IOException {
    activeRules = (new ActiveRulesBuilder()).build();

    inputFile("main.py");
    sensor().execute(context);
    assertThat(context.allIssues()).isEmpty();
    assertThat(logTester.logs(LoggerLevel.INFO)).noneMatch(s -> s.matches(".*performance measures.*"));
    Path defaultPerformanceFile = workDir.resolve("sonar-python-performance-measure.json");
    assertThat(defaultPerformanceFile).doesNotExist();
  }

  @Test
  public void saving_performance_measure() throws IOException {
    context.setSettings(new MapSettings().setProperty("sonar.python.performance.measure", "true"));
    activeRules = (new ActiveRulesBuilder()).build();

    inputFile("main.py");
    sensor().execute(context);
    Path defaultPerformanceFile = workDir.resolve("sonar-python-performance-measure.json");
    assertThat(logTester.logs(LoggerLevel.INFO)).anyMatch(s -> s.matches(".*performance measures.*"));
    assertThat(defaultPerformanceFile).exists();
    assertThat(new String(Files.readAllBytes(defaultPerformanceFile), UTF_8)).contains("\"PythonSensor\"");
  }

  @Test
  public void saving_performance_measure_custom_path() throws IOException {
    Path customPerformanceFile = workDir.resolve("custom.performance.measure.json");
    MapSettings mapSettings = new MapSettings();
    mapSettings.setProperty("sonar.python.performance.measure", "true");
    mapSettings.setProperty("sonar.python.performance.measure.path", customPerformanceFile.toString());
    context.setSettings(mapSettings);
    activeRules = (new ActiveRulesBuilder()).build();

    inputFile("main.py");
    sensor().execute(context);
    assertThat(logTester.logs(LoggerLevel.INFO)).anyMatch(s -> s.matches(".*performance measures.*"));
    Path defaultPerformanceFile = workDir.resolve("sonar-python-performance-measure.json");
    assertThat(defaultPerformanceFile).doesNotExist();
    assertThat(customPerformanceFile).exists();
    assertThat(new String(Files.readAllBytes(customPerformanceFile), UTF_8)).contains("\"PythonSensor\"");
  }

  @Test
  public void saving_performance_measure_empty_path() throws IOException {
    MapSettings mapSettings = new MapSettings();
    mapSettings.setProperty("sonar.python.performance.measure", "true");
    mapSettings.setProperty("sonar.python.performance.measure.path", "");
    context.setSettings(mapSettings);
    activeRules = (new ActiveRulesBuilder()).build();

    inputFile("main.py");
    sensor().execute(context);
    assertThat(logTester.logs(LoggerLevel.INFO)).anyMatch(s -> s.matches(".*performance measures.*"));
    Path defaultPerformanceFile = workDir.resolve("sonar-python-performance-measure.json");
    assertThat(defaultPerformanceFile).exists();
    assertThat(new String(Files.readAllBytes(defaultPerformanceFile), UTF_8)).contains("\"PythonSensor\"");
  }


  @Test
  public void test_using_cache() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, ONE_STATEMENT_PER_LINE_RULE_KEY))
        .build())
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S134"))
        .build())
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, FILE_COMPLEXITY_RULE_KEY))
        .setParam("maximumFileComplexityThreshold", "2")
        .build())
      .build();

    InputFile inputFile = inputFile(FILE_2, Type.MAIN, InputFile.Status.SAME);
    TestReadCache readCache = new TestReadCache();
    TestWriteCache writeCache = new TestWriteCache();
    writeCache.bind(readCache);

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    readCache.put(IMPORTS_MAP_CACHE_KEY_PREFIX + inputFile.key(), String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(PROJECT_SYMBOL_TABLE_CACHE_KEY_PREFIX + inputFile.key(), serializedSymbolTable);
    context.setPreviousCache(readCache);
    context.setNextCache(writeCache);
    context.setCacheEnabled(true);
    context.setSettings(new MapSettings().setProperty("sonar.python.skipUnchanged", true));
    sensor().execute(context);

    assertThat(context.allIssues()).isEmpty();
  }

  @Test
  public void test_scan_without_parsing_test_file() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S5905"))
        .build())
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S1226"))
        .build())
      .build();

    inputFile(FILE_TEST_FILE, Type.TEST, InputFile.Status.SAME);
    TestReadCache readCache = new TestReadCache();
    TestWriteCache writeCache = new TestWriteCache();
    writeCache.bind(readCache);

    context.setPreviousCache(readCache);
    context.setNextCache(writeCache);
    context.setCacheEnabled(true);
    context.setSettings(new MapSettings().setProperty("sonar.python.skipUnchanged", true));
    sensor().execute(context);

    assertThat(context.allIssues()).isEmpty();
  }

  @Test
  public void test_scan_without_parsing_fails() {
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, ONE_STATEMENT_PER_LINE_RULE_KEY))
        .build())
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CUSTOM_REPOSITORY_KEY, CUSTOM_RULE_KEY))
        .build())
      .build();

    InputFile inputFile = inputFile(FILE_2, Type.MAIN, InputFile.Status.SAME);
    TestReadCache readCache = new TestReadCache();
    TestWriteCache writeCache = new TestWriteCache();
    writeCache.bind(readCache);

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    readCache.put(IMPORTS_MAP_CACHE_KEY_PREFIX + inputFile.key(), String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(PROJECT_SYMBOL_TABLE_CACHE_KEY_PREFIX + inputFile.key(), serializedSymbolTable);
    context.setPreviousCache(readCache);
    context.setNextCache(writeCache);
    context.setCacheEnabled(true);
    context.setSettings(new MapSettings().setProperty("sonar.python.skipUnchanged", true));
    sensor().execute(context);

    assertThat(context.allIssues()).hasSize(1);
  }

  @Test
  public void cache_not_enabled_for_older_api_version() {
    SensorContextTester contextMock = spy(context);
    SonarRuntime runtime = mock(SonarRuntime.class);
    when(contextMock.runtime()).thenReturn(runtime);
    when(runtime.getProduct()).thenReturn(SonarProduct.SONARQUBE);
    when(runtime.getApiVersion()).thenReturn(Version.create(9, 6));
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, ONE_STATEMENT_PER_LINE_RULE_KEY))
        .build())
      .build();

    inputFile(FILE_2, Type.MAIN, InputFile.Status.SAME);
    TestReadCache readCache = new TestReadCache();
    TestWriteCache writeCache = new TestWriteCache();
    writeCache.bind(readCache);
    context.setCacheEnabled(true);
    context.setSettings(new MapSettings().setProperty("sonar.python.skipUnchanged", true));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    readCache.put(IMPORTS_MAP_CACHE_KEY_PREFIX + "file2", String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(PROJECT_SYMBOL_TABLE_CACHE_KEY_PREFIX + "file2", serializedSymbolTable);
    sensor().execute(contextMock);

    assertThat(context.allIssues()).hasSize(1);
  }


  private PythonSensor sensor() {
    return sensor(CUSTOM_RULES, null, analysisWarning);
  }

  private PythonSensor sensor(@Nullable PythonCustomRuleRepository[] customRuleRepositories, @Nullable PythonIndexer indexer, AnalysisWarningsWrapper analysisWarnings) {
    FileLinesContextFactory fileLinesContextFactory = mock(FileLinesContextFactory.class);
    FileLinesContext fileLinesContext = mock(FileLinesContext.class);
    when(fileLinesContextFactory.createFor(Mockito.any(InputFile.class))).thenReturn(fileLinesContext);
    CheckFactory checkFactory = new CheckFactory(activeRules);
    if (indexer == null && customRuleRepositories == null) {
      return new PythonSensor(fileLinesContextFactory, checkFactory, mock(NoSonarFilter.class), analysisWarnings);
    }
    if (indexer == null) {
      return new PythonSensor(fileLinesContextFactory, checkFactory, mock(NoSonarFilter.class), customRuleRepositories, analysisWarnings);
    }
    if (customRuleRepositories == null) {
      return new PythonSensor(fileLinesContextFactory, checkFactory, mock(NoSonarFilter.class), indexer, analysisWarnings);
    }
    return new PythonSensor(fileLinesContextFactory, checkFactory, mock(NoSonarFilter.class), customRuleRepositories, indexer, analysisWarnings);
  }

  private SonarLintPythonIndexer pythonIndexer(List<InputFile> files) {
    return new SonarLintPythonIndexer(new TestModuleFileSystem(files));
  }

  private InputFile inputFile(String name) {
    return inputFile(name, Type.MAIN);
  }

  private InputFile inputFile(String name, Type fileType) {
    DefaultInputFile inputFile = createInputFile(name, fileType, InputFile.Status.ADDED);
    context.fileSystem().add(inputFile);
    return inputFile;
  }

  private InputFile inputFile(String name, Type fileType, InputFile.Status status) {
    DefaultInputFile inputFile = createInputFile(name, fileType, status);
    context.fileSystem().add(inputFile);
    return inputFile;
  }

  private DefaultInputFile createInputFile(String name) {
    return createInputFile(name, Type.MAIN, InputFile.Status.ADDED);
  }

  private DefaultInputFile createInputFile(String name, Type fileType, InputFile.Status status) {
    return TestInputFileBuilder.create("moduleKey", name)
      .setModuleBaseDir(baseDir.toPath())
      .setCharset(UTF_8)
      .setType(fileType)
      .setLanguage(Python.KEY)
      .initMetadata(TestUtils.fileContent(new File(baseDir, name), UTF_8))
      .setStatus(status)
      .build();
  }

  private void verifyUsages(String componentKey, int line, int offset, TextRange... trs) {
    Collection<TextRange> textRanges = context.referencesForSymbolAt(componentKey, line, offset);
    assertThat(textRanges).containsExactly(trs);
  }

  private static TextRange reference(int lineStart, int columnStart, int lineEnd, int columnEnd) {
    return new DefaultTextRange(new DefaultTextPointer(lineStart, columnStart), new DefaultTextPointer(lineEnd, columnEnd));
  }

  private void activate_rule_S2710(){
    activeRules = new ActiveRulesBuilder()
      .addRule(new NewActiveRule.Builder()
        .setRuleKey(RuleKey.of(CheckList.REPOSITORY_KEY, "S2710"))
        .setName("First argument to class methods should follow naming convention")
        .build())
      .build();
  }
  private void setup_quickfix_sensor() throws IOException {
    String pathToQuickFixTestFile = "src/test/resources/org/sonar/plugins/python/sensor/" + FILE_QUICKFIX;
    File file = new File(pathToQuickFixTestFile);
    String content = Files.readString(file.toPath());

    ClientInputFile clientFile = mock(ClientInputFile.class);

    when(clientFile.relativePath()).thenReturn(pathToQuickFixTestFile);
    when(clientFile.getPath()).thenReturn(file.getAbsolutePath());
    when(clientFile.uri()).thenReturn(file.getAbsoluteFile().toURI());
    when(clientFile.contents()).thenReturn(content);

    Function<SonarLintInputFile, FileMetadata.Metadata> metadataGenerator = x -> {
      try {
        return new FileMetadata().readMetadata(new FileInputStream(file), StandardCharsets.UTF_8, file.toURI(), null);
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
    };

    SonarLintInputFile sonarFile = new SonarLintInputFile(clientFile, metadataGenerator);
    sonarFile.setType(Type.MAIN);
    sonarFile.setLanguage(Language.PYTHON);

    context.fileSystem().add(sonarFile);
    sensor().execute(context);
  }
}
