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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.taketoday.context.properties;

import cn.taketoday.beans.factory.BeanDefinitionRegistry;
import cn.taketoday.beans.factory.BeanFactory;
import cn.taketoday.beans.factory.HierarchicalBeanFactory;
import cn.taketoday.beans.factory.support.BeanDefinition;
import cn.taketoday.context.properties.ConfigurationPropertiesBean.BindMethod;
import cn.taketoday.core.annotation.MergedAnnotation;
import cn.taketoday.core.annotation.MergedAnnotations;
import cn.taketoday.core.annotation.MergedAnnotations.SearchStrategy;
import cn.taketoday.lang.Assert;
import cn.taketoday.util.StringUtils;

/**
 * Delegate used by {@link EnableConfigurationPropertiesRegistrar} and
 * {@link ConfigurationPropertiesScanRegistrar} to register a bean definition for a
 * {@link ConfigurationProperties @ConfigurationProperties} class.
 *
 * @author Madhura Bhave
 * @author Phillip Webb
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
final class ConfigurationPropertiesBeanRegistrar {

  private final BeanDefinitionRegistry registry;

  private final BeanFactory beanFactory;

  ConfigurationPropertiesBeanRegistrar(BeanDefinitionRegistry registry) {
    this.registry = registry;
    this.beanFactory = (BeanFactory) this.registry;
  }

  void register(Class<?> type) {
    MergedAnnotation<ConfigurationProperties> annotation = MergedAnnotations
            .from(type, SearchStrategy.TYPE_HIERARCHY).get(ConfigurationProperties.class);
    register(type, annotation);
  }

  void register(Class<?> type, MergedAnnotation<ConfigurationProperties> annotation) {
    String name = getName(type, annotation);
    if (!containsBeanDefinition(name)) {
      registerBeanDefinition(name, type, annotation);
    }
  }

  private String getName(Class<?> type, MergedAnnotation<ConfigurationProperties> annotation) {
    String prefix = annotation.isPresent() ? annotation.getString("prefix") : "";
    return StringUtils.hasText(prefix) ? prefix + "-" + type.getName() : type.getName();
  }

  private boolean containsBeanDefinition(String name) {
    return containsBeanDefinition(this.beanFactory, name);
  }

  private boolean containsBeanDefinition(BeanFactory beanFactory, String name) {
    if (beanFactory.containsBeanDefinition(name)) {
      return true;
    }
    if (beanFactory instanceof HierarchicalBeanFactory hierarchicalBeanFactory) {
      return containsBeanDefinition(hierarchicalBeanFactory.getParentBeanFactory(), name);
    }
    return false;
  }

  private void registerBeanDefinition(
          String beanName, Class<?> type, MergedAnnotation<ConfigurationProperties> annotation) {
    Assert.state(annotation.isPresent(), () -> "No " + ConfigurationProperties.class.getSimpleName()
            + " annotation found on  '" + type.getName() + "'.");
    this.registry.registerBeanDefinition(beanName, createBeanDefinition(beanName, type));
  }

  private BeanDefinition createBeanDefinition(String beanName, Class<?> type) {
    BindMethod bindMethod = BindMethod.forType(type);
    BeanDefinition definition = new BeanDefinition(type);
    definition.setAttribute(BindMethod.class.getName(), bindMethod);
    if (bindMethod == BindMethod.VALUE_OBJECT) {
      definition.setInstanceSupplier(() -> createValueObject(beanName, type));
    }
    return definition;
  }

  private Object createValueObject(String beanName, Class<?> beanType) {
    ConfigurationPropertiesBean bean = ConfigurationPropertiesBean.forValueObject(beanType, beanName);
    ConfigurationPropertiesBinder binder = ConfigurationPropertiesBinder.get(this.beanFactory);
    try {
      return binder.bindOrCreate(bean);
    }
    catch (Exception ex) {
      throw new ConfigurationPropertiesBindException(bean, ex);
    }
  }

}
