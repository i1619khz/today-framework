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

package cn.taketoday.web.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import cn.taketoday.http.HttpHeaders;
import cn.taketoday.lang.Nullable;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.Session;

/**
 * Standard jakarta.websocket.Session WebSocketSession
 *
 * @author TODAY 2021/4/5 14:25
 * @since 3.0
 */
public class StandardWebSocketSession extends NativeWebSocketSession<Session> {

  @Nullable
  private final InetSocketAddress localAddress;

  @Nullable
  private final InetSocketAddress remoteAddress;

  @Nullable
  private String acceptedProtocol;

  public StandardWebSocketSession(HttpHeaders handshakeHeaders,
          @Nullable InetSocketAddress localAddress, @Nullable InetSocketAddress remoteAddress) {
    super(handshakeHeaders);
    this.localAddress = localAddress;
    this.remoteAddress = remoteAddress;
  }

  @Override
  @Nullable
  public InetSocketAddress getLocalAddress() {
    return localAddress;
  }

  @Override
  @Nullable
  public InetSocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  @Nullable
  @Override
  public String getAcceptedProtocol() {
    return acceptedProtocol;
  }

  @Override
  public void sendText(String text) throws IOException {
    obtainNativeSession().getBasicRemote().sendText(text);
  }

  @Override
  public void sendPartialText(String partialMessage, boolean isLast) throws IOException {
    obtainNativeSession().getBasicRemote().sendText(partialMessage, isLast);
  }

  @Override
  public void sendBinary(BinaryMessage data) throws IOException {
    obtainNativeSession().getBasicRemote().sendBinary(data.getPayload());
  }

  @Override
  public void sendPartialBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
    obtainNativeSession().getBasicRemote().sendBinary(partialByte, isLast);
  }

  @Override
  public void sendPing(PingMessage message) throws IOException {
    obtainNativeSession().getBasicRemote().sendPing(message.getPayload());
  }

  @Override
  public void sendPong(PongMessage message) throws IOException {
    obtainNativeSession().getBasicRemote().sendPong(message.getPayload());
  }

  @Override
  public boolean isSecure() {
    return obtainNativeSession().isSecure();
  }

  @Override
  public boolean isOpen() {
    return obtainNativeSession().isOpen();
  }

  @Override
  public void close() throws IOException {
    obtainNativeSession().close();
  }

  @Override
  public void close(final CloseStatus status) throws IOException {
    final CloseReason closeReason = new CloseReason(
            CloseCodes.getCloseCode(status.getCode()), status.getReason());
    obtainNativeSession().close(closeReason);
  }

  @Override
  public void initializeNativeSession(Session session) {
    super.initializeNativeSession(session);
    this.acceptedProtocol = session.getNegotiatedSubprotocol();
  }

}
