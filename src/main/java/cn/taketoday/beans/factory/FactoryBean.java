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
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */
package cn.taketoday.beans.factory;

import cn.taketoday.lang.Nullable;

/**
 * Interface to be implemented by objects used within a {@link BeanFactory}
 * which are themselves factories for individual objects. If a bean implements
 * this interface, it is used as a factory for an object to expose, not directly
 * as a bean instance that will be exposed itself.
 *
 * <p>
 * <b>NB: A bean that implements this interface cannot be used as a normal
 * bean.</b> A FactoryBean is defined in a bean style, but the object exposed
 * for bean references ({@link #getObject()}) is always the object that it creates
 * ,and initialization by this factory.
 *
 * <p>
 * The implementation of FactoryBean is a factory; its instance will cached in
 * {@link BeanFactory} as a singleton bean.
 *
 * @author TODAY <br>
 * 2018-08-03 17:38
 */
public interface FactoryBean<T> {
  /**
   * Return an instance (possibly shared or independent) of the object
   * managed by this factory.
   * <p>As with a {@link BeanFactory}, this allows support for both the
   * Singleton and Prototype design pattern.
   * <p>If this FactoryBean is not fully initialized yet at the time of
   * the call (for example because it is involved in a circular reference),
   * throw a corresponding {@link FactoryBeanNotInitializedException}.
   * <p>As of Spring 2.0, FactoryBeans are allowed to return {@code null}
   * objects. The factory will consider this as normal value to be used; it
   * will not throw a FactoryBeanNotInitializedException in this case anymore.
   * FactoryBean implementations are encouraged to throw
   * FactoryBeanNotInitializedException themselves now, as appropriate.
   *
   * @return an instance of the bean (can be {@code null})
   * @throws Exception in case of creation errors
   * @see FactoryBeanNotInitializedException
   * @since 4.0
   */
  @Nullable
  T getObject() throws Exception;

  /**
   * /**
   * Is the object managed by this factory a singleton? That is,
   * will {@link #getObject()} always return the same object
   * (a reference that can be cached)?
   * <p><b>NOTE:</b> If a FactoryBean indicates to hold a singleton object,
   * the object returned from {@code getObject()} might get cached
   * by the owning BeanFactory. Hence, do not return {@code true}
   * unless the FactoryBean always exposes the same reference.
   * <p>The singleton status of the FactoryBean itself will generally
   * be provided by the owning BeanFactory; usually, it has to be
   * defined as singleton there.
   * <p><b>NOTE:</b> This method returning {@code false} does not
   * necessarily indicate that returned objects are independent instances.
   * An implementation of the extended {@link SmartFactoryBean} interface
   * may explicitly indicate independent instances through its
   * {@link SmartFactoryBean#isPrototype()} method. Plain {@link FactoryBean}
   * implementations which do not implement this extended interface are
   * simply assumed to always return independent instances if the
   * {@code isSingleton()} implementation returns {@code false}.
   * <p>The default implementation returns {@code true}, since a
   * {@code FactoryBean} typically manages a singleton instance.
   *
   * @return whether the exposed object is a singleton
   * @see #getObject()
   * @see SmartFactoryBean#isPrototype()
   * @since 2.1.2
   */
  @Nullable
  Class<?> getObjectType();

  default boolean isSingleton() {
    return true;
  }

}
