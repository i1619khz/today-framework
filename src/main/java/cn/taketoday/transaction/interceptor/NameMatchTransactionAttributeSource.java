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

package cn.taketoday.transaction.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import cn.taketoday.beans.factory.InitializingBean;
import cn.taketoday.context.expression.EmbeddedValueResolverAware;
import cn.taketoday.core.StringValueResolver;
import cn.taketoday.lang.Nullable;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.util.ClassUtils;
import cn.taketoday.util.ObjectUtils;
import cn.taketoday.util.StringUtils;

/**
 * Simple {@link TransactionAttributeSource} implementation that
 * allows attributes to be matched by registered name.
 *
 * @author Juergen Hoeller
 * @see #isMatch
 * @see MethodMapTransactionAttributeSource
 * @since 21.08.2003
 */
@SuppressWarnings("serial")
public class NameMatchTransactionAttributeSource
        implements TransactionAttributeSource, EmbeddedValueResolverAware, InitializingBean, Serializable {

  /**
   * Logger available to subclasses.
   * <p>Static for optimal serialization.
   */
  protected static final Logger logger = LoggerFactory.getLogger(NameMatchTransactionAttributeSource.class);

  /** Keys are method names; values are TransactionAttributes. */
  private final Map<String, TransactionAttribute> nameMap = new HashMap<>();

  @Nullable
  private StringValueResolver embeddedValueResolver;

  /**
   * Set a name/attribute map, consisting of method names
   * (e.g. "myMethod") and {@link TransactionAttribute} instances.
   *
   * @see #setProperties
   * @see TransactionAttribute
   */
  public void setNameMap(Map<String, TransactionAttribute> nameMap) {
    nameMap.forEach(this::addTransactionalMethod);
  }

  /**
   * Parse the given properties into a name/attribute map.
   * <p>Expects method names as keys and String attributes definitions as values,
   * parsable into {@link TransactionAttribute} instances via a
   * {@link TransactionAttributeEditor}.
   *
   * @see #setNameMap
   * @see TransactionAttributeEditor
   */
  public void setProperties(Properties transactionAttributes) {
    TransactionAttributeEditor tae = new TransactionAttributeEditor();
    Enumeration<?> propNames = transactionAttributes.propertyNames();
    while (propNames.hasMoreElements()) {
      String methodName = (String) propNames.nextElement();
      String value = transactionAttributes.getProperty(methodName);
      tae.setAsText(value);
      TransactionAttribute attr = (TransactionAttribute) tae.getValue();
      addTransactionalMethod(methodName, attr);
    }
  }

  /**
   * Add an attribute for a transactional method.
   * <p>Method names can be exact matches, or of the pattern "xxx*",
   * "*xxx", or "*xxx*" for matching multiple methods.
   *
   * @param methodName the name of the method
   * @param attr attribute associated with the method
   */
  public void addTransactionalMethod(String methodName, TransactionAttribute attr) {
    if (logger.isDebugEnabled()) {
      logger.debug("Adding transactional method [" + methodName + "] with attribute [" + attr + "]");
    }
    if (this.embeddedValueResolver != null && attr instanceof DefaultTransactionAttribute dta) {
      dta.resolveAttributeStrings(this.embeddedValueResolver);
    }
    this.nameMap.put(methodName, attr);
  }

  @Override
  public void setEmbeddedValueResolver(StringValueResolver resolver) {
    this.embeddedValueResolver = resolver;
  }

  @Override
  public void afterPropertiesSet() {
    for (TransactionAttribute attr : this.nameMap.values()) {
      if (attr instanceof DefaultTransactionAttribute dta) {
        dta.resolveAttributeStrings(this.embeddedValueResolver);
      }
    }
  }

  @Override
  @Nullable
  public TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
    if (!ClassUtils.isUserLevelMethod(method)) {
      return null;
    }

    // Look for direct name match.
    String methodName = method.getName();
    TransactionAttribute attr = this.nameMap.get(methodName);

    if (attr == null) {
      // Look for most specific name match.
      String bestNameMatch = null;
      for (String mappedName : this.nameMap.keySet()) {
        if (isMatch(methodName, mappedName) &&
                (bestNameMatch == null || bestNameMatch.length() <= mappedName.length())) {
          attr = this.nameMap.get(mappedName);
          bestNameMatch = mappedName;
        }
      }
    }

    return attr;
  }

  /**
   * Determine if the given method name matches the mapped name.
   * <p>The default implementation checks for "xxx*", "*xxx", and "*xxx*" matches,
   * as well as direct equality. Can be overridden in subclasses.
   *
   * @param methodName the method name of the class
   * @param mappedName the name in the descriptor
   * @return {@code true} if the names match
   * @see cn.taketoday.util.StringUtils#simpleMatch(String, String)
   */
  protected boolean isMatch(String methodName, String mappedName) {
    return StringUtils.simpleMatch(mappedName, methodName);
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof NameMatchTransactionAttributeSource otherTas)) {
      return false;
    }
    return ObjectUtils.nullSafeEquals(this.nameMap, otherTas.nameMap);
  }

  @Override
  public int hashCode() {
    return NameMatchTransactionAttributeSource.class.hashCode();
  }

  @Override
  public String toString() {
    return getClass().getName() + ": " + this.nameMap;
  }

}
