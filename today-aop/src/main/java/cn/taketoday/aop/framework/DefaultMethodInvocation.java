/*
 * Copyright 2017 - 2024 the original author or authors.
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
 * along with this program. If not, see [https://www.gnu.org/licenses/]
 */

package cn.taketoday.aop.framework;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import cn.taketoday.aop.support.AopUtils;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.ClassUtils;

import static cn.taketoday.aop.InterceptorChainFactory.EMPTY_INTERCEPTOR;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 2018-11-10 13:14
 */
public class DefaultMethodInvocation extends AbstractMethodInvocation implements MethodInvocation {

  private final Object proxy;

  protected Object[] args;

  @Nullable
  protected final Object target;

  protected final Method method;

  @Nullable
  protected final Class<?> targetClass;

  protected final MethodInterceptor[] advices;

  /**
   * a flag show that current index of advice
   */
  private int currentAdviceIndex = 0;

  private final int adviceLength;

  public DefaultMethodInvocation(Object proxy, Method method, Class<?> targetClass, Object[] arguments) {
    this(proxy, null, method, targetClass, arguments, EMPTY_INTERCEPTOR);
  }

  public DefaultMethodInvocation(Object proxy, @Nullable Object target,
          Method method, @Nullable Class<?> targetClass,
          Object[] arguments, MethodInterceptor[] advices) {
    this.proxy = proxy;
    this.target = target;
    this.method = method;
    this.targetClass = targetClass;
    this.args = ClassUtils.adaptArgumentsIfNecessary(method, arguments);
    this.advices = advices;
    if (advices != null)
      this.adviceLength = advices.length;
    else {
      this.adviceLength = 0;
    }
  }

  @Override
  public Method getMethod() {
    return method;
  }

  @Override
  public Object[] getArguments() {
    return args;
  }

  @Override
  public void setArguments(Object[] arguments) {
    args = arguments;
  }

  @Override
  public Object getProxy() {
    return proxy;
  }

  @Override
  protected Object invokeJoinPoint() throws Throwable {
    return AopUtils.invokeJoinpointUsingReflection(target, method, args);
  }

  @Override
  protected boolean hasInterceptor() {
    return currentAdviceIndex < adviceLength;
  }

  @Override
  protected Object executeInterceptor() throws Throwable {
    return advices[currentAdviceIndex++].invoke(this);
  }

  @Override
  public Object getThis() {
    return target;
  }

  @Override
  public Class<?> getTargetClass() {
    return targetClass;
  }

  @Override
  public AccessibleObject getStaticPart() {
    return method;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof final DefaultMethodInvocation that))
      return false;
    if (!super.equals(o))
      return false;
    return currentAdviceIndex == that.currentAdviceIndex
            && adviceLength == that.adviceLength
            && Arrays.equals(args, that.args)
            && Objects.equals(target, that.target)
            && Objects.equals(method, that.method)
            && Arrays.equals(advices, that.advices);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(super.hashCode(), target, method);
    result = 31 * result + Arrays.hashCode(args);
    result = 31 * result + Arrays.hashCode(advices);
    return result;
  }

  @Override
  public String toString() {
    // Don't do toString on target, it may be proxied.
    StringBuilder sb = new StringBuilder("DefaultMethodInvocation: ");
    sb.append(this.method).append("; ");
    if (this.target == null) {
      sb.append("target is null");
    }
    else {
      sb.append("target is of class [").append(this.target.getClass().getName()).append(']');
    }
    return sb.toString();
  }

}
