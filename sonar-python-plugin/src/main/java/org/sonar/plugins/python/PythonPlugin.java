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
package org.sonar.plugins.python;

import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.SonarProduct;
import org.sonar.api.SonarRuntime;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;
import org.sonar.plugins.python.bandit.BanditRulesDefinition;
import org.sonar.plugins.python.bandit.BanditSensor;
import org.sonar.plugins.python.coverage.PythonCoverageSensor;
import org.sonar.plugins.python.flake8.Flake8RulesDefinition;
import org.sonar.plugins.python.flake8.Flake8Sensor;
import org.sonar.plugins.python.pylint.PylintRulesDefinition;
import org.sonar.plugins.python.pylint.PylintSensor;
import org.sonar.plugins.python.warnings.DefaultAnalysisWarningsWrapper;
import org.sonar.plugins.python.xunit.PythonXUnitSensor;

public class PythonPlugin implements Plugin {

  private static final String PYTHON_CATEGORY = "Python";

  // Subcategories
  private static final String GENERAL = "General";
  private static final String TEST_AND_COVERAGE = "Tests and Coverage";
  private static final String EXTERNAL_ANALYZERS_CATEGORY = "External Analyzers";
  private static final String DEPRECATED_PREFIX = "DEPRECATED : Use " + PythonCoverageSensor.REPORT_PATHS_KEY + " instead. ";

  public static final String FILE_SUFFIXES_KEY = "sonar.python.file.suffixes";

  @Override
  public void define(Context context) {

    context.addExtensions(
      PropertyDefinition.builder(FILE_SUFFIXES_KEY)
        .index(10)
        .name("File Suffixes")
        .description("List of suffixes of Python files to analyze.")
        .multiValues(true)
        .category(PYTHON_CATEGORY)
        .subCategory(GENERAL)
        .onQualifiers(Qualifiers.PROJECT)
        .defaultValue("py")
        .build(),


      Python.class,

      PythonProfile.class,

      PythonSensor.class,
      PythonRuleRepository.class);

    SonarRuntime sonarRuntime = context.getRuntime();
    if (sonarRuntime.getProduct() != SonarProduct.SONARLINT) {
      context.addExtension(DefaultAnalysisWarningsWrapper.class);
      addCoberturaExtensions(context);
      addXUnitExtensions(context);
      addPylintExtensions(context);
      addBanditExtensions(context);
      addFlake8Extensions(context);
    }
  }

  private static void addCoberturaExtensions(Context context) {
    context.addExtensions(
      PropertyDefinition.builder(PythonCoverageSensor.REPORT_PATHS_KEY)
        .index(20)
        .name("Path to coverage report(s)")
        .description("List of paths pointing to coverage reports. Ant patterns are accepted for relative path. " +
          "The reports have to conform to the Cobertura XML format.")
        .category(PYTHON_CATEGORY)
        .subCategory(TEST_AND_COVERAGE)
        .onQualifiers(Qualifiers.PROJECT)
        .defaultValue(PythonCoverageSensor.DEFAULT_REPORT_PATH)
        .multiValues(true)
        .build(),
      // deprecated
      PropertyDefinition.builder(PythonCoverageSensor.REPORT_PATH_KEY)
        .index(21)
        .name("Path to coverage report")
        .description(DEPRECATED_PREFIX +
          "Path to a coverage report. Ant patterns are accepted for relative path. The report has to conform to the Cobertura XML format.")
        .category(PYTHON_CATEGORY)
        .subCategory(TEST_AND_COVERAGE)
        .onQualifiers(Qualifiers.PROJECT)
        .defaultValue("")
        .build(),
      PythonCoverageSensor.class);
  }

  private static void addXUnitExtensions(Context context) {
    context.addExtensions(
      PropertyDefinition.builder(PythonXUnitSensor.SKIP_DETAILS)
        .index(23)
        .name("Skip the details when importing the Xunit reports")
        .description("When enabled the test execution statistics is provided only on project level. Use this mode when paths in report are not found. Disabled by default.")
        .category(PYTHON_CATEGORY)
        .subCategory(TEST_AND_COVERAGE)
        .onQualifiers(Qualifiers.PROJECT)
        .defaultValue("false")
        .type(PropertyType.BOOLEAN)
        .build(),
      PropertyDefinition.builder(PythonXUnitSensor.REPORT_PATH_KEY)
        .index(24)
        .name("Path to xunit report(s)")
        .description("Path to the report of test execution, relative to project's root. Ant patterns are accepted. The reports have to conform to the junitreport XML format.")
        .category(PYTHON_CATEGORY)
        .subCategory(TEST_AND_COVERAGE)
        .onQualifiers(Qualifiers.PROJECT)
        .defaultValue(PythonXUnitSensor.DEFAULT_REPORT_PATH)
        .build(),
      PythonXUnitSensor.class);
  }

  private static void addBanditExtensions(Context context) {
    context.addExtensions(BanditSensor.class,
      PropertyDefinition.builder(BanditSensor.REPORT_PATH_KEY)
        .name("Bandit Report Files")
        .description("Paths (absolute or relative) to json files with Bandit issues.")
        .category(EXTERNAL_ANALYZERS_CATEGORY)
        .subCategory(PYTHON_CATEGORY)
        .onQualifiers(Qualifiers.PROJECT)
        .multiValues(true)
        .build(),
      BanditRulesDefinition.class);
  }

  private static void addPylintExtensions(Context context) {
    context.addExtensions(PylintSensor.class,
      PropertyDefinition.builder(PylintSensor.REPORT_PATH_KEY)
        .name("Pylint Report Files")
        .description("Paths (absolute or relative) to report files with Pylint issues.")
        .category(EXTERNAL_ANALYZERS_CATEGORY)
        .subCategory(PYTHON_CATEGORY)
        .onQualifiers(Qualifiers.PROJECT)
        .multiValues(true)
        .build(),
      PylintRulesDefinition.class);
  }

  private static void addFlake8Extensions(Context context) {
    context.addExtensions(Flake8Sensor.class,
      PropertyDefinition.builder(Flake8Sensor.REPORT_PATH_KEY)
        .name("Flake8 Report Files")
        .description("Paths (absolute or relative) to report files with Flake8 issues.")
        .category(EXTERNAL_ANALYZERS_CATEGORY)
        .subCategory(PYTHON_CATEGORY)
        .onQualifiers(Qualifiers.PROJECT)
        .multiValues(true)
        .build(),
      Flake8RulesDefinition.class);
  }

}
