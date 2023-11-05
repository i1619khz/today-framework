/*
 * Copyright 2017 - 2023 the original author or authors.
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

package cn.taketoday.test.context.junit.jupiter.web;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cn.taketoday.context.ApplicationContextInitializer;
import cn.taketoday.core.annotation.AliasFor;
import cn.taketoday.test.context.ContextConfiguration;
import cn.taketoday.test.context.ContextLoader;
import cn.taketoday.test.context.junit.jupiter.InfraExtension;
import cn.taketoday.test.context.junit.jupiter.JUnitConfig;
import cn.taketoday.test.context.web.WebAppConfiguration;

/**
 * {@code @ApplicationJUnitWebConfig} is a <em>composed annotation</em> that combines
 * {@link ExtendWith @ExtendWith(ApplicationExtension.class)} from JUnit Jupiter with
 * {@link ContextConfiguration @ContextConfiguration} and
 * {@link WebAppConfiguration @WebAppConfiguration} from the <em>TestContext Framework</em>.
 *
 * @author Sam Brannen
 * @see ExtendWith
 * @see InfraExtension
 * @see ContextConfiguration
 * @see WebAppConfiguration
 * @see JUnitConfig
 * @since 4.0
 */
@ExtendWith(InfraExtension.class)
@ContextConfiguration
@WebAppConfiguration
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JUnitWebConfig {

  /**
   * Alias for {@link ContextConfiguration#classes}.
   */
  @AliasFor(annotation = ContextConfiguration.class, attribute = "classes")
  Class<?>[] value() default {};

  /**
   * Alias for {@link ContextConfiguration#classes}.
   */
  @AliasFor(annotation = ContextConfiguration.class)
  Class<?>[] classes() default {};

  /**
   * Alias for {@link ContextConfiguration#locations}.
   */
  @AliasFor(annotation = ContextConfiguration.class)
  String[] locations() default {};

  /**
   * Alias for {@link ContextConfiguration#initializers}.
   */
  @AliasFor(annotation = ContextConfiguration.class)
  Class<? extends ApplicationContextInitializer>[] initializers() default {};

  /**
   * Alias for {@link ContextConfiguration#inheritLocations}.
   */
  @AliasFor(annotation = ContextConfiguration.class)
  boolean inheritLocations() default true;

  /**
   * Alias for {@link ContextConfiguration#inheritInitializers}.
   */
  @AliasFor(annotation = ContextConfiguration.class)
  boolean inheritInitializers() default true;

  /**
   * Alias for {@link ContextConfiguration#loader}.
   */
  @AliasFor(annotation = ContextConfiguration.class)
  Class<? extends ContextLoader> loader() default ContextLoader.class;

  /**
   * Alias for {@link ContextConfiguration#name}.
   */
  @AliasFor(annotation = ContextConfiguration.class)
  String name() default "";

  /**
   * Alias for {@link WebAppConfiguration#value}.
   */
  @AliasFor(annotation = WebAppConfiguration.class, attribute = "value")
  String resourcePath() default "src/main/webapp";

}
