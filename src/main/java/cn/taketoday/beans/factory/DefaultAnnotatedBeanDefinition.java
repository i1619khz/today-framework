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
package cn.taketoday.beans.factory;

import cn.taketoday.core.type.AnnotationMetadata;
import cn.taketoday.core.type.StandardAnnotationMetadata;
import cn.taketoday.lang.Assert;

/**
 * Extension of the {@link DefaultBeanDefinition} class, adding support
 * for annotation metadata exposed through the {@link AnnotatedBeanDefinition} interface.
 *
 * <p>This DefaultBeanDefinition variant is mainly useful for testing code that expects
 * to operate on an AnnotatedBeanDefinition
 *
 * @author TODAY 2021/10/25 17:37
 * @since 4.0
 */
public class DefaultAnnotatedBeanDefinition extends DefaultBeanDefinition implements AnnotatedBeanDefinition {

  private final AnnotationMetadata metadata;

  /**
   * Create a new AnnotatedGenericBeanDefinition for the given bean class.
   *
   * @param beanClass the loaded bean class
   */
  public DefaultAnnotatedBeanDefinition(Class<?> beanClass) {
    setBeanClass(beanClass);
    this.metadata = AnnotationMetadata.introspect(beanClass);
  }

  /**
   * Create a new AnnotatedGenericBeanDefinition for the given annotation metadata,
   * allowing for ASM-based processing and avoidance of early loading of the bean class.
   *
   * @param metadata the annotation metadata for the bean class in question
   */
  public DefaultAnnotatedBeanDefinition(AnnotationMetadata metadata) {
    Assert.notNull(metadata, "AnnotationMetadata must not be null");
    if (metadata instanceof StandardAnnotationMetadata) {
      setBeanClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass());
    }
    else {
      setBeanClassName(metadata.getClassName());
    }
    this.metadata = metadata;
  }

  @Override
  public final AnnotationMetadata getMetadata() {
    return this.metadata;
  }

}
