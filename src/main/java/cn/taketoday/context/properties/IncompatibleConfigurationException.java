/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2022 All Rights Reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.taketoday.context.properties;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Exception thrown when the application has configured an incompatible set of
 * {@link ConfigurationProperties} keys.
 *
 * @author Brian Clozel
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
public class IncompatibleConfigurationException extends RuntimeException {

  private final List<String> incompatibleKeys;

  public IncompatibleConfigurationException(String... incompatibleKeys) {
    super("The following configuration properties have incompatible values: " + Arrays.toString(incompatibleKeys));
    this.incompatibleKeys = Arrays.asList(incompatibleKeys);
  }

  public Collection<String> getIncompatibleKeys() {
    return this.incompatibleKeys;
  }

}
