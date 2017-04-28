/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2017 SonarSource SA
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

import com.sonar.sslr.api.Grammar;
import com.sonar.sslr.api.RecognitionException;
import com.sonar.sslr.impl.Parser;
import java.nio.charset.Charset;
import java.util.List;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.ce.measure.RangeDistributionBuilder;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.python.IssueLocation;
import org.sonar.python.PythonCheck;
import org.sonar.python.PythonCheck.PreciseIssue;
import org.sonar.python.PythonConfiguration;
import org.sonar.python.PythonVisitorContext;
import org.sonar.python.metrics.FileLinesVisitor;
import org.sonar.python.metrics.MetricVisitor;
import org.sonar.python.parser.PythonParser;

public class PythonScanner {

  private static final Logger LOG = Loggers.get(PythonScanner.class);

  private static final Number[] FUNCTIONS_DISTRIB_BOTTOM_LIMITS = {1, 2, 4, 6, 8, 10, 12, 20, 30};
  private static final Number[] FILES_DISTRIB_BOTTOM_LIMITS = {0, 5, 10, 20, 30, 60, 90};

  private final SensorContext context;
  private final Parser<Grammar> parser;
  private final MetricVisitor metricVisitor;
  private final List<InputFile> inputFiles;
  private final Checks<PythonCheck> checks;
  private final NoSonarFilter noSonarFilter;

  public PythonScanner(SensorContext context, Checks<PythonCheck> checks, MetricVisitor metricVisitor,
    NoSonarFilter noSonarFilter, List<InputFile> inputFiles) {
    this.context = context;
    this.checks = checks;
    this.metricVisitor = metricVisitor;
    this.noSonarFilter = noSonarFilter;
    this.inputFiles = inputFiles;
    this.parser = PythonParser.create(new PythonConfiguration(context.fileSystem().encoding()));
  }

  public void scanFiles() {
    for (InputFile pythonFile : inputFiles) {
      scanFile(pythonFile);
    }
  }

  private void scanFile(InputFile inputFile) {
    PythonVisitorContext visitorContext;
    Charset encoding = context.fileSystem().encoding();
    try {
      visitorContext = new PythonVisitorContext(parser.parse(inputFile.file()), inputFile.file(), encoding);
      saveMeasures(inputFile, visitorContext);
    } catch (RecognitionException e) {
      visitorContext = new PythonVisitorContext(inputFile.file(), encoding, e);
      LOG.error("Unable to parse file: " + inputFile.absolutePath());
      LOG.error(e.getMessage());
    }

    for (PythonCheck check : checks.all()) {
      saveIssues(inputFile, check, check.scanFileForIssues(visitorContext));
    }

    new PythonHighlighter(context).scanFile(visitorContext);
  }

  private void saveIssues(InputFile inputFile, PythonCheck check, List<PreciseIssue> issues) {
    RuleKey ruleKey = checks.ruleKey(check);
    for (PreciseIssue preciseIssue : issues) {

      NewIssue newIssue = context
        .newIssue()
        .forRule(ruleKey);

      Integer cost = preciseIssue.cost();
      if (cost != null) {
        newIssue.gap(cost.doubleValue());
      }

      newIssue.at(newLocation(inputFile, newIssue, preciseIssue.primaryLocation()));

      for (IssueLocation secondaryLocation : preciseIssue.secondaryLocations()) {
        newIssue.addLocation(newLocation(inputFile, newIssue, secondaryLocation));
      }

      newIssue.save();
    }
  }

  private static NewIssueLocation newLocation(InputFile inputFile, NewIssue issue, IssueLocation location) {
    NewIssueLocation newLocation = issue.newLocation()
      .on(inputFile);
    if (location.startLine() != 0) {
      TextRange range;
      if (location.startLineOffset() == -1) {
        range = inputFile.selectLine(location.startLine());
      } else {
        range = inputFile.newRange(location.startLine(), location.startLineOffset(), location.endLine(), location.endLineOffset());
      }
      newLocation.at(range);
    }

    if (location.message() != null) {
      newLocation.message(location.message());
    }
    return newLocation;
  }

  private void saveMeasures(InputFile inputFile, PythonVisitorContext visitorContext) {
    metricVisitor.scanFile(visitorContext);
    FileLinesVisitor fileLinesVisitor = metricVisitor.fileLinesVisitor();

    noSonarFilter.noSonarInFile(inputFile, fileLinesVisitor.getLinesWithNoSonar());

    saveFilesComplexityDistribution(inputFile);
    saveFunctionsComplexityDistribution(inputFile);

    saveMetricOnFile(inputFile, CoreMetrics.NCLOC, fileLinesVisitor.getLinesOfCode().size());
    saveMetricOnFile(inputFile, CoreMetrics.STATEMENTS, metricVisitor.numberOfStatements());
    saveMetricOnFile(inputFile, CoreMetrics.FUNCTIONS, metricVisitor.numberOfFunctions());
    saveMetricOnFile(inputFile, CoreMetrics.CLASSES, metricVisitor.numberOfClasses());
    saveMetricOnFile(inputFile, CoreMetrics.COMPLEXITY, metricVisitor.complexity());
    saveMetricOnFile(inputFile, CoreMetrics.COMMENT_LINES, fileLinesVisitor.getLinesOfComments().size());
  }

  private void saveMetricOnFile(InputFile inputFile, Metric<Integer> metric, Integer value) {
    context.<Integer>newMeasure()
      .withValue(value)
      .forMetric(metric)
      .on(inputFile)
      .save();
  }

  private void saveFunctionsComplexityDistribution(InputFile inputFile) {
    RangeDistributionBuilder complexityDistribution = new RangeDistributionBuilder(FUNCTIONS_DISTRIB_BOTTOM_LIMITS);
    for (Integer functionComplexity : metricVisitor.functionComplexities()) {
      complexityDistribution.add(functionComplexity);
    }

    context.<String>newMeasure()
      .on(inputFile)
      .forMetric(CoreMetrics.FUNCTION_COMPLEXITY_DISTRIBUTION)
      .withValue(complexityDistribution.build())
      .save();
  }

  private void saveFilesComplexityDistribution(InputFile inputFile) {
    RangeDistributionBuilder complexityDistribution = new RangeDistributionBuilder(FILES_DISTRIB_BOTTOM_LIMITS);
    complexityDistribution.add(metricVisitor.complexity());
    context.<String>newMeasure()
      .on(inputFile)
      .forMetric(CoreMetrics.FILE_COMPLEXITY_DISTRIBUTION)
      .withValue(complexityDistribution.build())
      .save();
  }
}
