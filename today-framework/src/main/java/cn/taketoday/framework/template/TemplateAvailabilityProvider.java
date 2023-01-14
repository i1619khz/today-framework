/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2023 All Rights Reserved.
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
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */

package cn.taketoday.framework.template;

import cn.taketoday.core.env.Environment;
import cn.taketoday.core.io.ResourceLoader;

/**
 * Indicates the availability of view templates for a particular templating engine such as
 * FreeMarker.
 *
 * @author Andy Wilkinson
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
@FunctionalInterface
public interface TemplateAvailabilityProvider {

  /**
   * Returns {@code true} if a template is available for the given {@code view}.
   *
   * @param view the view name
   * @param environment the environment
   * @param classLoader the class loader
   * @param resourceLoader the resource loader
   * @return if the template is available
   */
  boolean isTemplateAvailable(String view, Environment environment, ClassLoader classLoader,
          ResourceLoader resourceLoader);

}
