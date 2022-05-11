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

package cn.taketoday.web.context.support;

import java.security.Principal;

import cn.taketoday.context.ApplicationEvent;
import cn.taketoday.context.ApplicationEventPublisher;
import cn.taketoday.lang.Nullable;
import cn.taketoday.web.RequestContext;
import cn.taketoday.web.RequestContextUtils;
import cn.taketoday.web.RequestHandledListener;
import cn.taketoday.web.ServletDetector;
import cn.taketoday.web.servlet.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * publish {@link RequestHandledEvent}
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see RequestHandledEvent
 * @since 4.0 2022/5/11 10:44
 */
public class RequestHandledEventPublisher implements RequestHandledListener {

  private final ApplicationEventPublisher eventPublisher;

  public RequestHandledEventPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void requestHandled(RequestContext request, long startTime, @Nullable Throwable failureCause) {
    // Whether we succeeded, publish an event.
    var processingTime = System.currentTimeMillis() - startTime;
    var event = getRequestHandledEvent(request, failureCause, processingTime);
    eventPublisher.publishEvent(event);
  }

  /**
   * create a {@link RequestHandledEvent} for the given request.
   *
   * @param request request context
   * @param failureCause failure cause
   * @param processingTime processing time
   * @return the event
   */
  protected ApplicationEvent getRequestHandledEvent(
          RequestContext request, @Nullable Throwable failureCause, long processingTime) {
    if (ServletDetector.runningInServlet(request)) {
      return ServletDelegate.getRequestHandledEvent(this, request, failureCause, processingTime);
    }
    return new RequestHandledEvent(this,
            request.getRequestPath(), request.getRemoteAddress(),
            request.getMethodValue(),
            RequestContextUtils.getSessionId(request), null,
            processingTime, failureCause, request.getStatus());
  }

  static class ServletDelegate {

    static ApplicationEvent getRequestHandledEvent(Object source,
            RequestContext request, @Nullable Throwable failureCause, long processingTime) {
      HttpServletRequest servletRequest = ServletUtils.getServletRequest(request);
      Principal userPrincipal = servletRequest.getUserPrincipal();
      String userName = userPrincipal != null ? userPrincipal.getName() : null;
      return new ServletRequestHandledEvent(source,
              servletRequest.getRequestURI(), servletRequest.getRemoteAddr(),
              servletRequest.getMethod(), getServletConfig().getServletName(),
              ServletUtils.getSessionId(servletRequest), userName,
              processingTime, failureCause, request.getStatus());
    }

  }
}
