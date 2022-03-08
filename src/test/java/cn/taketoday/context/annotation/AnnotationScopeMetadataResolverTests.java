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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import cn.taketoday.beans.factory.annotation.AnnotatedBeanDefinition;
import cn.taketoday.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import cn.taketoday.beans.factory.config.BeanDefinition;
import cn.taketoday.context.loader.ScopeMetadata;
import cn.taketoday.core.type.classreading.MetadataReader;
import cn.taketoday.core.type.classreading.MetadataReaderFactory;
import cn.taketoday.core.type.classreading.SimpleMetadataReaderFactory;

import static cn.taketoday.context.annotation.ScopedProxyMode.INTERFACES;
import static cn.taketoday.context.annotation.ScopedProxyMode.NO;
import static cn.taketoday.context.annotation.ScopedProxyMode.TARGET_CLASS;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link AnnotationScopeMetadataResolver}.
 *
 * @author Rick Evans
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Sam Brannen
 */
public class AnnotationScopeMetadataResolverTests {

  private AnnotationScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

  @Test
  public void resolveScopeMetadataShouldNotApplyScopedProxyModeToSingleton() {
    AnnotatedBeanDefinition bd = new AnnotatedGenericBeanDefinition(AnnotatedWithSingletonScope.class);
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(bd);
    Assertions.assertThat(scopeMetadata).as("resolveScopeMetadata(..) must *never* return null.").isNotNull();
    Assertions.assertThat(scopeMetadata.getScopeName()).isEqualTo(BeanDefinition.SCOPE_SINGLETON);
    Assertions.assertThat(scopeMetadata.getScopedProxyMode()).isEqualTo(NO);
  }

  @Test
  public void resolveScopeMetadataShouldApplyScopedProxyModeToPrototype() {
    this.scopeMetadataResolver = new AnnotationScopeMetadataResolver(INTERFACES);
    AnnotatedBeanDefinition bd = new AnnotatedGenericBeanDefinition(AnnotatedWithPrototypeScope.class);
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(bd);
    Assertions.assertThat(scopeMetadata).as("resolveScopeMetadata(..) must *never* return null.").isNotNull();
    Assertions.assertThat(scopeMetadata.getScopeName()).isEqualTo(BeanDefinition.SCOPE_PROTOTYPE);
    Assertions.assertThat(scopeMetadata.getScopedProxyMode()).isEqualTo(INTERFACES);
  }

  @Test
  public void resolveScopeMetadataShouldReadScopedProxyModeFromAnnotation() {
    AnnotatedBeanDefinition bd = new AnnotatedGenericBeanDefinition(AnnotatedWithScopedProxy.class);
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(bd);
    Assertions.assertThat(scopeMetadata).as("resolveScopeMetadata(..) must *never* return null.").isNotNull();
    Assertions.assertThat(scopeMetadata.getScopeName()).isEqualTo("request");
    Assertions.assertThat(scopeMetadata.getScopedProxyMode()).isEqualTo(TARGET_CLASS);
  }

  @Test
  public void customRequestScope() {
    AnnotatedBeanDefinition bd = new AnnotatedGenericBeanDefinition(AnnotatedWithCustomRequestScope.class);
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(bd);
    Assertions.assertThat(scopeMetadata).as("resolveScopeMetadata(..) must *never* return null.").isNotNull();
    Assertions.assertThat(scopeMetadata.getScopeName()).isEqualTo("request");
    Assertions.assertThat(scopeMetadata.getScopedProxyMode()).isEqualTo(NO);
  }

  @Test
  public void customRequestScopeViaAsm() throws IOException {
    MetadataReaderFactory readerFactory = new SimpleMetadataReaderFactory();
    MetadataReader reader = readerFactory.getMetadataReader(AnnotatedWithCustomRequestScope.class.getName());
    AnnotatedBeanDefinition bd = new AnnotatedGenericBeanDefinition(reader.getAnnotationMetadata());
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(bd);
    Assertions.assertThat(scopeMetadata).as("resolveScopeMetadata(..) must *never* return null.").isNotNull();
    Assertions.assertThat(scopeMetadata.getScopeName()).isEqualTo("request");
    Assertions.assertThat(scopeMetadata.getScopedProxyMode()).isEqualTo(NO);
  }

  @Test
  public void customRequestScopeWithAttribute() {
    AnnotatedBeanDefinition bd = new AnnotatedGenericBeanDefinition(
            AnnotatedWithCustomRequestScopeWithAttributeOverride.class);
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(bd);
    Assertions.assertThat(scopeMetadata).as("resolveScopeMetadata(..) must *never* return null.").isNotNull();
    Assertions.assertThat(scopeMetadata.getScopeName()).isEqualTo("request");
    Assertions.assertThat(scopeMetadata.getScopedProxyMode()).isEqualTo(TARGET_CLASS);
  }

  @Test
  public void customRequestScopeWithAttributeViaAsm() throws IOException {
    MetadataReaderFactory readerFactory = new SimpleMetadataReaderFactory();
    MetadataReader reader = readerFactory.getMetadataReader(AnnotatedWithCustomRequestScopeWithAttributeOverride.class.getName());
    AnnotatedBeanDefinition bd = new AnnotatedGenericBeanDefinition(reader.getAnnotationMetadata());
    ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(bd);
    Assertions.assertThat(scopeMetadata).as("resolveScopeMetadata(..) must *never* return null.").isNotNull();
    Assertions.assertThat(scopeMetadata.getScopeName()).isEqualTo("request");
    Assertions.assertThat(scopeMetadata.getScopedProxyMode()).isEqualTo(TARGET_CLASS);
  }

  @Test
  public void ctorWithNullScopedProxyMode() {
    assertThatIllegalArgumentException().isThrownBy(() ->
            new AnnotationScopeMetadataResolver(null));
  }

  @Test
  public void setScopeAnnotationTypeWithNullType() {
    assertThatIllegalArgumentException().isThrownBy(() ->
            scopeMetadataResolver.setScopeAnnotationType(null));
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Scope("request")
  @interface CustomRequestScope {
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Scope("request")
  @interface CustomRequestScopeWithAttributeOverride {

    ScopedProxyMode proxyMode();
  }

  @Scope("singleton")
  private static class AnnotatedWithSingletonScope {
  }

  @Scope("prototype")
  private static class AnnotatedWithPrototypeScope {
  }

  @Scope(scopeName = "request", proxyMode = TARGET_CLASS)
  private static class AnnotatedWithScopedProxy {
  }

  @CustomRequestScope
  private static class AnnotatedWithCustomRequestScope {
  }

  @CustomRequestScopeWithAttributeOverride(proxyMode = TARGET_CLASS)
  private static class AnnotatedWithCustomRequestScopeWithAttributeOverride {
  }

}
