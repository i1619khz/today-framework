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

package cn.taketoday.framework.template;

import cn.taketoday.context.properties.ConfigurationProperties;
import cn.taketoday.core.Ordered;
import cn.taketoday.lang.Assert;
import cn.taketoday.web.view.AbstractTemplateViewResolver;

/**
 * Base class for {@link ConfigurationProperties @ConfigurationProperties} of a
 * {@link AbstractTemplateViewResolver}.
 *
 * @author Andy Wilkinson
 * @since 4.0
 */
public abstract class AbstractTemplateViewResolverProperties extends AbstractViewResolverProperties {

  /**
   * Prefix that gets prepended to view names when building a URL.
   */
  private String prefix;

  /**
   * Suffix that gets appended to view names when building a URL.
   */
  private String suffix;

  /**
   * Name of the RequestContext attribute for all views.
   */
  private String requestContextAttribute;

  /**
   * Whether all request attributes should be added to the model prior to merging with
   * the template.
   */
  private boolean exposeRequestAttributes = false;

  /**
   * Whether all HttpSession attributes should be added to the model prior to merging
   * with the template.
   */
  private boolean exposeSessionAttributes = false;

  /**
   * Whether HttpServletRequest attributes are allowed to override (hide) controller
   * generated model attributes of the same name.
   */
  private boolean allowRequestOverride = false;

  /**
   * Whether HttpSession attributes are allowed to override (hide) controller generated
   * model attributes of the same name.
   */
  private boolean allowSessionOverride = false;

  protected AbstractTemplateViewResolverProperties(String defaultPrefix, String defaultSuffix) {
    this.prefix = defaultPrefix;
    this.suffix = defaultSuffix;
  }

  public String getPrefix() {
    return this.prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String getSuffix() {
    return this.suffix;
  }

  public void setSuffix(String suffix) {
    this.suffix = suffix;
  }

  public String getRequestContextAttribute() {
    return this.requestContextAttribute;
  }

  public void setRequestContextAttribute(String requestContextAttribute) {
    this.requestContextAttribute = requestContextAttribute;
  }

  public boolean isExposeRequestAttributes() {
    return this.exposeRequestAttributes;
  }

  public void setExposeRequestAttributes(boolean exposeRequestAttributes) {
    this.exposeRequestAttributes = exposeRequestAttributes;
  }

  public boolean isExposeSessionAttributes() {
    return this.exposeSessionAttributes;
  }

  public void setExposeSessionAttributes(boolean exposeSessionAttributes) {
    this.exposeSessionAttributes = exposeSessionAttributes;
  }

  public boolean isAllowRequestOverride() {
    return this.allowRequestOverride;
  }

  public void setAllowRequestOverride(boolean allowRequestOverride) {
    this.allowRequestOverride = allowRequestOverride;
  }

  public boolean isAllowSessionOverride() {
    return this.allowSessionOverride;
  }

  public void setAllowSessionOverride(boolean allowSessionOverride) {
    this.allowSessionOverride = allowSessionOverride;
  }

  /**
   * Apply the given properties to a {@link AbstractTemplateViewResolver}. Use Object in
   * signature to avoid runtime dependency on MVC, which means that the template engine
   * can be used in a non-web application.
   *
   * @param viewResolver the resolver to apply the properties to.
   */
  public void applyToMvcViewResolver(Object viewResolver) {
    Assert.isInstanceOf(AbstractTemplateViewResolver.class, viewResolver,
            () -> "ViewResolver is not an instance of AbstractTemplateViewResolver :" + viewResolver);
    AbstractTemplateViewResolver resolver = (AbstractTemplateViewResolver) viewResolver;
    resolver.setPrefix(getPrefix());
    resolver.setSuffix(getSuffix());
    resolver.setCache(isCache());
    if (getContentType() != null) {
      resolver.setContentType(getContentType().toString());
    }
    resolver.setViewNames(getViewNames());
    resolver.setExposeRequestAttributes(isExposeRequestAttributes());
    resolver.setAllowRequestOverride(isAllowRequestOverride());
    resolver.setAllowSessionOverride(isAllowSessionOverride());
    resolver.setExposeSessionAttributes(isExposeSessionAttributes());
    resolver.setRequestContextAttribute(getRequestContextAttribute());
    // The resolver usually acts as a fallback resolver (e.g. like a
    // InternalResourceViewResolver) so it needs to have low precedence
    resolver.setOrder(Ordered.LOWEST_PRECEDENCE - 5);
  }

}
