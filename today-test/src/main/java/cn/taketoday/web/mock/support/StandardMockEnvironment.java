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

package cn.taketoday.web.mock.support;

import cn.taketoday.core.env.Environment;
import cn.taketoday.core.env.PropertySource;
import cn.taketoday.core.env.PropertySource.StubPropertySource;
import cn.taketoday.core.env.PropertySources;
import cn.taketoday.core.env.StandardEnvironment;
import cn.taketoday.jndi.JndiLocatorDelegate;
import cn.taketoday.jndi.JndiPropertySource;
import cn.taketoday.lang.Nullable;
import cn.taketoday.mock.api.MockConfig;
import cn.taketoday.mock.api.MockContext;
import cn.taketoday.util.ClassUtils;
import cn.taketoday.web.mock.ConfigurableWebEnvironment;
import cn.taketoday.web.mock.MockConfigPropertySource;
import cn.taketoday.web.mock.MockContextPropertySource;

/**
 * {@link Environment} implementation to be used by {@code Servlet}-based web
 * applications. All web-related (servlet-based) {@code ApplicationContext} classes
 * initialize an instance by default.
 *
 * <p>Contributes {@code ServletConfig}, {@code MockContext}, and JNDI-based
 * {@link PropertySource} instances. See {@link #customizePropertySources} method
 * documentation for details.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see StandardEnvironment
 * @since 4.0
 */
public class StandardMockEnvironment extends StandardEnvironment implements ConfigurableWebEnvironment {

  /** Servlet context init parameters property source name: {@value}. */
  public static final String MOCK_CONTEXT_PROPERTY_SOURCE_NAME = "mockContextInitParams";

  /** Servlet config init parameters property source name: {@value}. */
  public static final String MOCK_CONFIG_PROPERTY_SOURCE_NAME = "mockConfigInitParams";

  /** JNDI property source name: {@value}. */
  public static final String JNDI_PROPERTY_SOURCE_NAME = "jndiProperties";

  // Defensive reference to JNDI API for JDK 9+ (optional java.naming module)
  private static final boolean jndiPresent = ClassUtils.isPresent(
          "javax.naming.InitialContext", StandardMockEnvironment.class.getClassLoader());

  /**
   * Create a new {@code StandardServletEnvironment} instance.
   */
  public StandardMockEnvironment() { }

  /**
   * Create a new {@code StandardServletEnvironment} instance with a specific {@link PropertySources} instance.
   *
   * @param propertySources property sources to use
   */
  protected StandardMockEnvironment(PropertySources propertySources) {
    super(propertySources);
  }

  /**
   * Customize the set of property sources with those contributed by superclasses as
   * well as those appropriate for standard servlet-based environments:
   * <ul>
   * <li>{@value #MOCK_CONFIG_PROPERTY_SOURCE_NAME}
   * <li>{@value #MOCK_CONTEXT_PROPERTY_SOURCE_NAME}
   * <li>{@value #JNDI_PROPERTY_SOURCE_NAME}
   * </ul>
   * <p>Properties present in {@value #MOCK_CONFIG_PROPERTY_SOURCE_NAME} will
   * take precedence over those in {@value #MOCK_CONTEXT_PROPERTY_SOURCE_NAME}, and
   * properties found in either of the above take precedence over those found in
   * {@value #JNDI_PROPERTY_SOURCE_NAME}.
   * <p>Properties in any of the above will take precedence over system properties and
   * environment variables contributed by the {@link StandardEnvironment} superclass.
   * <p>The {@code Servlet}-related property sources are added as
   * {@link StubPropertySource stubs} at this stage, and will be
   * {@linkplain #initPropertySources(MockContext, MockConfig) fully initialized}
   * once the actual {@link MockContext} object becomes available.
   * <p>Addition of {@value #JNDI_PROPERTY_SOURCE_NAME} can be disabled with
   *
   * @see StandardEnvironment#customizePropertySources
   * @see cn.taketoday.core.env.AbstractEnvironment#customizePropertySources
   * @see MockConfigPropertySource
   * @see MockContextPropertySource
   * @see cn.taketoday.jndi.JndiPropertySource
   * @see cn.taketoday.context.support.AbstractApplicationContext#initPropertySources
   * @see #initPropertySources(MockContext, MockConfig)
   */
  @Override
  protected void customizePropertySources(PropertySources propertySources) {
    propertySources.addLast(new StubPropertySource(MOCK_CONFIG_PROPERTY_SOURCE_NAME));
    propertySources.addLast(new StubPropertySource(MOCK_CONTEXT_PROPERTY_SOURCE_NAME));
    if (jndiPresent && JndiLocatorDelegate.isDefaultJndiEnvironmentAvailable()) {
      propertySources.addLast(new JndiPropertySource(JNDI_PROPERTY_SOURCE_NAME));
    }
    super.customizePropertySources(propertySources);
  }

  @Override
  public void initPropertySources(@Nullable MockContext mockContext, @Nullable MockConfig mockConfig) {
    WebApplicationContextUtils.initMockPropertySources(getPropertySources(), mockContext, mockConfig);
  }

}
