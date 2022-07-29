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

import java.util.function.Supplier;

import cn.taketoday.beans.factory.config.Scope;
import cn.taketoday.core.AttributeAccessor;
import cn.taketoday.lang.Nullable;
import cn.taketoday.web.RequestContext;
import cn.taketoday.web.RequestContextHolder;
import cn.taketoday.web.RequestContextUtils;
import cn.taketoday.web.WebApplicationContext;

/**
 * Abstract {@link Scope} implementation that reads from a particular scope
 * in the current thread-bound {@link cn.taketoday.core.AttributeAccessor} object.
 *
 * <p>Subclasses may wish to override the {@link #get} and {@link #remove}
 * methods to add synchronization around the call back into this super class.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2022/1/19 21:31
 */
public abstract class AbstractRequestContextScope<T extends AttributeAccessor> implements Scope {

  public Object get(T context, String beanName, Supplier<?> objectFactory) {
    Object scopedObject = getAttribute(beanName, context);
    if (scopedObject == null) {
      scopedObject = objectFactory.get();
      setAttribute(context, beanName, scopedObject);
      // Retrieve object again, registering it for implicit session attribute updates.
      // As a bonus, we also allow for potential decoration at the getAttribute level.
      Object retrievedObject = getAttribute(beanName, context);
      if (retrievedObject != null) {
        // Only proceed with retrieved object if still present (the expected case).
        // If it disappeared concurrently, we return our locally created instance.
        scopedObject = retrievedObject;
      }
    }
    return scopedObject;
  }

  protected void setAttribute(T context, String beanName, Object scopedObject) {
    context.setAttribute(beanName, scopedObject);
  }

  protected Object getAttribute(String beanName, T context) {
    return context.getAttribute(beanName);
  }

  protected Object remove(T context, String name) {
    Object scopedObject = getAttribute(name, context);
    if (scopedObject != null) {
      removeAttribute(context, name);
      return scopedObject;
    }
    else {
      return null;
    }
  }

  protected void removeAttribute(T context, String name) {
    context.removeAttribute(name);
  }

  @Override
  public void registerDestructionCallback(String name, Runnable callback) {
    RequestContext context = RequestContextHolder.get();
    // registerDestructionCallback(name, callback); TODO

  }

  @Nullable
  @Override
  public Object resolveContextualObject(String key) {
    if (WebApplicationContext.SCOPE_REQUEST.equals(key)) {
      return RequestContextHolder.get();
    }
    else if (WebApplicationContext.SCOPE_SESSION.equals(key)) {
      RequestContext context = RequestContextHolder.get();
      if (context != null) {
        return RequestContextUtils.getSession(context, true);
      }
    }
    return null;
  }

}