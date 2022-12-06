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
package org.sonar.python.caching;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.batch.sensor.cache.ReadCache;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PythonReadCacheImplTest {

  @Rule
  public LogTester logTester = new LogTester();

  @Test
  public void read_bytes() {
    byte[] bytes = "hello".getBytes();
    ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

    ReadCache readCache = mock(ReadCache.class);
    when(readCache.read("key")).thenReturn(inputStream);
    when(readCache.contains("key")).thenReturn(true);

    PythonReadCacheImpl pythonReadCache = new PythonReadCacheImpl(readCache);
    byte[] result = pythonReadCache.readBytes("key");

    assertThat(result).isEqualTo(bytes);
  }

  @Test
  public void read_bytes_no_such_key() {
    ReadCache readCache = mock(ReadCache.class);
    when(readCache.contains("key")).thenReturn(false);

    PythonReadCacheImpl pythonReadCache = new PythonReadCacheImpl(readCache);
    byte[] result = pythonReadCache.readBytes("key");

    assertThat(result).isNull();
  }

  @Test
  public void read_bytes_io_exception() throws IOException {
    InputStream inputStream = mock(InputStream.class);
    when(inputStream.readAllBytes()).thenThrow(IOException.class);

    ReadCache readCache = mock(ReadCache.class);
    when(readCache.read("key")).thenReturn(inputStream);
    when(readCache.contains("key")).thenReturn(true);

    PythonReadCacheImpl pythonReadCache = new PythonReadCacheImpl(readCache);
    byte[] result = pythonReadCache.readBytes("key");

    assertThat(result)
      .isNull();

    assertThat(logTester.logs(LoggerLevel.DEBUG))
      .containsExactly("Unable to read data for key: \"key\"");
  }

  @Test
  public void contains() {
    ReadCache readCache = mock(ReadCache.class);
    when(readCache.contains("exists")).thenReturn(true);
    when(readCache.contains("doesNotExist")).thenReturn(false);

    PythonReadCacheImpl pythonReadCache = new PythonReadCacheImpl(readCache);

    assertThat(pythonReadCache.contains("exists")).isTrue();
    assertThat(pythonReadCache.contains("doesNotExists")).isFalse();
  }
}
