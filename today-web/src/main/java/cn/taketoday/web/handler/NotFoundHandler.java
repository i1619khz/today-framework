/*
 * Copyright 2017 - 2024 the original author or authors.
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
 * along with this program. If not, see [https://www.gnu.org/licenses/]
 */

package cn.taketoday.web.handler;

import java.io.IOException;

import cn.taketoday.http.HttpStatus;
import cn.taketoday.lang.Nullable;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.web.HttpRequestHandler;
import cn.taketoday.web.RequestContext;

/**
 * Process Handler not found
 *
 * @author TODAY 2019-12-20 19:15
 */
public class NotFoundHandler implements HttpRequestHandler {

  /** Log category to use when no mapped handler is found for a request. */
  public static final String PAGE_NOT_FOUND_LOG_CATEGORY = "cn.taketoday.web.handler.PageNotFound";

  /** Additional logger to use when no mapped handler is found for a request. */
  protected static final Logger pageNotFoundLogger = LoggerFactory.getLogger(PAGE_NOT_FOUND_LOG_CATEGORY);

  /**
   * NotFoundHandler default instance
   */
  public static final NotFoundHandler instance = new NotFoundHandler();

  @Nullable
  @Override
  public Object handleRequest(RequestContext request) throws Throwable {
    return handleNotFound(request);
  }

  /**
   * Process not found
   */
  protected Object handleNotFound(RequestContext request) throws IOException {
    logNotFound(request);

    request.sendError(HttpStatus.NOT_FOUND);
    return NONE_RETURN_VALUE;
  }

  protected void logNotFound(RequestContext context) {
    if (pageNotFoundLogger.isDebugEnabled()) {
      pageNotFoundLogger.debug("No mapping for {} {}", context.getMethodValue(), context.getRequestURI());
    }
  }

}
