/*
 * Copyright 2017 - 2023 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */

package cn.taketoday.maven;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Provides access to various versions.
 *
 * @author Andy Wilkinson
 */
class Versions {

  private final Map<String, String> versions;

  Versions() {
    this.versions = loadVersions();
  }

  private static Map<String, String> loadVersions() {
    try (InputStream input = Versions.class.getClassLoader().getResourceAsStream("extracted-versions.properties")) {
      Properties properties = new Properties();
      properties.load(input);
      Map<String, String> versions = new HashMap<>();
      properties.forEach((key, value) -> versions.put((String) key, (String) value));
      return versions;
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  String get(String name) {
    return this.versions.get(name);
  }

  Map<String, String> asMap() {
    return Collections.unmodifiableMap(this.versions);
  }

}
