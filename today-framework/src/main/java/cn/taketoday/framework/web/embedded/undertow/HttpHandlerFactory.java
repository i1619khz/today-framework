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

package cn.taketoday.framework.web.embedded.undertow;

import java.io.Closeable;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;

/**
 * Factory used by {@link UndertowServletWebServer} to add {@link HttpHandler
 * HttpHandlers}. Instances returned from this factory may optionally implement the
 * following interfaces:
 * <ul>
 * <li>{@link Closeable} - if they wish to be closed just before server stops.</li>
 * <li>{@link GracefulShutdownHandler} - if they wish to manage graceful shutdown.</li>
 * </ul>
 *
 * @author Phillip Webb
 * @since 4.0
 */
@FunctionalInterface
public interface HttpHandlerFactory {

  /**
   * Create the {@link HttpHandler} instance that should be added.
   *
   * @param next the next handler in the chain
   * @return the new HTTP handler instance
   */
  HttpHandler getHandler(HttpHandler next);

}
