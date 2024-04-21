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

package cn.taketoday.web.servlet.view;

import cn.taketoday.lang.Nullable;
import cn.taketoday.util.ClassUtils;
import cn.taketoday.web.view.AbstractUrlBasedView;
import cn.taketoday.web.view.UrlBasedViewResolver;

/**
 * Convenient subclass of {@link UrlBasedViewResolver} that supports
 * {@link InternalResourceView} (i.e. Servlets and JSPs) and subclasses
 * such as {@link JstlView}.
 *
 * <p>The view class for all views generated by this resolver can be specified
 * via {@link #setViewClass}. See {@link UrlBasedViewResolver}'s javadoc for details.
 * The default is {@link InternalResourceView}, or {@link JstlView} if the
 * JSTL API is present.
 *
 * <p>BTW, it's good practice to put JSP files that just serve as views under
 * WEB-INF, to hide them from direct access (e.g. via a manually entered URL).
 * Only controllers will be able to access them then.
 *
 * <p><b>Note:</b> When chaining ViewResolvers, an InternalResourceViewResolver
 * always needs to be last, as it will attempt to resolve any view name,
 * no matter whether the underlying resource actually exists.
 *
 * @author Juergen Hoeller
 * @see #setViewClass
 * @see #setPrefix
 * @see #setSuffix
 * @see #setRequestContextAttribute
 * @see InternalResourceView
 * @see JstlView
 * @since 4.0
 */
@Deprecated
public class InternalResourceViewResolver extends UrlBasedViewResolver {
  static final boolean jstlPresent = ClassUtils.isPresent(
          "jakarta.servlet.jsp.jstl.core.Config", InternalResourceViewResolver.class.getClassLoader());

  @Nullable
  private Boolean alwaysInclude;

  /**
   * Sets the default {@link #setViewClass view class} to {@link #requiredViewClass}:
   * by default {@link InternalResourceView}, or {@link JstlView} if the JSTL API
   * is present.
   */
  public InternalResourceViewResolver() {
    Class<?> viewClass = requiredViewClass();
    if (InternalResourceView.class == viewClass && jstlPresent) {
      viewClass = JstlView.class;
    }
    setViewClass(viewClass);
  }

  /**
   * A convenience constructor that allows for specifying {@link #setPrefix prefix}
   * and {@link #setSuffix suffix} as constructor arguments.
   *
   * @param prefix the prefix that gets prepended to view names when building a URL
   * @param suffix the suffix that gets appended to view names when building a URL
   */
  public InternalResourceViewResolver(String prefix, String suffix) {
    this();
    setPrefix(prefix);
    setSuffix(suffix);
  }

  /**
   * Specify whether to always include the view rather than forward to it.
   * <p>Default is "false". Switch this flag on to enforce the use of a
   * Servlet include, even if a forward would be possible.
   *
   * @see InternalResourceView#setAlwaysInclude
   */
  public void setAlwaysInclude(boolean alwaysInclude) {
    this.alwaysInclude = alwaysInclude;
  }

  @Override
  protected Class<?> requiredViewClass() {
    return InternalResourceView.class;
  }

  @Override
  protected AbstractUrlBasedView instantiateView() {
    Class<?> viewClass = getViewClass();
    return viewClass == InternalResourceView.class
           ? new InternalResourceView()
           : viewClass == JstlView.class
             ? new JstlView()
             : super.instantiateView();
  }

  @Override
  protected AbstractUrlBasedView buildView(String viewName) throws Exception {
    InternalResourceView view = (InternalResourceView) super.buildView(viewName);
    if (this.alwaysInclude != null) {
      view.setAlwaysInclude(this.alwaysInclude);
    }
    view.setPreventDispatchLoop(true);
    return view;
  }

}
