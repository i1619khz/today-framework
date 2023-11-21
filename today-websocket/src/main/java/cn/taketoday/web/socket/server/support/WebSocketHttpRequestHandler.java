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

package cn.taketoday.web.socket.server.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.web.HttpRequestHandler;
import cn.taketoday.web.RequestContext;
import cn.taketoday.web.socket.WebSocketHandler;
import cn.taketoday.web.socket.server.HandshakeFailureException;
import cn.taketoday.web.socket.server.HandshakeHandler;
import cn.taketoday.web.socket.server.HandshakeInterceptor;

/**
 * A {@link HttpRequestHandler} for processing WebSocket handshake requests.
 *
 * <p>This is the main class to use when configuring a server WebSocket at a specific URL.
 * It is a very thin wrapper around a {@link WebSocketHandler} and a {@link HandshakeHandler}.
 *
 * @author Rossen Stoyanchev
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
public class WebSocketHttpRequestHandler implements HttpRequestHandler {
  private static final Logger logger = LoggerFactory.getLogger(WebSocketHttpRequestHandler.class);

  private final WebSocketHandler wsHandler;
  private final HandshakeHandler handshakeHandler;
  private final ArrayList<HandshakeInterceptor> interceptors = new ArrayList<>();

  public WebSocketHttpRequestHandler(WebSocketHandler wsHandler) {
    this(wsHandler, new DefaultHandshakeHandler());
  }

  public WebSocketHttpRequestHandler(WebSocketHandler wsHandler, HandshakeHandler handshakeHandler) {
    Assert.notNull(wsHandler, "wsHandler is required");
    Assert.notNull(handshakeHandler, "handshakeHandler is required");
    this.wsHandler = wsHandler;
    this.handshakeHandler = handshakeHandler;
  }

  /**
   * Return the WebSocketHandler.
   */
  public WebSocketHandler getWebSocketHandler() {
    return this.wsHandler;
  }

  /**
   * Return the HandshakeHandler.
   */
  public HandshakeHandler getHandshakeHandler() {
    return this.handshakeHandler;
  }

  /**
   * Configure one or more WebSocket handshake request interceptors.
   */
  public void setHandshakeInterceptors(@Nullable List<HandshakeInterceptor> interceptors) {
    this.interceptors.clear();
    if (interceptors != null) {
      this.interceptors.addAll(interceptors);
    }
  }

  /**
   * Return the configured WebSocket handshake request interceptors.
   */
  public List<HandshakeInterceptor> getHandshakeInterceptors() {
    return this.interceptors;
  }

  @Nullable
  @Override
  public Object handleRequest(RequestContext request) {
    HandshakeInterceptorChain chain = new HandshakeInterceptorChain(interceptors, wsHandler);
    HandshakeFailureException failure = null;

    try {
      if (logger.isDebugEnabled()) {
        logger.debug(request);
      }
      Map<String, Object> attributes = new HashMap<>();
      if (!chain.applyBeforeHandshake(request, attributes)) {
        return NONE_RETURN_VALUE;
      }
      handshakeHandler.doHandshake(request, wsHandler, attributes);
      chain.applyAfterHandshake(request, null);
    }
    catch (HandshakeFailureException ex) {
      failure = ex;
    }
    catch (Exception ex) {
      failure = new HandshakeFailureException(
              "Uncaught failure for request " + request.getURI() + " - " + ex.getMessage(), ex);
    }
    finally {
      if (failure != null) {
        chain.applyAfterHandshake(request, failure);
        throw failure;
      }
    }
    return NONE_RETURN_VALUE;
  }

}
