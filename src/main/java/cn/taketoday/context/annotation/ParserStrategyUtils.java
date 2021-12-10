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

package cn.taketoday.context.annotation;

import java.lang.reflect.Constructor;

import cn.taketoday.beans.factory.Aware;
import cn.taketoday.beans.factory.BeanClassLoaderAware;
import cn.taketoday.beans.factory.BeanDefinitionRegistry;
import cn.taketoday.beans.factory.BeanFactory;
import cn.taketoday.beans.factory.BeanFactoryAware;
import cn.taketoday.beans.factory.BeanInstantiationException;
import cn.taketoday.beans.factory.ConfigurableBeanFactory;
import cn.taketoday.beans.support.BeanUtils;
import cn.taketoday.context.aware.EnvironmentAware;
import cn.taketoday.context.aware.ResourceLoaderAware;
import cn.taketoday.context.loader.DefinitionLoadingContext;
import cn.taketoday.core.env.Environment;
import cn.taketoday.core.io.PatternResourceLoader;
import cn.taketoday.core.io.ResourceLoader;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;

/**
 * Common delegate code for the handling of parser strategies, e.g.
 * {@code TypeFilter}, {@code ImportSelector}, {@code ImportBeanDefinitionRegistrar}
 *
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 4.0
 */
abstract class ParserStrategyUtils {

  /**
   * Instantiate a class using an appropriate constructor and return the new
   * instance as the specified assignable type. The returned instance will
   * have {@link BeanClassLoaderAware}, {@link BeanFactoryAware},
   * {@link EnvironmentAware}, and {@link ResourceLoaderAware} contracts
   * invoked if they are implemented by the given object.
   */
  @SuppressWarnings("unchecked")
  static <T> T instantiateClass(
          Class<?> clazz, Class<T> assignableTo, DefinitionLoadingContext loadingContext) {

    Assert.notNull(clazz, "Class must not be null");
    Assert.isAssignable(assignableTo, clazz);
    if (clazz.isInterface()) {
      throw new BeanInstantiationException(clazz, "Specified class is an interface");
    }
    BeanDefinitionRegistry registry = loadingContext.getRegistry();
    PatternResourceLoader resourceLoader = loadingContext.getResourceLoader();
    Environment environment = loadingContext.getEnvironment();
    ClassLoader classLoader = registry instanceof ConfigurableBeanFactory
                              ? ((ConfigurableBeanFactory) registry).getBeanClassLoader()
                              : resourceLoader.getClassLoader();
    T instance = (T) createInstance(clazz, environment, resourceLoader, registry, classLoader);
    ParserStrategyUtils.invokeAwareMethods(instance, environment, resourceLoader, registry, classLoader);
    return instance;
  }

  private static Object createInstance(
          Class<?> clazz, Environment environment,
          ResourceLoader resourceLoader, BeanDefinitionRegistry registry,
          @Nullable ClassLoader classLoader) {

    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    if (constructors.length == 1 && constructors[0].getParameterCount() > 0) {
      try {
        Constructor<?> constructor = constructors[0];
        Object[] args = resolveArgs(constructor.getParameterTypes(),
                environment, resourceLoader, registry, classLoader);
        return BeanUtils.newInstance(constructor, args);
      }
      catch (Exception ex) {
        throw new BeanInstantiationException(clazz, "No suitable constructor found", ex);
      }
    }
    return BeanUtils.newInstance(clazz);
  }

  private static Object[] resolveArgs(
          Class<?>[] parameterTypes,
          Environment environment, ResourceLoader resourceLoader,
          BeanDefinitionRegistry registry, @Nullable ClassLoader classLoader) {

    Object[] parameters = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameters[i] = resolveParameter(parameterTypes[i], environment,
              resourceLoader, registry, classLoader);
    }
    return parameters;
  }

  @Nullable
  private static Object resolveParameter(
          Class<?> parameterType,
          Environment environment, ResourceLoader resourceLoader,
          BeanDefinitionRegistry registry, @Nullable ClassLoader classLoader) {

    if (parameterType == Environment.class) {
      return environment;
    }
    if (parameterType == ResourceLoader.class) {
      return resourceLoader;
    }
    if (parameterType == BeanFactory.class) {
      return (registry instanceof BeanFactory ? registry : null);
    }
    if (parameterType == ClassLoader.class) {
      return classLoader;
    }
    throw new IllegalStateException("Illegal method parameter type: " + parameterType.getName());
  }

  private static void invokeAwareMethods(
          Object parserStrategyBean, Environment environment,
          ResourceLoader resourceLoader, BeanDefinitionRegistry registry, @Nullable ClassLoader classLoader) {

    if (parserStrategyBean instanceof Aware) {
      if (parserStrategyBean instanceof BeanClassLoaderAware && classLoader != null) {
        ((BeanClassLoaderAware) parserStrategyBean).setBeanClassLoader(classLoader);
      }
      if (parserStrategyBean instanceof BeanFactoryAware && registry instanceof BeanFactory) {
        ((BeanFactoryAware) parserStrategyBean).setBeanFactory((BeanFactory) registry);
      }
      if (parserStrategyBean instanceof EnvironmentAware) {
        ((EnvironmentAware) parserStrategyBean).setEnvironment(environment);
      }
      if (parserStrategyBean instanceof ResourceLoaderAware) {
        ((ResourceLoaderAware) parserStrategyBean).setResourceLoader(resourceLoader);
      }
    }
  }

}
