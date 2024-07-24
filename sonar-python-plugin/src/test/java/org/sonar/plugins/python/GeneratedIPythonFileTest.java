/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2024 SonarSource SA
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

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.plugins.python.PythonInputFile.Kind;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedIPythonFileTest {

  @Test
  void shouldHaveIPythonKind() {
    PythonInputFile inputFile = new GeneratedIPythonFile(createWrappedFile(), "");
    assertThat(inputFile.kind()).isEqualTo(Kind.IPYTHON);
  }

  @Test
  void shouldReturnTheWrappedFile() {
    InputFile wrappedFile = createWrappedFile();
    GeneratedIPythonFile inputFile = new GeneratedIPythonFile(wrappedFile, "");
    assertThat(inputFile.wrappedFile()).isEqualTo(wrappedFile);
  }

  @Test
  void shouldHaveTheWrappedFileToString() {
    InputFile wrappedFile = createWrappedFile();
    PythonInputFile inputFile = new GeneratedIPythonFile(wrappedFile, "");
    assertThat(inputFile).hasToString(wrappedFile.toString());
  }

  @Test
  void shouldHaveTheContentPassed() throws IOException {
    InputFile wrappedFile = createWrappedFile();
    PythonInputFile inputFile = new GeneratedIPythonFile(wrappedFile, "test");
    assertThat(inputFile.contents()).isEqualTo("test");

  }

  private InputFile createWrappedFile() {
    return TestInputFileBuilder.create("moduleKey", "name").setContents("Some content").build();
  }
}