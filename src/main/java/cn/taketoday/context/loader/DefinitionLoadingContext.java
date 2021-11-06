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

package cn.taketoday.context.loader;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Set;

import cn.taketoday.beans.factory.BeanDefinition;
import cn.taketoday.beans.factory.BeanDefinitionBuilder;
import cn.taketoday.beans.factory.BeanDefinitionRegistry;
import cn.taketoday.beans.support.BeanFactoryAwareBeanInstantiator;
import cn.taketoday.context.ApplicationContext;
import cn.taketoday.context.annotation.AnnotationScopeMetadataResolver;
import cn.taketoday.context.event.ApplicationListener;
import cn.taketoday.context.expression.ExpressionEvaluator;
import cn.taketoday.core.io.PathMatchingPatternResourceLoader;
import cn.taketoday.core.io.PatternResourceLoader;
import cn.taketoday.core.io.Resource;
import cn.taketoday.core.io.ResourceLoader;
import cn.taketoday.core.type.AnnotatedTypeMetadata;
import cn.taketoday.core.type.AnnotationMetadata;
import cn.taketoday.core.type.MethodMetadata;
import cn.taketoday.core.type.classreading.CachingMetadataReaderFactory;
import cn.taketoday.core.type.classreading.MetadataReader;
import cn.taketoday.core.type.classreading.MetadataReaderFactory;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.NonNull;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.ClassUtils;
import cn.taketoday.util.ExceptionUtils;

/**
 * @author TODAY 2021/10/19 22:22
 * @since 4.0
 */
public class DefinitionLoadingContext {

  private final BeanDefinitionRegistry registry;
  private final ApplicationContext applicationContext;
  private final MissingBeanRegistry missingBeanRegistry;

  private ConditionEvaluator conditionEvaluator;
  private BeanFactoryAwareBeanInstantiator instantiator;
  private MetadataReaderFactory metadataReaderFactory;

  @Nullable
  private PatternResourceLoader resourceLoader;

  private ScopeMetadataResolver scopeMetadataResolver;

  public DefinitionLoadingContext(BeanDefinitionRegistry registry, @NonNull ApplicationContext context) {
    this.registry = registry;
    this.resourceLoader = context;
    this.applicationContext = context;
    this.missingBeanRegistry = new MissingBeanRegistry(this);
  }

  public DefinitionLoadingContext(
          BeanDefinitionRegistry registry, @Nullable ConditionEvaluator conditionEvaluator, @NonNull ApplicationContext context) {
    this.registry = registry;
    this.resourceLoader = context;
    this.applicationContext = context;
    this.conditionEvaluator = conditionEvaluator;
    this.missingBeanRegistry = new MissingBeanRegistry(this);
  }

  public BeanDefinitionRegistry getRegistry() {
    return registry;
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  public MissingBeanRegistry getMissingBeanRegistry() {
    return missingBeanRegistry;
  }

  /**
   * unwrap bean-factory to {@code requiredType}
   *
   * @throws IllegalArgumentException not a requiredType
   */
  @NonNull
  public <T> T unwrapFactory(Class<T> requiredType) {
    return applicationContext.unwrapFactory(requiredType);
  }

  /**
   * unwrap this ApplicationContext to {@code requiredType}
   *
   * @throws IllegalArgumentException not a requiredType
   */
  @NonNull
  public <T> T unwrapContext(Class<T> requiredType) {
    return applicationContext.unwrap(requiredType);
  }

  /**
   * default is use {@link ClassUtils#getShortName(Class)}
   *
   * <p>
   * sub-classes can overriding this method to provide a strategy to create bean name
   * </p>
   *
   * @param type type
   * @return bean name
   * @see ClassUtils#getShortName(Class)
   */
  public String createBeanName(Class<?> type) {
    return BeanDefinitionBuilder.defaultBeanName(type);
  }

  public String createBeanName(String clazzName) {
    return BeanDefinitionBuilder.defaultBeanName(clazzName);
  }

  @NonNull
  public ConditionEvaluator getConditionEvaluator() {
    if (conditionEvaluator == null) {
      this.conditionEvaluator = new ConditionEvaluator(applicationContext, registry);
    }
    return conditionEvaluator;
  }


  public void registerBeanDefinition(BeanDefinition definition) {
    definition.setScope(resolveScopeName(definition));
    registry.registerBeanDefinition(definition);
  }

  public boolean containsBeanDefinition(Class<?> beanClass) {
    return registry.containsBeanDefinition(beanClass);
  }

  public boolean containsBeanDefinition(Class<?> type, boolean equals) {
    return registry.containsBeanDefinition(type, equals);
  }

  public boolean containsBeanDefinition(String beanName) {
    return registry.containsBeanDefinition(beanName);
  }

  public boolean passCondition(AnnotatedElement annotated) {
    if (annotated instanceof Class) {
      return getConditionEvaluator().passCondition((Class<?>) annotated);
    }
    else if (annotated instanceof Method) {
      return getConditionEvaluator().passCondition((Method) annotated);
    }
    else {
      throw new IllegalArgumentException("AnnotatedElement must be Method or Class");
    }
  }

  public boolean passCondition(AnnotatedTypeMetadata metadata) {
    return getConditionEvaluator().passCondition(metadata);
  }

  public void addApplicationListener(ApplicationListener<?> importer) {
    applicationContext.addApplicationListener(importer);
  }

  public Object getBean(BeanDefinition def) {
    return applicationContext.getBean(def);
  }

  //---------------------------------------------------------------------
  // BeanFactoryAwareBeanInstantiator
  //---------------------------------------------------------------------

  private BeanFactoryAwareBeanInstantiator instantiator() {
    if (instantiator == null) {
      this.instantiator = new BeanFactoryAwareBeanInstantiator(applicationContext);
    }
    return instantiator;
  }

  public <T> T instantiate(Class<T> beanClass) {
    return instantiator().instantiate(beanClass);
  }

  //---------------------------------------------------------------------
  // detectMissingBean
  //---------------------------------------------------------------------

  public void detectMissingBean(MethodMetadata method, BeanDefinition config) {
    missingBeanRegistry.detectMissingBean(method, config);
  }

  public void detectMissingBean(MetadataReader metadataReader) {
    missingBeanRegistry.detectMissingBean(metadataReader);
  }

  //---------------------------------------------------------------------
  // MetadataReaderFactory
  //---------------------------------------------------------------------

  /**
   * Set the {@link ResourceLoader} to use for resource locations.
   * This will typically be a {@link PatternResourceLoader} implementation.
   * <p>Default is a {@code PathMatchingResourcePatternResolver}, also capable of
   * resource pattern resolving through the {@code ResourcePatternResolver} interface.
   *
   * @see PatternResourceLoader
   * @see PathMatchingPatternResourceLoader
   */
  public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
    this.resourceLoader = PatternResourceLoader.fromResourceLoader(resourceLoader);
    this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
  }

  public PatternResourceLoader getResourceLoader() {
    if (this.resourceLoader == null) {
      this.resourceLoader = applicationContext;
      // try to bind ResourceLoader to MetadataReaderFactory
      if (this.metadataReaderFactory == null) {
        this.metadataReaderFactory = new CachingMetadataReaderFactory(applicationContext);
      }
    }
    return this.resourceLoader;
  }

  /**
   * Set the {@link MetadataReaderFactory} to use.
   * <p>Default is a {@link CachingMetadataReaderFactory} for the specified
   * {@linkplain #setResourceLoader resource loader}.
   * <p>Call this setter method <i>after</i> {@link #setResourceLoader} in order
   * for the given MetadataReaderFactory to override the default factory.
   */
  public void setMetadataReaderFactory(@Nullable MetadataReaderFactory metadataReaderFactory) {
    this.metadataReaderFactory = metadataReaderFactory;
  }

  /**
   * Return the MetadataReaderFactory used by this component provider.
   */
  public final MetadataReaderFactory getMetadataReaderFactory() {
    if (this.metadataReaderFactory == null) {
      // try to bind ResourceLoader to MetadataReaderFactory
      if (this.resourceLoader == null) {
        this.metadataReaderFactory = new CachingMetadataReaderFactory(applicationContext);
        this.resourceLoader = applicationContext;
      }
      else {
        ResourceLoader resourceLoader;
        if (this.resourceLoader instanceof PathMatchingPatternResourceLoader) {
          resourceLoader = ((PathMatchingPatternResourceLoader) this.resourceLoader).getRootLoader();
        }
        else {
          resourceLoader = this.resourceLoader;
        }
        this.metadataReaderFactory = new CachingMetadataReaderFactory(resourceLoader);
      }
    }
    return this.metadataReaderFactory;
  }

  @NonNull
  public AnnotationMetadata getAnnotationMetadata(String className) {
    return getMetadataReader(className).getAnnotationMetadata();
  }

  @NonNull
  public MetadataReader getMetadataReader(String className) {
    try {
      MetadataReaderFactory metadataFactory = getMetadataReaderFactory();
      return metadataFactory.getMetadataReader(className);
    }
    catch (IOException e) {
      throw ExceptionUtils.sneakyThrow(e);
    }
  }

  /**
   * Clear the local metadata cache, if any, removing all cached class metadata.
   */
  public void clearCache() {
    if (metadataReaderFactory instanceof CachingMetadataReaderFactory) {
      // Clear cache in externally provided MetadataReaderFactory; this is a no-op
      // for a shared cache since it'll be cleared by the ApplicationContext.
      ((CachingMetadataReaderFactory) metadataReaderFactory).clearCache();
    }
  }

  // ExpressionEvaluator

  public String evaluateExpression(String expression) {
    return evaluateExpression(expression, String.class);
  }

  public <T> T evaluateExpression(String expression, Class<T> requiredType) {
    ExpressionEvaluator expressionEvaluator = applicationContext.getExpressionEvaluator();
    return expressionEvaluator.evaluate(expression, requiredType);
  }

  // PatternResourceLoader

  public Resource getResource(String location) {
    return getResourceLoader().getResource(location);
  }

  public Set<Resource> getResources(String locationPattern) throws IOException {
    return getResourceLoader().getResources(locationPattern);
  }

  // ScopeMetadataResolver

  public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
    Assert.notNull(scopeMetadataResolver, "ScopeMetadataResolver is required");
    this.scopeMetadataResolver = scopeMetadataResolver;
  }

  public ScopeMetadataResolver getScopeMetadataResolver() {
    if (scopeMetadataResolver == null) {
      scopeMetadataResolver = new AnnotationScopeMetadataResolver();
    }
    return scopeMetadataResolver;
  }

  public String resolveScopeName(BeanDefinition definition) {
    return getScopeMetadataResolver().resolveScopeMetadata(definition).getScopeName();
  }

}
