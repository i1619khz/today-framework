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

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.taketoday.framework.web.server.AbstractConfigurableWebServerFactory;
import cn.taketoday.framework.web.server.Compression;
import cn.taketoday.framework.web.server.Http2;
import cn.taketoday.framework.web.server.Shutdown;
import cn.taketoday.framework.web.server.Ssl;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.StringUtils;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.Undertow.Builder;
import io.undertow.UndertowOptions;

/**
 * Delegate class used by {@link UndertowServletWebServerFactory} and
 * {@link UndertowReactiveWebServerFactory}.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
class UndertowWebServerFactoryDelegate {

  private Set<UndertowBuilderCustomizer> builderCustomizers = new LinkedHashSet<>();

  @Nullable
  private Integer bufferSize;

  @Nullable
  private Integer ioThreads;

  @Nullable
  private Integer workerThreads;

  @Nullable
  private Boolean directBuffers;

  @Nullable
  private File accessLogDirectory;

  @Nullable
  private String accessLogPattern;

  @Nullable
  private String accessLogPrefix;

  @Nullable
  private String accessLogSuffix;

  private boolean accessLogEnabled = false;

  private boolean accessLogRotate = true;

  private boolean useForwardHeaders;

  void setBuilderCustomizers(Collection<? extends UndertowBuilderCustomizer> customizers) {
    Assert.notNull(customizers, "Customizers must not be null");
    this.builderCustomizers = new LinkedHashSet<>(customizers);
  }

  void addBuilderCustomizers(UndertowBuilderCustomizer... customizers) {
    Assert.notNull(customizers, "Customizers must not be null");
    this.builderCustomizers.addAll(Arrays.asList(customizers));
  }

  Collection<UndertowBuilderCustomizer> getBuilderCustomizers() {
    return this.builderCustomizers;
  }

  void setBufferSize(Integer bufferSize) {
    this.bufferSize = bufferSize;
  }

  void setIoThreads(Integer ioThreads) {
    this.ioThreads = ioThreads;
  }

  void setWorkerThreads(Integer workerThreads) {
    this.workerThreads = workerThreads;
  }

  void setUseDirectBuffers(Boolean directBuffers) {
    this.directBuffers = directBuffers;
  }

  void setAccessLogDirectory(File accessLogDirectory) {
    this.accessLogDirectory = accessLogDirectory;
  }

  void setAccessLogPattern(String accessLogPattern) {
    this.accessLogPattern = accessLogPattern;
  }

  void setAccessLogPrefix(String accessLogPrefix) {
    this.accessLogPrefix = accessLogPrefix;
  }

  @Nullable
  String getAccessLogPrefix() {
    return this.accessLogPrefix;
  }

  void setAccessLogSuffix(String accessLogSuffix) {
    this.accessLogSuffix = accessLogSuffix;
  }

  void setAccessLogEnabled(boolean accessLogEnabled) {
    this.accessLogEnabled = accessLogEnabled;
  }

  boolean isAccessLogEnabled() {
    return this.accessLogEnabled;
  }

  void setAccessLogRotate(boolean accessLogRotate) {
    this.accessLogRotate = accessLogRotate;
  }

  void setUseForwardHeaders(boolean useForwardHeaders) {
    this.useForwardHeaders = useForwardHeaders;
  }

  boolean isUseForwardHeaders() {
    return this.useForwardHeaders;
  }

  Builder createBuilder(AbstractConfigurableWebServerFactory factory) {
    Ssl ssl = factory.getSsl();
    InetAddress address = factory.getAddress();
    int port = factory.getPort();
    Builder builder = Undertow.builder();
    if (this.bufferSize != null) {
      builder.setBufferSize(this.bufferSize);
    }
    if (this.ioThreads != null) {
      builder.setIoThreads(this.ioThreads);
    }
    if (this.workerThreads != null) {
      builder.setWorkerThreads(this.workerThreads);
    }
    if (this.directBuffers != null) {
      builder.setDirectBuffers(this.directBuffers);
    }
    Http2 http2 = factory.getHttp2();
    if (http2 != null) {
      builder.setServerOption(UndertowOptions.ENABLE_HTTP2, http2.isEnabled());
    }
    if (ssl != null && ssl.isEnabled()) {
      new SslBuilderCustomizer(factory.getPort(), address, ssl, factory.getOrCreateSslStoreProvider())
              .customize(builder);
    }
    else {
      builder.addHttpListener(port, (address != null) ? address.getHostAddress() : "0.0.0.0");
    }
    builder.setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 0);
    for (UndertowBuilderCustomizer customizer : this.builderCustomizers) {
      customizer.customize(builder);
    }
    return builder;
  }

  List<HttpHandlerFactory> createHttpHandlerFactories(AbstractConfigurableWebServerFactory webServerFactory,
          HttpHandlerFactory... initialHttpHandlerFactories) {
    List<HttpHandlerFactory> factories = createHttpHandlerFactories(
            webServerFactory.getCompression(),
            useForwardHeaders,
            webServerFactory.getServerHeader(),
            webServerFactory.getShutdown(),
            initialHttpHandlerFactories
    );

    if (isAccessLogEnabled()) {
      factories.add(new AccessLogHttpHandlerFactory(accessLogDirectory, accessLogPattern,
              accessLogPrefix, accessLogSuffix, accessLogRotate));
    }
    return factories;
  }

  static List<HttpHandlerFactory> createHttpHandlerFactories(
          @Nullable Compression compression, boolean useForwardHeaders,
          String serverHeader, Shutdown shutdown, HttpHandlerFactory... initialHttpHandlerFactories) {
    List<HttpHandlerFactory> factories = new ArrayList<>(Arrays.asList(initialHttpHandlerFactories));
    if (compression != null && compression.isEnabled()) {
      factories.add(new CompressionHttpHandlerFactory(compression));
    }
    if (useForwardHeaders) {
      factories.add(Handlers::proxyPeerAddress);
    }
    if (StringUtils.hasText(serverHeader)) {
      factories.add((next) -> Handlers.header(next, "Server", serverHeader));
    }
    if (shutdown == Shutdown.GRACEFUL) {
      factories.add(Handlers::gracefulShutdown);
    }
    return factories;
  }

}
