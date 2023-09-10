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

package cn.taketoday.bytecode.proxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import cn.taketoday.bytecode.commons.MethodSignature;
import cn.taketoday.bytecode.core.AbstractClassGenerator;
import cn.taketoday.bytecode.core.CodeGenerationException;
import cn.taketoday.bytecode.core.GeneratorStrategy;
import cn.taketoday.bytecode.core.NamingPolicy;
import cn.taketoday.bytecode.reflect.MethodAccess;
import cn.taketoday.lang.Nullable;

/**
 * Classes generated by {@link Enhancer} pass this object to the registered
 * {@link MethodInterceptor} objects when an intercepted method is invoked. It
 * can be used to either invoke the original method, or call the same method on
 * a different object of the same type.
 *
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see 2019-10-21 23:49
 */
public class MethodProxy {

  @Nullable
  private MethodSignature sig1;

  @Nullable
  private MethodSignature sig2;

  private CreateInfo createInfo;

  private volatile FastClassInfo fastClassInfo;

  /**
   * For internal use by {@link Enhancer} only; see the {@link cn.taketoday.bytecode.reflect.MethodAccess} class
   * for similar functionality.
   */
  @SuppressWarnings({ "rawtypes" })
  public static MethodProxy create(Class c1, Class c2, String desc, String name1, String name2) {
    MethodProxy proxy = new MethodProxy();
    proxy.sig1 = new MethodSignature(name1, desc);
    proxy.sig2 = new MethodSignature(name2, desc);
    proxy.createInfo = new CreateInfo(c1, c2);

    if (!c1.isInterface() && c1 != Object.class && !Factory.class.isAssignableFrom(c2)) {
      // Try early initialization for overridden methods on specifically purposed subclasses
      try {
        proxy.init();
      }
      catch (CodeGenerationException ex) {
        // Ignore - to be retried when actually needed later on (possibly not at all)
      }
    }
    return proxy;
  }

  private FastClassInfo init() {
    /*
     * Using a volatile invariant allows us to initialize the FastClass and
     * method index pairs atomically.
     *
     * Double-checked locking is safe with volatile in Java 5.  Before 1.5 this
     * code could allow fastClassInfo to be instantiated more than once, which
     * appears to be benign.
     */
    FastClassInfo fastClassInfo = this.fastClassInfo;
    if (fastClassInfo == null) {
      synchronized(this) {
        fastClassInfo = this.fastClassInfo;
        if (fastClassInfo == null) {
          CreateInfo ci = createInfo;
          MethodAccess f1 = helper(ci, ci.c1);
          MethodAccess f2 = helper(ci, ci.c2);
          int i1 = f1.getIndex(sig1);
          int i2 = f2.getIndex(sig2);

          fastClassInfo = new FastClassInfo(f1, f2, i1, i2);
          this.fastClassInfo = fastClassInfo;

          this.createInfo = null;
          this.sig1 = null;
          this.sig2 = null;
        }
      }
    }
    return fastClassInfo;
  }

  private static class FastClassInfo {

    public final MethodAccess f1;
    public final MethodAccess f2;

    final int i1;
    final int i2;

    private FastClassInfo(MethodAccess f1, MethodAccess f2, int i1, int i2) {
      this.f1 = f1;
      this.f2 = f2;
      this.i1 = i1;
      this.i2 = i2;
    }
  }

  @SuppressWarnings({ "rawtypes" })
  private static class CreateInfo {

    final Class c1;

    final Class c2;

    NamingPolicy namingPolicy;

    GeneratorStrategy strategy;

    boolean attemptLoad;

    public CreateInfo(Class c1, Class c2) {
      this.c1 = c1;
      this.c2 = c2;
      AbstractClassGenerator fromEnhancer = AbstractClassGenerator.getCurrent();
      if (fromEnhancer != null) {
        namingPolicy = fromEnhancer.getNamingPolicy();
        strategy = fromEnhancer.getStrategy();
        attemptLoad = fromEnhancer.isAttemptLoad();
      }
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static MethodAccess helper(CreateInfo ci, Class type) {
    MethodAccess.Generator g = new MethodAccess.Generator(type);
    g.setNeighbor(type);
    g.setClassLoader(ci.c2.getClassLoader());
    g.setNamingPolicy(ci.namingPolicy);
    g.setStrategy(ci.strategy);
    g.setAttemptLoad(ci.attemptLoad);
    return g.create();
  }

  private MethodProxy() { }

  /**
   * Return the <code>MethodProxy</code> used when intercepting the method
   * matching the given signature.
   *
   * @param type the class generated by Enhancer
   * @param sig the signature to match
   * @return the MethodProxy instance, or null if no applicable matching method is found
   * @throws IllegalArgumentException if the Class was not created by Enhancer or does not use a MethodInterceptor
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static MethodProxy find(Class type, MethodSignature sig) {
    try {
      Method m = type.getDeclaredMethod(
              MethodInterceptorGenerator.FIND_PROXY_NAME, MethodSignature.class);
      return (MethodProxy) m.invoke(null, new Object[] { sig });
    }
    catch (NoSuchMethodException ex) {
      throw new IllegalArgumentException("Class " + type + " does not use a MethodInterceptor");
    }
    catch (IllegalAccessException | InvocationTargetException ex) {
      throw new CodeGenerationException(ex);
    }
  }

  /**
   * Invoke the original method, on a different object of the same type.
   *
   * @param obj the compatible object; recursion will result if you use the object passed as the first
   * argument to the MethodInterceptor (usually not what you want)
   * @param args the arguments passed to the intercepted method; you may substitute a different
   * argument array as long as the types are compatible
   * @throws Throwable the bare exceptions thrown by the called method are passed through
   * without wrapping in an <code>InvocationTargetException</code>
   * @see MethodInterceptor#intercept
   */
  public Object invoke(Object obj, Object[] args) throws Throwable {
    try {
      FastClassInfo fci = init();
      return fci.f1.invoke(fci.i1, obj, args);
    }
    catch (InvocationTargetException ex) {
      throw ex.getTargetException();
    }
    catch (IllegalArgumentException ex) {
      if (fastClassInfo.i1 < 0)
        throw new IllegalArgumentException("Protected method: " + sig1);
      throw ex;
    }
  }

  /**
   * Invoke the original (super) method on the specified object.
   *
   * @param obj the enhanced object, must be the object passed as the first
   * argument to the MethodInterceptor
   * @param args the arguments passed to the intercepted method; you may substitute a different
   * argument array as long as the types are compatible
   * @throws Throwable the bare exceptions thrown by the called method are passed through
   * without wrapping in an <code>InvocationTargetException</code>
   * @see MethodInterceptor#intercept
   */
  public Object invokeSuper(Object obj, Object[] args) throws Throwable {
    try {
      FastClassInfo fci = init();
      return fci.f2.invoke(fci.i2, obj, args);
    }
    catch (InvocationTargetException e) {
      throw e.getTargetException();
    }
  }

}
