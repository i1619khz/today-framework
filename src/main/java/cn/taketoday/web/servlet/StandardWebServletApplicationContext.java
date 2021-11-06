/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2021 All Rights Reserved.
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.taketoday.web.servlet;

import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import cn.taketoday.beans.factory.ConfigurableBeanFactory;
import cn.taketoday.beans.factory.StandardBeanFactory;
import cn.taketoday.context.StandardApplicationContext;
import cn.taketoday.core.env.ConfigurableEnvironment;
import cn.taketoday.core.env.StandardEnvironment;
import cn.taketoday.web.RequestContextHolder;

/**
 * @author TODAY <br>
 * 2018-07-10 1:16:17
 */
public class StandardWebServletApplicationContext
        extends StandardApplicationContext implements WebServletApplicationContext {

  /** Servlet context */
  private ServletContext servletContext;

  /**
   * Default Constructor
   */
  public StandardWebServletApplicationContext() {
    this(new StandardEnvironment());
  }

  /**
   * Construct with given {@link ConfigurableEnvironment}
   *
   * @param env {@link ConfigurableEnvironment} instance
   */
  public StandardWebServletApplicationContext(ConfigurableEnvironment env) {
    setEnvironment(env);
  }

  public StandardWebServletApplicationContext(StandardBeanFactory beanFactory) {
    super(beanFactory);
  }

  public StandardWebServletApplicationContext(ServletContext servletContext) {
    this();
    this.servletContext = servletContext;
  }

  /**
   * @param classes class set
   * @param servletContext {@link ServletContext}
   * @since 2.3.3
   */
  public StandardWebServletApplicationContext(Set<Class<?>> classes, ServletContext servletContext) {
    this(servletContext);
    registerBean(classes);
    refresh();
  }

  /**
   * @param servletContext {@link ServletContext}
   * @param propertiesLocation properties location
   * @param locations package locations
   * @since 2.3.3
   */
  public StandardWebServletApplicationContext(ServletContext servletContext, String propertiesLocation, String... locations) {
    this(servletContext);
    setPropertiesLocation(propertiesLocation);
    scan(locations);
  }

  @Override
  protected void registerFrameworkComponents(ConfigurableBeanFactory beanFactory) {
    super.registerFrameworkComponents(beanFactory);
    beanFactory.registerSingleton(this);

    // @since 3.0
    final class HttpSessionFactory implements Supplier<HttpSession> {
      @Override
      public HttpSession get() {
        return ServletUtils.getHttpSession(RequestContextHolder.currentContext());
      }
    }

    beanFactory.registerResolvableDependency(HttpSession.class, new HttpSessionFactory());
    beanFactory.registerResolvableDependency(HttpServletRequest.class, factory(RequestContextHolder::currentRequest));
    beanFactory.registerResolvableDependency(HttpServletResponse.class, factory(RequestContextHolder::currentResponse));
    beanFactory.registerResolvableDependency(ServletContext.class, factory(this::getServletContext));

  }

  private static <T> Supplier<T> factory(Supplier<T> objectFactory) {
    return objectFactory;
  }

  @Override
  public String getContextPath() {
    return servletContext.getContextPath();
  }

  @Override
  public ServletContext getServletContext() {
    return servletContext;
  }

  @Override
  public void setServletContext(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

}
