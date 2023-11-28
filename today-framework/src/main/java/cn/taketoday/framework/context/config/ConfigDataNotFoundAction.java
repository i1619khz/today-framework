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
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */

package cn.taketoday.framework.context.config;

import cn.taketoday.logging.Logger;

/**
 * Action to take when an uncaught {@link ConfigDataNotFoundException} is thrown.
 *
 * @author Phillip Webb
 * @since 4.0
 */
public enum ConfigDataNotFoundAction {

  /**
   * Throw the exception to fail startup.
   */
  FAIL {
    @Override
    void handle(Logger logger, ConfigDataNotFoundException ex) {
      throw ex;
    }

  },

  /**
   * Ignore the exception and continue processing the remaining locations.
   */
  IGNORE {
    @Override
    void handle(Logger logger, ConfigDataNotFoundException ex) {
      logger.trace("Ignoring missing config data {}", ex.getReferenceDescription());
    }

  };

  /**
   * Handle the given exception.
   *
   * @param logger the logger used for output {@code ConfigDataLocation})
   * @param ex the exception to handle
   */
  abstract void handle(Logger logger, ConfigDataNotFoundException ex);

}
