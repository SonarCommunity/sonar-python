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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.SonarProduct;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.python.api.ProjectPythonVersion;
import org.sonar.plugins.python.api.PythonCustomRuleRepository;
import org.sonar.plugins.python.api.PythonVersionUtils;
import org.sonar.plugins.python.api.caching.CacheContext;
import org.sonar.plugins.python.indexer.PythonIndexer;
import org.sonar.plugins.python.indexer.SonarQubePythonIndexer;
import org.sonar.plugins.python.warnings.AnalysisWarningsWrapper;
import org.sonar.python.caching.CacheContextImpl;
import org.sonar.python.checks.CheckList;
import org.sonarsource.performance.measure.PerformanceMeasure;

import static org.sonar.plugins.python.api.PythonVersionUtils.PYTHON_VERSION_KEY;

public final class PythonSensor implements Sensor {

  private static final String PERFORMANCE_MEASURE_PROPERTY = "sonar.python.performance.measure";
  private static final String PERFORMANCE_MEASURE_FILE_PATH_PROPERTY = "sonar.python.performance.measure.path";
  private static final String PERFORMANCE_MEASURE_DESTINATION_FILE = "sonar-python-performance-measure.json";

  private final PythonChecks checks;
  private final FileLinesContextFactory fileLinesContextFactory;
  private final NoSonarFilter noSonarFilter;
  private final PythonIndexer indexer;
  private final AnalysisWarningsWrapper analysisWarnings;
  private static final Logger LOG = Loggers.get(PythonSensor.class);
  static final String UNSET_VERSION_WARNING =
    "Your code is analyzed as compatible with python 2 and 3 by default. This will prevent the detection of issues specific to python 2 or python 3." +
    " You can get a more precise analysis by setting a python version in your configuration via the parameter \"sonar.python.version\"";

  /**
   * Constructor to be used by pico if neither PythonCustomRuleRepository nor PythonIndexer are to be found and injected.
   */
  public PythonSensor(FileLinesContextFactory fileLinesContextFactory, CheckFactory checkFactory,
                      NoSonarFilter noSonarFilter, AnalysisWarningsWrapper analysisWarnings) {
    this(fileLinesContextFactory, checkFactory, noSonarFilter, null, null, analysisWarnings);
  }

  public PythonSensor(FileLinesContextFactory fileLinesContextFactory, CheckFactory checkFactory, NoSonarFilter noSonarFilter,
                      PythonCustomRuleRepository[] customRuleRepositories, AnalysisWarningsWrapper analysisWarnings) {
    this(fileLinesContextFactory, checkFactory, noSonarFilter, customRuleRepositories, null, analysisWarnings);
  }

  public PythonSensor(FileLinesContextFactory fileLinesContextFactory, CheckFactory checkFactory, NoSonarFilter noSonarFilter,
                      PythonIndexer indexer, AnalysisWarningsWrapper analysisWarnings) {
    this(fileLinesContextFactory, checkFactory, noSonarFilter, null, indexer, analysisWarnings);
  }

  public PythonSensor(FileLinesContextFactory fileLinesContextFactory, CheckFactory checkFactory, NoSonarFilter noSonarFilter,
                      @Nullable PythonCustomRuleRepository[] customRuleRepositories, @Nullable PythonIndexer indexer, AnalysisWarningsWrapper analysisWarnings) {
    this.checks = new PythonChecks(checkFactory)
      .addChecks(CheckList.REPOSITORY_KEY, CheckList.getChecks())
      .addCustomChecks(customRuleRepositories);
    this.fileLinesContextFactory = fileLinesContextFactory;
    this.noSonarFilter = noSonarFilter;
    this.indexer = indexer;
    this.analysisWarnings = analysisWarnings;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .onlyOnLanguage(Python.KEY)
      .name("Python Sensor");
  }

  @Override
  public void execute(SensorContext context) {
    PerformanceMeasure.Duration durationReport = createPerformanceMeasureReport(context);
    List<InputFile> pythonFiles = getInputFiles(context);
    Optional<String> pythonVersionParameter = context.config().get(PYTHON_VERSION_KEY);
    if (!pythonVersionParameter.isPresent() && context.runtime().getProduct() != SonarProduct.SONARLINT) {
      LOG.warn(UNSET_VERSION_WARNING);
      analysisWarnings.addUnique(UNSET_VERSION_WARNING);
    }
    pythonVersionParameter.ifPresent(value -> ProjectPythonVersion.setCurrentVersions(PythonVersionUtils.fromString(value)));
    CacheContext cacheContext = CacheContextImpl.of(context);
    PythonIndexer pythonIndexer = this.indexer != null ? this.indexer : new SonarQubePythonIndexer(pythonFiles, cacheContext, context);
    PythonScanner scanner = new PythonScanner(context, checks, fileLinesContextFactory, noSonarFilter, pythonIndexer);
    scanner.execute(pythonFiles, context);
    durationReport.stop();
  }

  private static List<InputFile> getInputFiles(SensorContext context) {
    FilePredicates p = context.fileSystem().predicates();
    Iterable<InputFile> it = context.fileSystem().inputFiles(p.and(p.hasLanguage(Python.KEY)));
    List<InputFile> list = new ArrayList<>();
    it.forEach(list::add);
    return Collections.unmodifiableList(list);
  }

  private static PerformanceMeasure.Duration createPerformanceMeasureReport(SensorContext context) {
    return PerformanceMeasure.reportBuilder()
      .activate(context.config().getBoolean(PERFORMANCE_MEASURE_PROPERTY).orElse(Boolean.FALSE))
      .toFile(context.config().get(PERFORMANCE_MEASURE_FILE_PATH_PROPERTY)
        .filter(path -> !path.isEmpty())
        .orElseGet(() -> Optional.ofNullable(context.fileSystem().workDir())
          .filter(File::exists)
          .map(file -> file.toPath().resolve(PERFORMANCE_MEASURE_DESTINATION_FILE).toString())
          .orElse(null)))
      .appendMeasurementCost()
      .start("PythonSensor");
  }
}
