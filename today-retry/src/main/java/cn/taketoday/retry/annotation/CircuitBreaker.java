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

package cn.taketoday.retry.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a method invocation that is retryable.
 *
 * @author Dave Syer
 * @author Artem Bilan
 * @author Gary Russell
 * @since 4.0
 */
@Documented
@Retryable(stateful = true)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface CircuitBreaker {

  /**
   * Exception types that are retryable. Synonym for includes(). Defaults to empty (and
   * if excludes is also empty all exceptions are retried).
   *
   * @return exception types to retry
   */
  Class<? extends Throwable>[] value() default {};

  /**
   * Exception types that are retryable. Defaults to empty (and if excludes is also
   * empty all exceptions are retried).
   *
   * @return exception types to retry
   */
  Class<? extends Throwable>[] include() default {};

  /**
   * Exception types that are not retryable. Defaults to empty (and if includes is also
   * empty all exceptions are retried). If includes is empty but excludes is not, all
   * not excluded exceptions are retried
   *
   * @return exception types not to retry
   */
  Class<? extends Throwable>[] exclude() default {};

  /**
   * @return the maximum number of attempts (including the first failure), defaults to 3
   */
  int maxAttempts() default 3;

  /**
   * @return an expression evaluated to the maximum number of attempts (including the
   * first failure), defaults to 3 Overrides {@link #maxAttempts()}. Use {@code #{...}}
   * for one-time evaluation during initialization, omit the delimiters for evaluation
   * at runtime.
   */
  String maxAttemptsExpression() default "";

  /**
   * A unique label for the circuit for reporting and state management. Defaults to the
   * method signature where the annotation is declared.
   *
   * @return the label for the circuit
   */
  String label() default "";

  /**
   * If the circuit is open for longer than this timeout then it resets on the next call
   * to give the downstream component a chance to respond again.
   *
   * @return the timeout before an open circuit is reset in milliseconds, defaults to
   * 20000
   */
  long resetTimeout() default 20000;

  /**
   * If the circuit is open for longer than this timeout then it resets on the next call
   * to give the downstream component a chance to respond again. Overrides
   * {@link #resetTimeout()}. Use {@code #{...}} for one-time evaluation during
   * initialization, omit the delimiters for evaluation at runtime.
   *
   * @return the timeout before an open circuit is reset in milliseconds, no default.
   */
  String resetTimeoutExpression() default "";

  /**
   * When {@link #maxAttempts()} failures are reached within this timeout, the circuit
   * is opened automatically, preventing access to the downstream component.
   *
   * @return the timeout before an closed circuit is opened in milliseconds, defaults to
   * 5000
   */
  long openTimeout() default 5000;

  /**
   * When {@link #maxAttempts()} failures are reached within this timeout, the circuit
   * is opened automatically, preventing access to the downstream component. Overrides
   * {@link #openTimeout()}. Use {@code #{...}} for one-time evaluation during
   * initialization, omit the delimiters for evaluation at runtime.
   *
   * @return the timeout before an closed circuit is opened in milliseconds, no default.
   */
  String openTimeoutExpression() default "";

  /**
   * Specify an expression to be evaluated after the
   * {@code SimpleRetryPolicy.canRetry()} returns true - can be used to conditionally
   * suppress the retry. Only invoked after an exception is thrown. The root object for
   * the evaluation is the last {@code Throwable}. Other beans in the context can be
   * referenced. For example:
   *
   * <pre class=code>
   *  {@code "message.contains('you can retry this')"}.
   * </pre>
   *
   * and
   *
   * <pre class=code>
   *  {@code "@someBean.shouldRetry(#root)"}.
   * </pre>
   *
   * @return the expression.
   */
  String exceptionExpression() default "";

}
