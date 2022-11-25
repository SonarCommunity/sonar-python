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
package org.sonar.plugins.python.indexer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.plugins.python.api.caching.PythonReadCache;
import org.sonar.plugins.python.api.caching.PythonWriteCache;
import org.sonar.plugins.python.caching.CacheContextImpl;
import org.sonar.plugins.python.caching.PythonReadCacheImpl;
import org.sonar.plugins.python.caching.PythonWriteCacheImpl;
import org.sonar.plugins.python.caching.TestReadCache;
import org.sonar.plugins.python.caching.TestWriteCache;
import org.sonar.python.index.VariableDescriptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.sonar.plugins.python.caching.Caching.PROJECT_SYMBOL_TABLE_CACHE_KEY_PREFIX;
import static org.sonar.python.index.DescriptorsToProtobuf.toProtobufModuleDescriptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.plugins.python.TestUtils.createInputFile;
import static org.sonar.plugins.python.caching.Caching.IMPORTS_MAP_CACHE_KEY_PREFIX;

public class SonarQubePythonIndexerTest {

  private final File baseDir = new File("src/test/resources/org/sonar/plugins/python/indexer").getAbsoluteFile();
  private SensorContextTester context;

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  private InputFile file1;
  private InputFile file2;
  private SonarQubePythonIndexer pythonIndexer;
  private TestReadCache readCache;
  private CacheContextImpl cacheContext;

  @Before
  public void init() throws IOException {
    context = SensorContextTester.create(baseDir);
    Path workDir = Files.createTempDirectory("workDir");
    context.fileSystem().setWorkDir(workDir);
    context.settings().setProperty("sonar.python.skipUnchanged", true);

    TestWriteCache writeCache = new TestWriteCache();
    readCache = new TestReadCache();
    writeCache.bind(readCache);
    PythonWriteCache pythonWriteCache = new PythonWriteCacheImpl(writeCache);
    PythonReadCache pythonReadCache = new PythonReadCacheImpl(readCache);
    cacheContext = new CacheContextImpl(true, pythonWriteCache, pythonReadCache);
  }

  @Test
  public void test_single_file_modified() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("mod")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), String.join(";", Collections.emptyList()).getBytes(StandardCharsets.UTF_8));
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py"), serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isTrue();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Using cached data to retrieve global symbols.")
      .contains("Cached information of global symbols will be used for 1 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Optimized analysis can be performed for 1 out of 2 files.")
      .contains("1/1 source file has been analyzed");
  }

  @Test
  public void test_modified_dependency() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("unknown", "mod", "other")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), importsAsByteArray(Collections.emptyList()));
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py") , serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 1 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Optimized analysis can be performed for 0 out of 2 files.")
      .contains("1/1 source file has been analyzed");
  }

  @Test
  public void test_no_file_modified_missing_entry() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 0 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Optimized analysis can be performed for 0 out of 2 files.")
      .contains("2/2 source files have been analyzed");
  }

  @Test
  public void test_no_file_modified_missing_imports() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    byte[] serializedSymbolTable = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("x", "main.x", null))).toByteArray();
    byte[] outdatedEntry = toProtobufModuleDescriptor(Set.of(new VariableDescriptor("outdated", "mod.outdated", null))).toByteArray();
    readCache.put(projectSymbolTableCacheKey("moduleKey:main.py"), serializedSymbolTable);
    readCache.put(projectSymbolTableCacheKey("moduleKey:mod.py"), outdatedEntry);

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 0 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Optimized analysis can be performed for 0 out of 2 files.")
      .contains("2/2 source files have been analyzed");
  }

  @Test
  public void test_no_file_modified_missing_descriptors() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    readCache.put(importsMapCacheKey("moduleKey:main.py"), importsAsByteArray(List.of("mod")));
    readCache.put(importsMapCacheKey("moduleKey:mod.py"), importsAsByteArray(Collections.emptyList()));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO))
      .contains("Cached information of global symbols will be used for 0 out of 2 main files. Global symbols will be recomputed for the remaining files.")
      .contains("Optimized analysis can be performed for 0 out of 2 files.")
      .contains("2/2 source files have been analyzed");
  }

  @Test
  public void test_test_files_not_using_cache() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.TEST);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.CHANGED, InputFile.Type.TEST);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isTrue();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isFalse();
  }

  @Test
  public void test_pr_analysis_disabled() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    context.settings().setProperty("sonar.python.skipUnchanged", false);
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO)).doesNotContain("Using cached data to retrieve global symbols.");
  }

  @Test
  public void test_pr_analysis_enabled() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    SensorContext mockContext = spy(context);
    when(mockContext.canSkipUnchangedFiles()).thenReturn(true);
    context.settings().setProperty("sonar.python.skipUnchanged", false);
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(mockContext);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Using cached data to retrieve global symbols.");
  }

  @Test
  public void test_disabled_cache() {
    file1 = createInputFile(baseDir, "main.py", InputFile.Status.CHANGED, InputFile.Type.MAIN);
    file2 = createInputFile(baseDir, "mod.py", InputFile.Status.SAME, InputFile.Type.MAIN);

    List<InputFile> inputFiles = new ArrayList<>(Arrays.asList(file1, file2));

    cacheContext = new CacheContextImpl(false, new PythonWriteCacheImpl(new TestWriteCache()), new PythonReadCacheImpl(new TestReadCache()));
    pythonIndexer = new SonarQubePythonIndexer(inputFiles, cacheContext);
    pythonIndexer.buildOnce(context);

    assertThat(pythonIndexer.canBeScannedWithoutParsing(file1)).isFalse();
    assertThat(pythonIndexer.canBeScannedWithoutParsing(file2)).isFalse();
    assertThat(logTester.logs(LoggerLevel.INFO)).doesNotContain("Using cached data to retrieve global symbols.");
  }

  @Test
  public void test_regular_scan_when_scan_without_parsing_fails() {
    List<InputFile> files = List.of(createInputFile(baseDir, "main.py", InputFile.Status.SAME, InputFile.Type.MAIN));
    PythonIndexer.GlobalSymbolsScanner globalSymbolsScanner = spy(
      new SonarQubePythonIndexer(files, cacheContext).new GlobalSymbolsScanner(context)
    );
    when(globalSymbolsScanner.canBeScannedWithoutParsing(any())).thenReturn(true);
    globalSymbolsScanner.execute(files, context);

    assertThat(logTester.logs(LoggerLevel.INFO)).contains("1/1 source file has been analyzed");
  }

  private byte[] importsAsByteArray(List<String> mod) {
    return String.join(";", mod).getBytes(StandardCharsets.UTF_8);
  }

  private String importsMapCacheKey(String key) {
    return IMPORTS_MAP_CACHE_KEY_PREFIX + key;
  }

  private String projectSymbolTableCacheKey(String key) {
    return PROJECT_SYMBOL_TABLE_CACHE_KEY_PREFIX + key;
  }
}
