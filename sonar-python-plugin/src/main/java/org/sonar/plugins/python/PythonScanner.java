/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2019 SonarSource SA
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

import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.Grammar;
import com.sonar.sslr.api.RecognitionException;
import com.sonar.sslr.impl.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.issue.NoSonarFilter;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;
import org.sonar.api.measures.Metric;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.python.cpd.PythonCpdAnalyzer;
import org.sonar.python.IssueLocation;
import org.sonar.plugins.python.api.PythonCheck;
import org.sonar.plugins.python.api.PythonCheck.PreciseIssue;
import org.sonar.python.PythonConfiguration;
import org.sonar.python.PythonFile;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.PythonVisitorContext;
import org.sonar.python.SubscriptionVisitor;
import org.sonar.plugins.python.api.tree.FileInput;
import org.sonar.python.metrics.FileLinesVisitor;
import org.sonar.python.metrics.FileMetrics;
import org.sonar.python.parser.PythonParser;
import org.sonar.python.tree.PythonTreeMaker;

public class PythonScanner {

  private static final Logger LOG = Loggers.get(PythonScanner.class);

  private final SensorContext context;
  private final Parser<Grammar> parser;
  private final List<InputFile> inputFiles;
  private final PythonChecks checks;
  private final FileLinesContextFactory fileLinesContextFactory;
  private final NoSonarFilter noSonarFilter;
  private final PythonCpdAnalyzer cpdAnalyzer;

  public PythonScanner(SensorContext context, PythonChecks checks,
                       FileLinesContextFactory fileLinesContextFactory, NoSonarFilter noSonarFilter, List<InputFile> inputFiles) {
    this.context = context;
    this.checks = checks;
    this.fileLinesContextFactory = fileLinesContextFactory;
    this.noSonarFilter = noSonarFilter;
    this.cpdAnalyzer = new PythonCpdAnalyzer(context);
    this.inputFiles = inputFiles;
    this.parser = PythonParser.create(new PythonConfiguration(context.fileSystem().encoding()));
  }

  public void scanFiles() {
    for (InputFile pythonFile : inputFiles) {
      if (context.isCancelled()) {
        return;
      }
      try {
        scanFile(pythonFile);
      } catch (Exception e) {
        LOG.warn("Unable to analyze file '{}'. Error: {}", pythonFile.toString(), e);
      }
    }
  }

  private void scanFile(InputFile inputFile) {
    PythonFile pythonFile = SonarQubePythonFile.create(inputFile);
    PythonVisitorContext visitorContext;
    try {
      AstNode astNode = parser.parse(pythonFile.content());
      FileInput parse = new PythonTreeMaker().fileInput(astNode);
      visitorContext = new PythonVisitorContext(astNode, parse, pythonFile);
      saveMeasures(inputFile, visitorContext);
    } catch (RecognitionException e) {
      visitorContext = new PythonVisitorContext(pythonFile, e);
      LOG.error("Unable to parse file: " + inputFile.toString());
      LOG.error(e.getMessage());
      context.newAnalysisError()
        .onFile(inputFile)
        .at(inputFile.newPointer(e.getLine(), 0))
        .message(e.getMessage())
        .save();
    }
    List<PythonSubscriptionCheck> checksBasedOnTree = new ArrayList<>();
    for (PythonCheck check : checks.all()) {
      if (check instanceof PythonSubscriptionCheck) {
        checksBasedOnTree.add((PythonSubscriptionCheck) check);
      } else {
        check.scanFile(visitorContext);
      }
    }
    SubscriptionVisitor.analyze(checksBasedOnTree, visitorContext);
    saveIssues(inputFile, visitorContext.getIssues());

    new SymbolVisitor(context.newSymbolTable().onFile(inputFile)).visitFileInput(visitorContext.rootTree());
    new PythonHighlighter(context, inputFile).scanFile(visitorContext);
  }

  private void saveIssues(InputFile inputFile, List<PreciseIssue> issues) {
    for (PreciseIssue preciseIssue : issues) {
      RuleKey ruleKey = checks.ruleKey(preciseIssue.check());
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
    if (location.startLine() != IssueLocation.UNDEFINED_LINE) {
      TextRange range;
      if (location.startLineOffset() == IssueLocation.UNDEFINED_OFFSET) {
        range = inputFile.selectLine(location.startLine());
      } else {
        range = inputFile.newRange(location.startLine(), location.startLineOffset(), location.endLine(), location.endLineOffset());
      }
      newLocation.at(range);
    }

    String message = location.message();
    if (message != null) {
      newLocation.message(message);
    }
    return newLocation;
  }

  private void saveMeasures(InputFile inputFile, PythonVisitorContext visitorContext) {
    new PythonConfiguration(context.fileSystem().encoding());
    FileMetrics fileMetrics = new FileMetrics(visitorContext);
    FileLinesVisitor fileLinesVisitor = fileMetrics.fileLinesVisitor();

    cpdAnalyzer.pushCpdTokens(inputFile, visitorContext);
    noSonarFilter.noSonarInFile(inputFile, fileLinesVisitor.getLinesWithNoSonar());

    Set<Integer> linesOfCode = fileLinesVisitor.getLinesOfCode();
    saveMetricOnFile(inputFile, CoreMetrics.NCLOC, linesOfCode.size());
    saveMetricOnFile(inputFile, CoreMetrics.STATEMENTS, fileMetrics.numberOfStatements());
    saveMetricOnFile(inputFile, CoreMetrics.FUNCTIONS, fileMetrics.numberOfFunctions());
    saveMetricOnFile(inputFile, CoreMetrics.CLASSES, fileMetrics.numberOfClasses());
    saveMetricOnFile(inputFile, CoreMetrics.COMPLEXITY, fileMetrics.complexity());
    saveMetricOnFile(inputFile, CoreMetrics.COGNITIVE_COMPLEXITY, fileMetrics.cognitiveComplexity());
    saveMetricOnFile(inputFile, CoreMetrics.COMMENT_LINES, fileLinesVisitor.getCommentLineCount());

    FileLinesContext fileLinesContext = fileLinesContextFactory.createFor(inputFile);
    for (int line : linesOfCode) {
      fileLinesContext.setIntValue(CoreMetrics.NCLOC_DATA_KEY, line, 1);
    }
    for (int line : fileLinesVisitor.getExecutableLines()) {
      fileLinesContext.setIntValue(CoreMetrics.EXECUTABLE_LINES_DATA_KEY, line, 1);
    }
    fileLinesContext.save();
  }

  private void saveMetricOnFile(InputFile inputFile, Metric<Integer> metric, Integer value) {
    context.<Integer>newMeasure()
      .withValue(value)
      .forMetric(metric)
      .on(inputFile)
      .save();
  }

}
