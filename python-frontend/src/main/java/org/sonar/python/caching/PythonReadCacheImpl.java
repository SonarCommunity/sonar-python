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

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.sensor.cache.ReadCache;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.python.api.caching.PythonReadCache;

public class PythonReadCacheImpl implements PythonReadCache {
  private static final Logger LOG = Loggers.get(PythonReadCacheImpl.class);

  private final ReadCache readCache;

  public PythonReadCacheImpl(ReadCache readCache) {
    this.readCache = readCache;
  }

  @Override
  public InputStream read(String key) {
    return readCache.read(key);
  }

  @CheckForNull
  @Override
  public byte[] readBytes(String key) {
    if (readCache.contains(key)) {
      try (var in = read(key)) {
        return in.readAllBytes();
      } catch (IOException e) {
        LOG.debug("Unable to read data for key: \"{}\"", key);
      }
    } else {
      LOG.trace(() -> String.format("Cache miss for key '%s'", key));
    }
    return null;
  }

  @Override
  public boolean contains(String key) {
    return readCache.contains(key);
  }
}
