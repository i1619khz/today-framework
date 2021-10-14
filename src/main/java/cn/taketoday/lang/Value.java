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
package cn.taketoday.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used at the field or method/constructor parameter level
 * that indicates a default value expression for the annotated element.
 *
 * <p>Typically used for expression-driven or property-driven dependency injection.
 * Also supported for dynamic resolution of handler method arguments &mdash; for
 * example, in Web MVC.
 *
 * <p>A common use case is to inject values using
 * <code>#{systemProperties.myProp}</code> style EL (Java Unified Expression Language)
 * expressions. Alternatively, values may be injected using
 * <code>${my.app.myProp}</code> style property placeholders.
 *
 * <p>Note that actual processing of the {@code @Value} annotation is performed
 * by a {@link cn.taketoday.context.loader.ValuePropertyResolver ValuePropertyResolver}
 * which in turn means that you <em>cannot</em> use {@code @Value} within
 * {@link cn.taketoday.context.loader.ValuePropertyResolver ValuePropertyResolver} or
 * {@link cn.taketoday.context.loader.PropertyValueResolver PropertyValueResolver}
 * types.
 *
 * @author TODAY 2018-08-04 15:57
 * @see Autowired
 * @see Env
 * @see cn.taketoday.context.expression.ExpressionEvaluator
 * @see cn.taketoday.context.loader.ValuePropertyResolver
 * @see cn.taketoday.context.loader.PropertyValueResolver
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
public @interface Value {

  /**
   * The actual value expression such as <code>#{systemProperties.myProp}</code>
   * or property placeholder such as <code>${my.app.myProp}</code>.
   * <p>
   * If value is empty string when declared on Field, use
   * <pre>
   *     PropertyPlaceholderHandler.PLACEHOLDER_PREFIX +
   *               field.getDeclaringClass().getName() +
   *               Constant.PACKAGE_SEPARATOR +
   *               field.getName() +
   *               PropertyPlaceholderHandler.PLACEHOLDER_SUFFIX
   *  </pre>
   * class full name dot property name as property-key to find in
   * application environment
   * </p>
   *
   * @see java.lang.reflect.Field
   */
  String value() default Constant.BLANK;

  /**
   * Is required ?
   *
   * @see Required
   * @see cn.taketoday.context.expression.ExpressionEvaluationException
   */
  boolean required() default true;

  /**
   * default value expression or fallback expression
   */
  String defaultValue() default Constant.BLANK;

}
