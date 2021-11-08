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
package cn.taketoday.util;

import cn.taketoday.core.ConstructorNotFoundException;
import cn.taketoday.core.reflect.ReflectionException;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Constant;
import cn.taketoday.lang.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.UndeclaredThrowableException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fast reflection operation
 *
 * @author TODAY 2020-08-13 18:45
 * @since 2.1.7
 */
@SuppressWarnings("rawtypes")
public abstract class ReflectionUtils {

  /**
   * Pre-built MethodFilter that matches all non-bridge non-synthetic methods
   * which are not declared on {@code java.lang.Object}.
   */
  public static final MethodFilter USER_DECLARED_METHODS
          = (method -> !method.isBridge() && !method.isSynthetic());

  /**
   * Pre-built FieldFilter that matches all non-static, non-final fields.
   */
  public static final FieldFilter COPYABLE_FIELDS = //
          (field -> !(Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())));

  /**
   * Naming prefix for CGLIB-renamed methods.
   *
   * @see #isCglibRenamedMethod
   */
  public static final String CGLIB_RENAMED_METHOD_PREFIX = "today$";
  private static final Field[] EMPTY_FIELD_ARRAY = Constant.EMPTY_FIELD_ARRAY;
  private static final Method[] EMPTY_METHOD_ARRAY = Constant.EMPTY_METHOD_ARRAY;
  private static final Object[] EMPTY_OBJECT_ARRAY = Constant.EMPTY_OBJECT_ARRAY;

  /**
   * Cache for {@link Class#getDeclaredFields()}, allowing for fast iteration.
   */
  private static final ConcurrentReferenceHashMap<Class<?>, Field[]>
          DECLARED_FIELDS_CACHE = new ConcurrentReferenceHashMap<>(256);
  /**
   * Cache for {@link Class#getDeclaredMethods()} plus equivalent default methods
   * from Java 8 based interfaces, allowing for fast iteration.
   */
  private static final ConcurrentReferenceHashMap<Class<?>, Method[]>
          DECLARED_METHODS_CACHE = new ConcurrentReferenceHashMap<>(256);

  /**
   * Cache for equivalent methods on an interface implemented by the declaring class.
   *
   * @since 4.0
   */
  private static final ConcurrentReferenceHashMap<Method, Method>
          interfaceMethodCache = new ConcurrentReferenceHashMap<>(256);

  // Exception handling

  /**
   * Handle the given reflection exception.
   * <p>
   * Should only be called if no checked exception is expected to be thrown by a
   * target method, or if an error occurs while accessing a method or field.
   * <p>
   * Throws the underlying RuntimeException or Error in case of an
   * InvocationTargetException with such a root cause. Throws an
   * IllegalStateException with an appropriate message or
   * UndeclaredThrowableException otherwise.
   *
   * @param ex the reflection exception to handle
   */
  public static void handleReflectionException(Exception ex) {
    if (ex instanceof NoSuchMethodException) {
      throw new IllegalStateException("Method not found: " + ex.getMessage());
    }
    if (ex instanceof IllegalAccessException) {
      throw new IllegalStateException("Could not access method or field: " + ex.getMessage());
    }
    if (ex instanceof InvocationTargetException) {
      handleInvocationTargetException((InvocationTargetException) ex);
    }
    if (ex instanceof RuntimeException) {
      throw (RuntimeException) ex;
    }
    throw new UndeclaredThrowableException(ex);
  }

  /**
   * Handle the given invocation target exception. Should only be called if no
   * checked exception is expected to be thrown by the target method.
   * <p>
   * Throws the underlying RuntimeException or Error in case of such a root cause.
   * Throws an UndeclaredThrowableException otherwise.
   *
   * @param ex the invocation target exception to handle
   */
  public static void handleInvocationTargetException(InvocationTargetException ex) {
    rethrowRuntimeException(ex.getTargetException());
  }

  /**
   * Rethrow the given {@link Throwable exception}, which is presumably the
   * <em>target exception</em> of an {@link InvocationTargetException}. Should
   * only be called if no checked exception is expected to be thrown by the target
   * method.
   * <p>
   * Rethrows the underlying exception cast to a {@link RuntimeException} or
   * {@link Error} if appropriate; otherwise, throws an
   * {@link UndeclaredThrowableException}.
   *
   * @param ex the exception to rethrow
   * @throws RuntimeException the rethrown exception
   */
  public static void rethrowRuntimeException(Throwable ex) {
    if (ex instanceof RuntimeException) {
      throw (RuntimeException) ex;
    }
    if (ex instanceof Error) {
      throw (Error) ex;
    }
    throw new UndeclaredThrowableException(ex);
  }

  /**
   * Rethrow the given {@link Throwable exception}, which is presumably the
   * <em>target exception</em> of an {@link InvocationTargetException}. Should
   * only be called if no checked exception is expected to be thrown by the target
   * method.
   * <p>
   * Rethrows the underlying exception cast to an {@link Exception} or
   * {@link Error} if appropriate; otherwise, throws an
   * {@link UndeclaredThrowableException}.
   *
   * @param ex the exception to rethrow
   * @throws Exception the rethrown exception (in case of a checked exception)
   */
  public static void rethrowException(Throwable ex) throws Exception {
    if (ex instanceof Exception) {
      throw (Exception) ex;
    }
    if (ex instanceof Error) {
      throw (Error) ex;
    }
    throw new UndeclaredThrowableException(ex);
  }

  // Method handling

  /**
   * Determine whether the given class has a public method with the given signature.
   * <p>Essentially translates {@code NoSuchMethodException} to "false".
   *
   * @param clazz the clazz to analyze
   * @param methodName the name of the method
   * @param paramTypes the parameter types of the method
   * @return whether the class has a corresponding method
   * @see Class#getMethod
   * @since 4.0
   */
  public static boolean hasMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
    return (getMethodIfAvailable(clazz, methodName, paramTypes) != null);
  }

  /**
   * Determine whether the given class has a public method with the given signature,
   * and return it if available (else throws an {@code IllegalStateException}).
   * <p>In case of any signature specified, only returns the method if there is a
   * unique candidate, i.e. a single public method with the specified name.
   * <p>Essentially translates {@code NoSuchMethodException} to {@code IllegalStateException}.
   *
   * @param clazz the clazz to analyze
   * @param methodName the name of the method
   * @param paramTypes the parameter types of the method
   * (may be {@code null} to indicate any signature)
   * @return the method (never {@code null})
   * @throws IllegalStateException if the method has not been found
   * @see Class#getMethod
   * @since 4.0
   */
  public static Method getMethod(Class<?> clazz, String methodName, @Nullable Class<?>... paramTypes) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.notNull(methodName, "Method name must not be null");
    if (paramTypes != null) {
      try {
        return clazz.getMethod(methodName, paramTypes);
      }
      catch (NoSuchMethodException ex) {
        throw new IllegalStateException("Expected method not found: " + ex);
      }
    }
    else {
      Set<Method> candidates = findMethodCandidatesByName(clazz, methodName);
      if (candidates.size() == 1) {
        return candidates.iterator().next();
      }
      else if (candidates.isEmpty()) {
        throw new IllegalStateException("Expected method not found: " + clazz.getName() + '.' + methodName);
      }
      else {
        throw new IllegalStateException("No unique method found: " + clazz.getName() + '.' + methodName);
      }
    }
  }

  /**
   * Determine whether the given class has a public method with the given signature,
   * and return it if available (else return {@code null}).
   * <p>In case of any signature specified, only returns the method if there is a
   * unique candidate, i.e. a single public method with the specified name.
   * <p>Essentially translates {@code NoSuchMethodException} to {@code null}.
   *
   * @param clazz the clazz to analyze
   * @param methodName the name of the method
   * @param paramTypes the parameter types of the method
   * (may be {@code null} to indicate any signature)
   * @return the method, or {@code null} if not found
   * @see Class#getMethod
   * @since 4.0
   */
  @Nullable
  public static Method getMethodIfAvailable(
          Class<?> clazz, String methodName, @Nullable Class<?>... paramTypes) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.notNull(methodName, "Method name must not be null");
    if (paramTypes != null) {
      return getMethodOrNull(clazz, methodName, paramTypes);
    }
    else {
      Set<Method> candidates = findMethodCandidatesByName(clazz, methodName);
      if (candidates.size() == 1) {
        return candidates.iterator().next();
      }
      return null;
    }
  }

  /**
   * Return the number of methods with a given name (with any argument types),
   * for the given class and/or its superclasses. Includes non-public methods.
   *
   * @param clazz the clazz to check
   * @param methodName the name of the method
   * @return the number of methods with the given name
   * @since 4.0
   */
  public static int getMethodCountForName(Class<?> clazz, String methodName) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.notNull(methodName, "Method name must not be null");
    int count = 0;
    Method[] declaredMethods = clazz.getDeclaredMethods();
    for (Method method : declaredMethods) {
      if (methodName.equals(method.getName())) {
        count++;
      }
    }
    Class<?>[] ifcs = clazz.getInterfaces();
    for (Class<?> ifc : ifcs) {
      count += getMethodCountForName(ifc, methodName);
    }
    if (clazz.getSuperclass() != null) {
      count += getMethodCountForName(clazz.getSuperclass(), methodName);
    }
    return count;
  }

  /**
   * Return a public static method of a class.
   *
   * @param clazz the class which defines the method
   * @param methodName the static method name
   * @param args the parameter types to the method
   * @return the static method, or {@code null} if no static method was found
   * @throws IllegalArgumentException if the method name is blank or the clazz is null
   */
  @Nullable
  public static Method getStaticMethod(Class<?> clazz, String methodName, Class<?>... args) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.notNull(methodName, "Method name must not be null");
    try {
      Method method = clazz.getMethod(methodName, args);
      return Modifier.isStatic(method.getModifiers()) ? method : null;
    }
    catch (NoSuchMethodException ex) {
      return null;
    }
  }

  @Nullable
  static Method getMethodOrNull(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
    try {
      return clazz.getMethod(methodName, paramTypes);
    }
    catch (NoSuchMethodException ex) {
      return null;
    }
  }

  /**
   * @since 4.0
   */
  private static Set<Method> findMethodCandidatesByName(Class<?> clazz, String methodName) {
    HashSet<Method> candidates = new HashSet<>(1);
    Method[] methods = clazz.getMethods();
    for (Method method : methods) {
      if (methodName.equals(method.getName())) {
        candidates.add(method);
      }
    }
    return candidates;
  }

  /**
   * Determine whether the given class has a public method with the given signature.
   *
   * @param clazz the clazz to analyze
   * @param method the method to look for
   * @return whether the class has a corresponding method
   * @since 3.0
   */
  public static boolean hasMethod(Class<?> clazz, Method method) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.notNull(method, "Method must not be null");
    if (clazz == method.getDeclaringClass()) {
      return true;
    }
    String methodName = method.getName();
    Class<?>[] paramTypes = method.getParameterTypes();
    return getMethodOrNull(clazz, methodName, paramTypes) != null;
  }

  /**
   * Given a method, which may come from an interface, and a target class used
   * in the current reflective invocation, find the corresponding target method
   * if there is one. E.g. the method may be {@code IFoo.bar()} and the
   * target class may be {@code DefaultFoo}. In this case, the method may be
   * {@code DefaultFoo.bar()}. This enables attributes on that method to be found.
   * <p><b>NOTE:</b> In contrast to {@link cn.taketoday.aop.support.AopUtils#getMostSpecificMethod},
   * this method does <i>not</i> resolve Java 5 bridge methods automatically.
   * Call {@link cn.taketoday.core.BridgeMethodResolver#findBridgedMethod}
   * if bridge method resolution is desirable (e.g. for obtaining metadata from
   * the original method definition).
   *
   * @param method the method to be invoked, which may come from an interface
   * @param targetClass the target class for the current invocation
   * (may be {@code null} or may not even implement the method)
   * @return the specific target method, or the original method if the
   * {@code targetClass} does not implement it
   * @since 3.0
   */
  public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
    if (targetClass != null && targetClass != method.getDeclaringClass() && isOverridable(method, targetClass)) {
      try {
        if (Modifier.isPublic(method.getModifiers())) {
          try {
            return targetClass.getMethod(method.getName(), method.getParameterTypes());
          }
          catch (NoSuchMethodException ex) {
            return method;
          }
        }
        else {
          Method specificMethod =
                  ReflectionUtils.findMethod(targetClass, method.getName(), method.getParameterTypes());
          return (specificMethod != null ? specificMethod : method);
        }
      }
      catch (SecurityException ex) {
        // Security settings are disallowing reflective access; fall back to 'method' below.
      }
    }
    return method;
  }

  /**
   * Determine a corresponding interface method for the given method handle, if possible.
   * <p>This is particularly useful for arriving at a public exported type on Jigsaw
   * which can be reflectively invoked without an illegal access warning.
   *
   * @param method the method to be invoked, potentially from an implementation class
   * @return the corresponding interface method, or the original method if none found
   * @see #getMostSpecificMethod
   * @since 4.0
   */
  public static Method getInterfaceMethodIfPossible(Method method) {
    if (!Modifier.isPublic(method.getModifiers()) || method.getDeclaringClass().isInterface()) {
      return method;
    }
    return interfaceMethodCache.computeIfAbsent(method, key -> {
      Class<?> current = key.getDeclaringClass();
      while (current != null && current != Object.class) {
        Class<?>[] ifcs = current.getInterfaces();
        for (Class<?> ifc : ifcs) {
          try {
            return ifc.getMethod(key.getName(), key.getParameterTypes());
          }
          catch (NoSuchMethodException ex) {
            // ignore
          }
        }
        current = current.getSuperclass();
      }
      return key;
    });
  }

  /**
   * Determine whether the given method is overridable in the given target class.
   *
   * @param method the method to check
   * @param targetClass the target class to check against
   */
  private static boolean isOverridable(Method method, @Nullable Class<?> targetClass) {
    if (Modifier.isPrivate(method.getModifiers())) {
      return false;
    }
    if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())) {
      return true;
    }
    return targetClass == null
            || ClassUtils.getPackageName(method.getDeclaringClass()).equals(ClassUtils.getPackageName(targetClass));
  }

  /**
   * Attempt to find a {@link Method} on the supplied class with the supplied name
   * and no parameters. Searches all superclasses up to {@code Object}.
   * <p>
   * Returns {@code null} if no {@link Method} can be found.
   *
   * @param clazz the class to introspect
   * @param name the name of the method
   * @return the Method object, or {@code null} if none found
   */
  @Nullable
  public static Method findMethod(Class<?> clazz, String name) {
    return findMethod(clazz, name, (Class<?>[]) null);
  }

  /**
   * Attempt to find a {@link Method} on the supplied class with the supplied name
   * and parameter types. Searches all superclasses up to {@code Object}.
   * <p>
   * Returns {@code null} if no {@link Method} can be found.
   *
   * @param clazz the class to introspect
   * @param name the name of the method
   * @param paramTypes the parameter types of the method (may be {@code null} to indicate
   * any signature)
   * @return the Method object, or {@code null} if none found
   */
  @Nullable
  public static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.notNull(name, "Method name must not be null");
    Class<?> searchType = clazz;
    while (searchType != null) {
      Method[] methods = (searchType.isInterface() ? searchType.getMethods() : getDeclaredMethods(searchType, false));
      for (Method method : methods) {
        if (name.equals(method.getName())
                && (paramTypes == null || hasSameParams(method, paramTypes))) {
          return method;
        }
      }
      searchType = searchType.getSuperclass();
    }
    return null;
  }

  private static boolean hasSameParams(Method method, Class<?>[] paramTypes) {
    return paramTypes.length == method.getParameterCount()
            && Arrays.equals(paramTypes, method.getParameterTypes());
  }

  /**
   * Find the method from FunctionalInterface
   *
   * @throws IllegalArgumentException if given class is not a FunctionalInterface
   * @see FunctionalInterface
   * @since 4.0
   */
  public static Method findFunctionalInterfaceMethod(Class clazz) {
    if (clazz.isInterface()) {
      Method found = null;
      for (final Method method : clazz.getDeclaredMethods()) {
        if (!method.isDefault()) {
          if (found != null) {
            throw new IllegalArgumentException("expecting exactly 1 method in " + clazz);
          }
          found = method;
        }
      }
      return found;
    }
    throw new IllegalArgumentException(clazz + " is not an interface");
  }

  /**
   * Invoke the specified {@link Method} against the supplied target object with
   * no arguments. The target object can be {@code null} when invoking a static
   * {@link Method}.
   * <p>
   * Thrown exceptions are handled via a call to
   * {@link #handleReflectionException}.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @return the invocation result, if any
   * @see #invokeMethod(java.lang.reflect.Method, Object, Object[])
   */
  public static Object invokeMethod(Method method, Object target) {
    return invokeMethod(method, target, EMPTY_OBJECT_ARRAY);
  }

  /**
   * Invoke the specified {@link Method} against the supplied target object with
   * the supplied arguments. The target object can be {@code null} when invoking a
   * static {@link Method}.
   * <p>
   * Thrown exceptions are handled via a call to
   * {@link #handleReflectionException}.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @param args the invocation arguments (may be {@code null})
   * @return the invocation result, if any
   */
  public static Object invokeMethod(Method method, Object target, Object... args) {
    try {
      return method.invoke(target, args);
    }
    catch (Exception ex) {
      handleReflectionException(ex);
    }
    throw new IllegalStateException("Should never get here");
  }

  public static Object accessInvokeMethod(Method method, Object target, Object... args) {
    return invokeMethod(makeAccessible(method), target, args);
  }

  /**
   * Determine whether the given method explicitly declares the given exception or
   * one of its superclasses, which means that an exception of that type can be
   * propagated as-is within a reflective invocation.
   *
   * @param method the declaring method
   * @param exceptionType the exception to throw
   * @return {@code true} if the exception can be thrown as-is; {@code false} if
   * it needs to be wrapped
   */
  public static boolean declaresException(Method method, Class<?> exceptionType) {
    Assert.notNull(method, "Method must not be null");
    Class<?>[] declaredExceptions = method.getExceptionTypes();
    for (Class<?> declaredException : declaredExceptions) {
      if (declaredException.isAssignableFrom(exceptionType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Perform the given callback operation on all matching methods of the given
   * class, as locally declared or equivalent thereof (such as default methods on
   * Java 8 based interfaces that the given class implements).
   *
   * @param clazz the class to introspect
   * @param mc the callback to invoke for each method
   * @throws IllegalStateException if introspection fails
   * @see #doWithMethods
   */
  public static void doWithLocalMethods(Class<?> clazz, MethodCallback mc) {
    Method[] methods = getDeclaredMethods(clazz, false);
    for (Method method : methods) {
      try {
        mc.doWith(method);
      }
      catch (IllegalAccessException ex) {
        throw new IllegalStateException("Not allowed to access method '" + method.getName() + "': " + ex);
      }
    }
  }

  /**
   * Perform the given callback operation on all matching methods of the given
   * class and superclasses.
   * <p>
   * The same named method occurring on subclass and superclass will appear twice,
   * unless excluded by a {@link MethodFilter}.
   *
   * @param clazz the class to introspect
   * @param mc the callback to invoke for each method
   * @throws IllegalStateException if introspection fails
   * @see #doWithMethods(Class, MethodCallback, MethodFilter)
   */
  public static void doWithMethods(Class<?> clazz, MethodCallback mc) {
    doWithMethods(clazz, mc, null);
  }

  /**
   * Perform the given callback operation on all matching methods of the given
   * class and superclasses (or given interface and super-interfaces).
   * <p>
   * The same named method occurring on subclass and superclass will appear twice,
   * unless excluded by the specified {@link MethodFilter}.
   *
   * @param clazz the class to introspect
   * @param mc the callback to invoke for each method
   * @param mf the filter that determines the methods to apply the callback to
   * @throws IllegalStateException if introspection fails
   */
  public static void doWithMethods(Class<?> clazz, MethodCallback mc, MethodFilter mf) {
    // Keep backing up the inheritance hierarchy.
    Method[] methods = getDeclaredMethods(clazz, false);
    for (Method method : methods) {
      if (mf != null && !mf.matches(method)) {
        continue;
      }
      try {
        mc.doWith(method);
      }
      catch (IllegalAccessException ex) {
        throw new IllegalStateException("Not allowed to access method '" + method.getName() + "': " + ex);
      }
    }
    if (clazz.getSuperclass() != null
            && (mf != USER_DECLARED_METHODS || clazz.getSuperclass() != Object.class)) {
      doWithMethods(clazz.getSuperclass(), mc, mf);
    }
    else if (clazz.isInterface()) {
      for (Class<?> superIfc : clazz.getInterfaces()) {
        doWithMethods(superIfc, mc, mf);
      }
    }
  }

  /**
   * Get all declared methods on the leaf class and all superclasses. Leaf class
   * methods are included first.
   *
   * @param leafClass the class to introspect
   * @throws IllegalStateException if introspection fails
   */
  public static Method[] getAllDeclaredMethods(Class<?> leafClass) {
    final ArrayList<Method> methods = new ArrayList<>(32);
    doWithMethods(leafClass, methods::add);
    return toMethodArray(methods);
  }

  /**
   * Variant of {@link Class#getDeclaredMethods()} that uses a local cache in
   * order to avoid the JVM's SecurityManager check and new Method instances. In
   * addition, it also includes Java 8 default methods from locally implemented
   * interfaces, since those are effectively to be treated just like declared
   * methods.
   *
   * @param targetClass the class to introspect
   * @return the cached array of methods
   * @throws IllegalStateException if introspection fails
   * @see Class#getDeclaredMethods()
   */
  public static Method[] getDeclaredMethods(Class<?> targetClass) {
    return getDeclaredMethods(targetClass, true);
  }

  private static Method[] getDeclaredMethods(Class<?> targetClass, boolean defensive) {
    Assert.notNull(targetClass, "targetClass must not be null");
    Method[] result = DECLARED_METHODS_CACHE.get(targetClass);
    if (result == null) {
      try {
        Method[] declaredMethods = targetClass.getDeclaredMethods();
        List<Method> defaultMethods = findConcreteMethodsOnInterfaces(targetClass);
        if (defaultMethods != null) {
          result = new Method[declaredMethods.length + defaultMethods.size()];
          System.arraycopy(declaredMethods, 0, result, 0, declaredMethods.length);
          int index = declaredMethods.length;
          for (Method defaultMethod : defaultMethods) {
            result[index] = defaultMethod;
            index++;
          }
        }
        else {
          result = declaredMethods;
        }
        DECLARED_METHODS_CACHE.put(targetClass, (result.length == 0 ? EMPTY_METHOD_ARRAY : result));
      }
      catch (Throwable ex) {
        throw new IllegalStateException(
                "Failed to introspect Class [" + targetClass.getName() +
                        "] from ClassLoader [" + targetClass.getClassLoader() + "]", ex);
      }
    }
    return (result.length == 0 || !defensive) ? result : result.clone();
  }

  @Nullable
  private static List<Method> findConcreteMethodsOnInterfaces(Class<?> clazz) {
    ArrayList<Method> result = null;
    for (Class<?> ifc : clazz.getInterfaces()) {
      for (Method ifcMethod : ifc.getMethods()) {
        if (!Modifier.isAbstract(ifcMethod.getModifiers())) {
          if (result == null) {
            result = new ArrayList<>();
          }
          result.add(ifcMethod);
        }
      }
    }
    return result;
  }

  /**
   * Get the unique set of declared methods on the leaf class and all
   * superclasses. Leaf class methods are included first and while traversing the
   * superclass hierarchy any methods found with signatures matching a method
   * already included are filtered out.
   *
   * @param leafClass the class to introspect
   * @throws IllegalStateException if introspection fails
   */
  public static Method[] getUniqueDeclaredMethods(Class<?> leafClass) {
    return getUniqueDeclaredMethods(leafClass, null);
  }

  /**
   * Get the unique set of declared methods on the leaf class and all
   * superclasses. Leaf class methods are included first and while traversing the
   * superclass hierarchy any methods found with signatures matching a method
   * already included are filtered out.
   *
   * @param leafClass the class to introspect
   * @param mf the filter that determines the methods to take into account
   * @throws IllegalStateException if introspection fails
   */
  public static Method[] getUniqueDeclaredMethods(Class<?> leafClass, MethodFilter mf) {
    final ArrayList<Method> methods = new ArrayList<>(32);
    doWithMethods(leafClass, method -> {
      boolean knownSignature = false;
      Method methodBeingOverriddenWithCovariantReturnType = null;
      for (Method existingMethod : methods) {
        if (method.getName().equals(existingMethod.getName())
                && method.getParameterCount() == existingMethod.getParameterCount()
                && Arrays.equals(method.getParameterTypes(), existingMethod.getParameterTypes())) {
          // Is this a covariant return type situation?
          if (existingMethod.getReturnType() != method.getReturnType()
                  && existingMethod.getReturnType().isAssignableFrom(method.getReturnType())) {
            methodBeingOverriddenWithCovariantReturnType = existingMethod;
          }
          else {
            knownSignature = true;
          }
          break;
        }
      }
      if (methodBeingOverriddenWithCovariantReturnType != null) {
        methods.remove(methodBeingOverriddenWithCovariantReturnType);
      }
      if (!knownSignature && !isCglibRenamedMethod(method)) {
        methods.add(method);
      }
    }, mf);
    return toMethodArray(methods);
  }

  /**
   * Determine whether the given method is an "equals" method.
   *
   * @see java.lang.Object#equals(Object)
   */
  public static boolean isEqualsMethod(Method method) {
    if (method == null || !method.getName().equals("equals")) {
      return false;
    }
    if (method.getParameterCount() != 1) {
      return false;
    }
    return method.getParameterTypes()[0] == Object.class;
  }

  /**
   * Determine whether the given method is a "hashCode" method.
   *
   * @see java.lang.Object#hashCode()
   */
  public static boolean isHashCodeMethod(Method method) {
    return (method != null && method.getName().equals("hashCode") && method.getParameterCount() == 0);
  }

  /**
   * Determine whether the given method is a "toString" method.
   *
   * @see java.lang.Object#toString()
   */
  public static boolean isToStringMethod(Method method) {
    return (method != null && method.getName().equals("toString") && method.getParameterCount() == 0);
  }

  /**
   * Determine whether the given method is originally declared by
   * {@link java.lang.Object}.
   */
  public static boolean isObjectMethod(Method method) {
    return (method != null
            && (
            method.getDeclaringClass() == Object.class
                    || isEqualsMethod(method)
                    || isHashCodeMethod(method)
                    || isToStringMethod(method))
    );
  }

  /**
   * Determine whether the given method is a "finalize" method.
   *
   * @see java.lang.Object#finalize()
   */
  public static boolean isFinalizeMethod(Method method) {
    return method != null
            && method.getName().equals("finalize")
            && method.getParameterCount() == 0;
  }

  /**
   * Determine whether the given method is a CGLIB 'renamed' method, following the
   * pattern "CGLIB$methodName$0".
   *
   * @param renamedMethod the method to check
   */
  public static boolean isCglibRenamedMethod(Method renamedMethod) {
    String name = renamedMethod.getName();
    if (name.startsWith(CGLIB_RENAMED_METHOD_PREFIX)) {
      int i = name.length() - 1;
      while (i >= 0 && Character.isDigit(name.charAt(i))) {
        i--;
      }
      return (i > CGLIB_RENAMED_METHOD_PREFIX.length() && (i < name.length() - 1) && name.charAt(i) == '$');
    }
    return false;
  }

  /**
   * Make the given method accessible, explicitly setting it accessible if
   * necessary. The {@code setAccessible(true)} method is only called when
   * actually necessary, to avoid unnecessary conflicts with a JVM SecurityManager
   * (if active).
   *
   * @param method the method to make accessible
   * @see java.lang.reflect.Method#setAccessible
   */
//  @SuppressWarnings("deprecation") // on JDK 9
  public static Method makeAccessible(Method method) {
    Assert.notNull(method, "method must not be null");
    if ((!Modifier.isPublic(method.getModifiers()) ||
            !Modifier.isPublic(method.getDeclaringClass().getModifiers())) && !method.isAccessible()) {
      method.setAccessible(true);
    }
    return method;
  }

  /**
   * Copy the given {@code Collection} into a {@code Method} array.
   * <p>The {@code Collection} must contain {@code Method} elements only.
   *
   * @param collection the {@code Collection} to copy
   * @return the {@code Method} array
   * @see StringUtils#toStringArray(Collection)
   * @see ClassUtils#toClassArray(Collection)
   * @since 4.0
   */
  public static Method[] toMethodArray(Collection<Method> collection) {
    return CollectionUtils.isEmpty(collection)
            ? EMPTY_METHOD_ARRAY
            : collection.toArray(new Method[collection.size()]);
  }

  // Field handling

  /**
   * Attempt to find a {@link Field field} on the supplied {@link Class} with the
   * supplied {@code name}. Searches all superclasses up to {@link Object}.
   *
   * @param clazz the class to introspect
   * @param name the name of the field
   * @return the corresponding Field object, or {@code null} if not found
   */
  @Nullable
  public static Field findField(Class<?> clazz, String name) {
    return findField(clazz, name, null);
  }

  /**
   * Attempt to find a {@link Field field} on the supplied {@link Class} with the
   * supplied {@code name} and/or {@link Class type}. Searches all superclasses up
   * to {@link Object}.
   *
   * @param clazz the class to introspect
   * @param name the name of the field (may be {@code null} if type is specified)
   * @param type the type of the field (may be {@code null} if name is specified)
   * @return the corresponding Field object, or {@code null} if not found
   */
  @Nullable
  public static Field findField(Class<?> clazz, String name, Class<?> type) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.isTrue(name != null || type != null, "Either name or type of the field must be specified");
    Class<?> searchType = clazz;
    while (Object.class != searchType && searchType != null) {
      Field[] fields = getDeclaredFields(searchType);
      for (Field field : fields) {
        if ((name == null || name.equals(field.getName()))
                && (type == null || type.equals(field.getType()))) {
          return field;
        }
      }
      searchType = searchType.getSuperclass();
    }
    return null;
  }

  /**
   * Set the field represented by the supplied {@linkplain Field field object} on
   * the specified {@linkplain Object target object} to the specified
   * {@code value}.
   * <p>
   * In accordance with {@link Field#set(Object, Object)} semantics, the new value
   * is automatically unwrapped if the underlying field has a primitive type.
   * <p>
   * This method does not support setting {@code static final} fields.
   * <p>
   * Thrown exceptions are handled via a call to
   * {@link #handleReflectionException(Exception)}.
   *
   * @param field the field to set
   * @param target the target object on which to set the field
   * @param value the value to set (may be {@code null})
   */
  public static void setField(Field field, Object target, Object value) {
    try {
      field.set(target, value);
    }
    catch (IllegalAccessException ex) {
      handleReflectionException(ex);
    }
  }

  /**
   * Get the field represented by the supplied {@link Field field object} on the
   * specified {@link Object target object}. In accordance with
   * {@link Field#get(Object)} semantics, the returned value is automatically
   * wrapped if the underlying field has a primitive type.
   * <p>
   * Thrown exceptions are handled via a call to
   * {@link #handleReflectionException(Exception)}.
   *
   * @param field the field to get
   * @param target the target object from which to get the field
   * @return the field's current value
   */
  public static Object getField(Field field, Object target) {
    try {
      return field.get(target);
    }
    catch (IllegalAccessException ex) {
      handleReflectionException(ex);
    }
    throw new IllegalStateException("Should never get here");
  }

  /**
   * Invoke the given callback on all locally declared fields in the given class.
   *
   * @param clazz the target class to analyze
   * @param fc the callback to invoke for each field
   * @throws IllegalStateException if introspection fails
   * @see #doWithFields
   */
  public static void doWithLocalFields(Class<?> clazz, FieldCallback fc) {
    for (Field field : getDeclaredFields(clazz)) {
      try {
        fc.doWith(field);
      }
      catch (IllegalAccessException ex) {
        throw new IllegalStateException("Not allowed to access field '" + field.getName() + "': " + ex);
      }
    }
  }

  /**
   * Invoke the given callback on all fields in the target class, going up the
   * class hierarchy to get all declared fields.
   *
   * @param clazz the target class to analyze
   * @param fc the callback to invoke for each field
   * @throws IllegalStateException if introspection fails
   */
  public static void doWithFields(Class<?> clazz, FieldCallback fc) {
    doWithFields(clazz, fc, null);
  }

  /**
   * Invoke the given callback on all fields in the target class, going up the
   * class hierarchy to get all declared fields.
   *
   * @param clazz the target class to analyze
   * @param fc the callback to invoke for each field
   * @param ff the filter that determines the fields to apply the callback to
   * @throws IllegalStateException if introspection fails
   */
  public static void doWithFields(Class<?> clazz, FieldCallback fc, FieldFilter ff) {
    // Keep backing up the inheritance hierarchy.
    Class<?> targetClass = clazz;
    do {
      Field[] fields = getDeclaredFields(targetClass);
      for (Field field : fields) {
        if (ff != null && !ff.matches(field)) {
          continue;
        }
        try {
          fc.doWith(field);
        }
        catch (IllegalAccessException ex) {
          throw new IllegalStateException("Not allowed to access field '" + field.getName() + "': " + ex);
        }
      }
      targetClass = targetClass.getSuperclass();
    }
    while (targetClass != null && targetClass != Object.class);
  }

  /**
   * This variant retrieves {@link Class#getDeclaredFields()} from a local cache
   * in order to avoid the JVM's SecurityManager check and defensive array
   * copying.
   *
   * @param clazz the class to introspect
   * @return the cached array of fields
   * @throws IllegalStateException if introspection fails
   * @see Class#getDeclaredFields()
   */
  public static Field[] getDeclaredFields(Class<?> clazz) {
    Assert.notNull(clazz, "Class must not be null");
    Field[] result = DECLARED_FIELDS_CACHE.get(clazz);
    if (result == null) {
      try {
        result = clazz.getDeclaredFields();
        DECLARED_FIELDS_CACHE.put(clazz, (result.length == 0 ? EMPTY_FIELD_ARRAY : result));
      }
      catch (Throwable ex) {
        throw new IllegalStateException(
                "Failed to introspect Class [" + clazz.getName() +
                        "] from ClassLoader [" + clazz.getClassLoader() + "]", ex);
      }
    }
    return result;
  }

  /**
   * Given the source object and the destination, which must be the same class or
   * a subclass, copy all fields, including inherited fields. Designed to work on
   * objects with public no-arg constructors.
   *
   * @throws IllegalStateException if introspection fails
   */
  public static void shallowCopyFieldState(final Object src, final Object dest) {
    Assert.notNull(src, "Source for field copy cannot be null");
    Assert.notNull(dest, "Destination for field copy cannot be null");
    if (!src.getClass().isAssignableFrom(dest.getClass())) {
      throw new IllegalArgumentException(
              "Destination class [" + dest.getClass().getName() +
                      "] must be same or subclass as source class [" + src.getClass().getName() + "]");
    }

    final class CopyFieldCallback implements FieldCallback {

      @Override
      public void doWith(final Field field) {
        copyField(field, src, dest);
      }
    }

    doWithFields(src.getClass(), new CopyFieldCallback(), COPYABLE_FIELDS);
  }

  /**
   * Copy a given field from the source object to the destination
   *
   * @param field target field property
   */
  public static void copyField(final Field field, final Object src, final Object dest) {
    makeAccessible(field);
    setField(field, dest, getField(field, src));
  }

  /**
   * Make the given field accessible, explicitly setting it accessible if
   * necessary. The {@code setAccessible(true)} method is only called when
   * actually necessary, to avoid unnecessary conflicts with a JVM SecurityManager
   * (if active).
   *
   * @param field the field to make accessible
   * @see java.lang.reflect.Field#setAccessible
   */
  @SuppressWarnings("deprecation") // on JDK 9
  public static Field makeAccessible(Field field) {
    Assert.notNull(field, "field must not be null");

    if ((!Modifier.isPublic(field.getModifiers()) ||
            !Modifier.isPublic(field.getDeclaringClass().getModifiers()) ||
            Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
      field.setAccessible(true);
    }
    return field;
  }

  /**
   * Determine whether the given field is a "public static final" constant.
   *
   * @param field the field to check
   */
  public static boolean isPublicStaticFinal(Field field) {
    int modifiers = field.getModifiers();
    return (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers));
  }

  /**
   * @since 4.0
   */
  public static Field[] toFieldArray(Collection<Field> fields) {
    return CollectionUtils.isEmpty(fields)
            ? EMPTY_FIELD_ARRAY
            : fields.toArray(new Field[fields.size()]);
  }

  // Constructor handling

  public static <T> Constructor<T> accessibleConstructor(
          final Class<T> targetClass, final Class<?>... parameterTypes) {
    return makeAccessible(getConstructor(targetClass, parameterTypes));
  }

  public static <T> Constructor<T> makeAccessible(Constructor<T> constructor) {
    Assert.notNull(constructor, "constructor must not be null");

    if ((!Modifier.isPublic(constructor.getModifiers())
            || !Modifier.isPublic(constructor.getDeclaringClass().getModifiers()))
            && !constructor.isAccessible()) {

      constructor.setAccessible(true);
    }
    return constructor;
  }

  /**
   * Determine whether the given class has a declared constructor with the given signature.
   * <p>Essentially translates {@code NoSuchMethodException} to "false".
   *
   * @param clazz the clazz to analyze
   * @param paramTypes the parameter types of the method
   * @return whether the class has a corresponding constructor
   * @see Class#getDeclaredConstructor
   * @since 4.0
   */
  public static boolean hasConstructor(Class<?> clazz, Class<?>... paramTypes) {
    return getConstructorIfAvailable(clazz, paramTypes) != null;
  }

  /**
   * Determine whether the given class has a declared constructor with the given signature,
   * and return it if available (else return {@code null}).
   * <p>Essentially translates {@code NoSuchMethodException} to {@code null}.
   *
   * @param clazz the clazz to analyze
   * @param paramTypes the parameter types of the method
   * @return the constructor, or {@code null} if not found
   * @see Class#getDeclaredConstructor
   * @since 4.0
   */
  @Nullable
  public static <T> Constructor<T> getConstructorIfAvailable(Class<T> clazz, Class<?>... paramTypes) {
    Assert.notNull(clazz, "Class must not be null");
    try {
      return clazz.getDeclaredConstructor(paramTypes);
    }
    catch (NoSuchMethodException ex) {
      return null;
    }
  }

  /**
   * getDeclaredConstructor
   *
   * @throws ConstructorNotFoundException not found
   * @see Class#getDeclaredConstructor
   * @since 4.0
   */
  public static <T> Constructor<T> getConstructor(Class<T> type, Class<?>... parameterTypes) {
    Assert.notNull(type, "Class must not be null");
    try {
      return type.getDeclaredConstructor(parameterTypes);
    }
    catch (NoSuchMethodException e) {
      throw new ConstructorNotFoundException(type, parameterTypes, e);
    }
  }

  public static <T> T invokeConstructor(Constructor<T> constructor, Object[] args) {
    try {
      return constructor.newInstance(args);
    }
    catch (Exception ex) {
      handleReflectionException(ex);
    }
    throw new IllegalStateException("Should never get here");
  }

  // Cache handling

  /**
   * Clear the internal method/field cache.
   */
  public static void clearCache() {
    DECLARED_FIELDS_CACHE.clear();
    DECLARED_METHODS_CACHE.clear();
  }

  public static Field obtainField(Class<?> clazz, String name) {
    final Field field = findField(clazz, name);
    if (field == null) {
      throw new ReflectionException(
              "No such field named: " + name + " in class: " + clazz.getName());
    }
    return field;
  }

  public static Method obtainMethod(
          final Class<?> targetClass, final String methodName, final Class<?>... parameterTypes) {
    final Method declaredMethod = findMethod(targetClass, methodName, parameterTypes);
    if (declaredMethod == null) {
      throw new ReflectionException(
              "No such method named: " + methodName + " in class: " + targetClass.getName());
    }
    return declaredMethod;
  }

  // Accessor
  // --------------------------------

  /**
   * find getter method
   *
   * @since 3.0.2
   */
  public static Method getReadMethod(Field field) {
    Assert.notNull(field, "field must not be null");
    final Class<?> type = field.getType();
    final String propertyName = field.getName();
    return getReadMethod(field.getDeclaringClass(), type, propertyName);
  }

  /**
   * find getter method
   *
   * @since 3.0.2
   */
  public static Method getReadMethod(Class<?> declaredClass, Class<?> type, String name) {
    final String getterName = getterPropertyName(name, type);
    for (final Method declaredMethod : declaredClass.getDeclaredMethods()) {
      if (declaredMethod.getName().equals(getterName)
              && declaredMethod.getParameterCount() == 0 && declaredMethod.getReturnType() == type) {
        return declaredMethod;
      }
    }
    return null;
  }

  /**
   * find setter method
   *
   * @since 3.0.2
   */
  public static Method getWriteMethod(Field field) {
    Assert.notNull(field, "field must not be null");
    final Class<?> type = field.getType();
    final String propertyName = field.getName();
    return getWriteMethod(field.getDeclaringClass(), type, propertyName);
  }

  /**
   * find setter method
   *
   * @since 3.0.2
   */
  public static Method getWriteMethod(Class<?> declaredClass, Class<?> type, String name) {
    final String setterName = setterPropertyName(name, type);
    for (final Method declaredMethod : declaredClass.getDeclaredMethods()) {
      if (declaredMethod.getName().equals(setterName)) {
        final Class<?>[] parameterTypes = declaredMethod.getParameterTypes();
        if (parameterTypes.length == 1 && parameterTypes[0] == type) {
          return declaredMethod;
        }
      }
    }
    return null;
  }

  /**
   * <pre>
   *   setterPropertyName("isName", boolean.class); -> setName
   *   setterPropertyName("isName", String.class); -> setIsName
   * </pre>
   */
  static String setterPropertyName(String name, final Class<?> type) {
    if (type == boolean.class && name.startsWith("is")) {
      name = name.substring(2);
    }
    return "set".concat(StringUtils.capitalize(name));
  }

  /**
   * <pre>
   * getterPropertyName("isName", boolean.class); -> isName
   * getterPropertyName("isName", String.class); -> getIsName
   * </pre>
   */
  static String getterPropertyName(final String name, final Class<?> type) {
    if (type == boolean.class) {
      if (name.startsWith("is")) {
        return name;
      }
      return "is".concat(StringUtils.capitalize(name));
    }
    return "get".concat(StringUtils.capitalize(name));
  }

  /**
   * Action to take on each method.
   */
  @FunctionalInterface
  public interface MethodCallback {

    /**
     * Perform an operation using the given method.
     *
     * @param method the method to operate on
     */
    void doWith(Method method) throws IllegalArgumentException, IllegalAccessException;
  }

  /**
   * Callback optionally used to filter methods to be operated on by a method
   * callback.
   */
  @FunctionalInterface
  public interface MethodFilter {

    /**
     * Determine whether the given method matches.
     *
     * @param method the method to check
     */
    boolean matches(Method method);

    /**
     * Create a composite filter based on this filter <em>and</em> the provided filter.
     * <p>If this filter does not match, the next filter will not be applied.
     *
     * @param next the next {@code MethodFilter}
     * @return a composite {@code MethodFilter}
     * @throws IllegalArgumentException if the MethodFilter argument is {@code null}
     * @since 4.0
     */
    default MethodFilter and(MethodFilter next) {
      Assert.notNull(next, "Next MethodFilter must not be null");
      return method -> matches(method) && next.matches(method);
    }
  }

  /**
   * Callback interface invoked on each field in the hierarchy.
   */
  @FunctionalInterface
  public interface FieldCallback {

    /**
     * Perform an operation using the given field.
     *
     * @param field the field to operate on
     */
    void doWith(Field field) throws IllegalArgumentException, IllegalAccessException;
  }

  /**
   * Callback optionally used to filter fields to be operated on by a field
   * callback.
   */
  @FunctionalInterface
  public interface FieldFilter {

    /**
     * Determine whether the given field matches.
     *
     * @param field the field to check
     */
    boolean matches(Field field);

    /**
     * Create a composite filter based on this filter <em>and</em> the provided filter.
     * <p>If this filter does not match, the next filter will not be applied.
     *
     * @param next the next {@code FieldFilter}
     * @return a composite {@code FieldFilter}
     * @throws IllegalArgumentException if the FieldFilter argument is {@code null}
     * @since 4.0
     */
    default FieldFilter and(FieldFilter next) {
      Assert.notNull(next, "Next FieldFilter must not be null");
      return field -> matches(field) && next.matches(field);
    }

  }

  //

  /**
   * Get {@link Parameter} index
   *
   * @param parameter {@link Parameter}
   * @since 3.0
   */
  public static int getParameterIndex(final Parameter parameter) {
    Executable executable = parameter.getDeclaringExecutable();
    Parameter[] allParams = executable.getParameters();
    // Try first with identity checks for greater performance.
    for (int i = 0; i < allParams.length; i++) {
      if (parameter == allParams[i]) {
        return i;
      }
    }
    // Potentially try again with object equality checks in order to avoid race
    // conditions while invoking java.lang.reflect.Executable.getParameters().
    for (int i = 0; i < allParams.length; i++) {
      if (parameter.equals(allParams[i])) {
        return i;
      }
    }
    throw new IllegalArgumentException(
            "Given parameter [" + parameter + "] does not match any parameter in the declaring executable");
  }

  /**
   * Get {@link Parameter} with given {@code parameterIndex}
   *
   * @param executable {@link Method} or {@link Constructor}
   * @throws IllegalArgumentException parameter index is illegal
   * @since 3.0
   */
  public static Parameter getParameter(final Executable executable, final int parameterIndex) {
    Assert.notNull(executable, "Executable must not be null");
    final Parameter[] parameters = executable.getParameters();
    if (parameterIndex < 0 || parameterIndex >= parameters.length) {
      throw new IllegalArgumentException("parameter index is illegal");
    }
    return parameters[parameterIndex];
  }

  // newInstance

  /**
   * Get instance with bean class
   *
   * @param beanClassName bean class name string
   * @return the instance of target class
   * @throws ClassNotFoundException If the class was not found
   * @since 4.0
   */
  @SuppressWarnings("unchecked")
  public static <T> T newInstance(String beanClassName) throws ClassNotFoundException {
    return (T) newInstance(ClassUtils.resolveClassName(beanClassName, null));
  }

  /**
   * @since 4.0
   */
  public static Object newInstance(Class<?> type) {
    return newInstance(type, Constant.EMPTY_CLASS_ARRAY, null);
  }

  /**
   * @since 4.0
   */
  public static Object newInstance(Class<?> type, Class[] parameterTypes, Object[] args) {
    return invokeConstructor(getConstructor(type, parameterTypes), args);
  }

  /**
   * @since 4.0
   */
  public static ProtectionDomain getProtectionDomain(final Class<?> source) {
    if (source == null) {
      return null;
    }
    return source.getProtectionDomain();
  }

  /**
   * @since 4.0
   */
  public static <T> Class<T> defineClass(String className, byte[] bytes) {
    return defineClass(className, bytes, ClassUtils.getDefaultClassLoader(), null);
  }

  /**
   * Converts an array of bytes into an instance of class <tt>Class</tt>.
   * Before the <tt>Class</tt> can be used it must be resolved.
   *
   * <p> This method assigns a default {@link java.security.ProtectionDomain
   * <tt>ProtectionDomain</tt>} to the newly defined class.
   * <tt>Policy.getPolicy().getPermissions(new CodeSource(null, null))</tt>}
   * is invoked.  The default domain is created on the first invocation of
   * {@link #defineClass(String, byte[]) <tt>defineClass</tt>},
   * and re-used on subsequent invocations.
   *
   * <p> To assign a specific <tt>ProtectionDomain</tt> to the class, use
   * the {@link #defineClass(String, byte[], ClassLoader,
   * java.security.ProtectionDomain) <tt>defineClass</tt>} method that takes a
   * <tt>ProtectionDomain</tt> as one of its arguments.  </p>
   *
   * @param className The expected <a href="#name">binary name</a> of the class, or
   * <tt>null</tt> if not known
   * @param bytes The bytes that make up the class data.  The bytes in positions
   * <tt>off</tt> through <tt>off+len-1</tt> should have the format
   * of a valid class file as defined by
   * <cite>The Java&trade; Virtual Machine Specification</cite>.
   * @return The <tt>Class</tt> object that was created from the specified
   * class data.
   * @throws ClassFormatError If the data did not contain a valid class
   * @throws IndexOutOfBoundsException If either <tt>off</tt> or <tt>len</tt> is negative, or if
   * <tt>off+len</tt> is greater than <tt>b.length</tt>.
   * @throws SecurityException If an attempt is made to add this class to a package that
   * contains classes that were signed by a different set of
   * certificates than this class (which is unsigned), or if
   * <tt>name</tt> begins with "<tt>java.</tt>".
   * @see java.security.CodeSource
   * @see java.security.SecureClassLoader
   * @since 4.0
   */
  public static <T> Class<T> defineClass(String className, byte[] bytes, ClassLoader loader) {
    return defineClass(className, bytes, loader, null);
  }

  /**
   * Converts an array of bytes into an instance of class <tt>Class</tt>,
   * with an optional <tt>ProtectionDomain</tt>.  If the domain is
   * <tt>null</tt>, then a default domain will be assigned to the class as
   * specified in the documentation for {@link #defineClass(String, byte[])}.
   * Before the class can be used it must be resolved.
   *
   * <p> The first class defined in a package determines the exact set of
   * certificates that all subsequent classes defined in that package must
   * contain.  The set of certificates for a class is obtained from the
   * {@link java.security.CodeSource <tt>CodeSource</tt>} within the
   * <tt>ProtectionDomain</tt> of the class.  Any classes added to that
   * package must contain the same set of certificates or a
   * <tt>SecurityException</tt> will be thrown.  Note that if
   * <tt>name</tt> is <tt>null</tt>, this check is not performed.
   * You should always pass in the <a href="#name">binary name</a> of the
   * class you are defining as well as the bytes.  This ensures that the
   * class you are defining is indeed the class you think it is.
   *
   * <p> The specified <tt>name</tt> cannot begin with "<tt>java.</tt>", since
   * all classes in the "<tt>java.*</tt> packages can only be defined by the
   * bootstrap class loader.  If <tt>name</tt> is not <tt>null</tt>, it
   * must be equal to the <a href="#name">binary name</a> of the class
   * specified by the byte array "<tt>b</tt>", otherwise a {@link
   * NoClassDefFoundError <tt>NoClassDefFoundError</tt>} will be thrown. </p>
   *
   * @param className The expected <a href="#name">binary name</a> of the class, or
   * <tt>null</tt> if not known
   * @param bytes The bytes that make up the class data. The bytes in positions
   * <tt>off</tt> through <tt>off+len-1</tt> should have the format
   * of a valid class file as defined by
   * <cite>The Java&trade; Virtual Machine Specification</cite>.
   * @param protection The ProtectionDomain of the class
   * @return The <tt>Class</tt> object created from the data,
   * and optional <tt>ProtectionDomain</tt>.
   * @throws ClassFormatError If the data did not contain a valid class
   * @throws NoClassDefFoundError If <tt>name</tt> is not equal to the <a href="#name">binary
   * name</a> of the class specified by <tt>b</tt>
   * @throws IndexOutOfBoundsException If either <tt>off</tt> or <tt>len</tt> is negative, or if
   * <tt>off+len</tt> is greater than <tt>b.length</tt>.
   * @throws SecurityException If an attempt is made to add this class to a package that
   * contains classes that were signed by a different set of
   * certificates than this class, or if <tt>name</tt> begins with
   * "<tt>java.</tt>".
   * @since 4.0
   */
  @SuppressWarnings("unchecked")
  public static <T> Class<T> defineClass(
          String className, byte[] bytes, ClassLoader loader, @Nullable ProtectionDomain protection) {
    try {
      return (Class<T>) new DefineClassClassLoader(loader, bytes, className, protection).loadClass(className);
    }
    catch (Exception e) {
      throw new ReflectionException("defineClass '" + className + "' failed", e);
    }
  }
}

final class DefineClassClassLoader extends ClassLoader {
  final byte[] bytes;
  private final String className;
  private final ProtectionDomain protection;

  public DefineClassClassLoader(ClassLoader loader, byte[] bytes, String className, ProtectionDomain protection) {
    super(loader);
    this.bytes = bytes;
    this.className = className;
    this.protection = protection;
  }

  @Override
  public Class<?> loadClass(String name) throws ClassNotFoundException {
    if (name.equals(this.className)) {
      return defineClass(name, bytes, 0, bytes.length, protection);
    }
    return getParent().loadClass(name);
  }

}
