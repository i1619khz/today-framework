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

package cn.taketoday.annotation.config.web.reactive;

import cn.taketoday.core.ApplicationTemp;
import cn.taketoday.core.Ordered;
import cn.taketoday.core.ssl.SslBundles;
import cn.taketoday.web.server.reactive.ConfigurableReactiveWebServerFactory;
import cn.taketoday.web.server.ServerProperties;
import cn.taketoday.web.server.WebServerFactoryCustomizer;
import cn.taketoday.lang.Nullable;

/**
 * {@link WebServerFactoryCustomizer} to apply {@link ServerProperties} to reactive
 * servers.
 *
 * @author Brian Clozel
 * @author Yunkun Huang
 * @author Scott Frederick
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2022/10/21 11:40
 */
public class ReactiveWebServerFactoryCustomizer
        implements WebServerFactoryCustomizer<ConfigurableReactiveWebServerFactory>, Ordered {

  @Nullable
  private final SslBundles sslBundles;

  @Nullable
  private final ApplicationTemp applicationTemp;

  private final ServerProperties serverProperties;

  /**
   * Create a new {@link ReactiveWebServerFactoryCustomizer} instance.
   *
   * @param serverProperties the server properties
   */
  public ReactiveWebServerFactoryCustomizer(ServerProperties serverProperties, @Nullable SslBundles sslBundles) {
    this(serverProperties, sslBundles, null);
  }

  /**
   * Create a new {@link ReactiveWebServerFactoryCustomizer} instance.
   *
   * @param serverProperties the server properties
   * @param sslBundles the SSL bundles
   */
  public ReactiveWebServerFactoryCustomizer(ServerProperties serverProperties,
          @Nullable SslBundles sslBundles, @Nullable ApplicationTemp applicationTemp) {
    this.serverProperties = serverProperties;
    this.sslBundles = sslBundles;
    this.applicationTemp = applicationTemp;
  }

  @Override
  public int getOrder() {
    return 0;
  }

  @Override
  public void customize(ConfigurableReactiveWebServerFactory factory) {
    serverProperties.applyTo(factory, sslBundles, applicationTemp);
  }

}
