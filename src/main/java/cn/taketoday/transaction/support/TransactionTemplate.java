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

package cn.taketoday.transaction.support;

import java.lang.reflect.UndeclaredThrowableException;

import cn.taketoday.beans.factory.InitializingBean;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.transaction.PlatformTransactionManager;
import cn.taketoday.transaction.TransactionDefinition;
import cn.taketoday.transaction.TransactionException;
import cn.taketoday.transaction.TransactionStatus;
import cn.taketoday.transaction.TransactionSystemException;

/**
 * Template class that simplifies programmatic transaction demarcation and
 * transaction exception handling.
 *
 * <p>The central method is {@link #execute}, supporting transactional code that
 * implements the {@link TransactionCallback} interface. This template handles
 * the transaction lifecycle and possible exceptions such that neither the
 * TransactionCallback implementation nor the calling code needs to explicitly
 * handle transactions.
 *
 * <p>Typical usage: Allows for writing low-level data access objects that use
 * resources such as JDBC DataSources but are not transaction-aware themselves.
 * Instead, they can implicitly participate in transactions handled by higher-level
 * application services utilizing this class, making calls to the low-level
 * services via an inner-class callback object.
 *
 * <p>Can be used within a service implementation via direct instantiation with
 * a transaction manager reference, or get prepared in an application context
 * and passed to services as bean reference. Note: The transaction manager should
 * always be configured as bean in the application context: in the first case given
 * to the service directly, in the second case given to the prepared template.
 *
 * <p>Supports setting the propagation behavior and the isolation level by name,
 * for convenient configuration in context definitions.
 *
 * @author Juergen Hoeller
 * @see #execute
 * @see #setTransactionManager
 * @see PlatformTransactionManager
 * @since 4.0
 */
@SuppressWarnings("serial")
public class TransactionTemplate extends DefaultTransactionDefinition
        implements TransactionOperations, InitializingBean {

  /** Logger available to subclasses. */
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Nullable
  private PlatformTransactionManager transactionManager;

  /**
   * Construct a new TransactionTemplate for bean usage.
   * <p>Note: The PlatformTransactionManager needs to be set before
   * any {@code execute} calls.
   *
   * @see #setTransactionManager
   */
  public TransactionTemplate() {
  }

  /**
   * Construct a new TransactionTemplate using the given transaction manager.
   *
   * @param transactionManager the transaction management strategy to be used
   */
  public TransactionTemplate(PlatformTransactionManager transactionManager) {
    this.transactionManager = transactionManager;
  }

  /**
   * Construct a new TransactionTemplate using the given transaction manager,
   * taking its default settings from the given transaction definition.
   *
   * @param transactionManager the transaction management strategy to be used
   * @param transactionDefinition the transaction definition to copy the
   * default settings from. Local properties can still be set to change values.
   */
  public TransactionTemplate(PlatformTransactionManager transactionManager, TransactionDefinition transactionDefinition) {
    super(transactionDefinition);
    this.transactionManager = transactionManager;
  }

  /**
   * Set the transaction management strategy to be used.
   */
  public void setTransactionManager(@Nullable PlatformTransactionManager transactionManager) {
    this.transactionManager = transactionManager;
  }

  /**
   * Return the transaction management strategy to be used.
   */
  @Nullable
  public PlatformTransactionManager getTransactionManager() {
    return this.transactionManager;
  }

  @Override
  public void afterPropertiesSet() {
    if (this.transactionManager == null) {
      throw new IllegalArgumentException("Property 'transactionManager' is required");
    }
  }

  @Override
  @Nullable
  public <T> T execute(TransactionCallback<T> action) throws TransactionException {
    Assert.state(this.transactionManager != null, "No PlatformTransactionManager set");

    if (this.transactionManager instanceof CallbackPreferringPlatformTransactionManager) {
      return ((CallbackPreferringPlatformTransactionManager) this.transactionManager).execute(this, action);
    }
    else {
      TransactionStatus status = this.transactionManager.getTransaction(this);
      T result;
      try {
        result = action.doInTransaction(status);
      }
      catch (RuntimeException | Error ex) {
        // Transactional code threw application exception -> rollback
        rollbackOnException(status, ex);
        throw ex;
      }
      catch (Throwable ex) {
        // Transactional code threw unexpected exception -> rollback
        rollbackOnException(status, ex);
        throw new UndeclaredThrowableException(ex, "TransactionCallback threw undeclared checked exception");
      }
      this.transactionManager.commit(status);
      return result;
    }
  }

  /**
   * Perform a rollback, handling rollback exceptions properly.
   *
   * @param status object representing the transaction
   * @param ex the thrown application exception or error
   * @throws TransactionException in case of a rollback error
   */
  private void rollbackOnException(TransactionStatus status, Throwable ex) throws TransactionException {
    Assert.state(this.transactionManager != null, "No PlatformTransactionManager set");

    logger.debug("Initiating transaction rollback on application exception", ex);
    try {
      this.transactionManager.rollback(status);
    }
    catch (TransactionSystemException ex2) {
      logger.error("Application exception overridden by rollback exception", ex);
      ex2.initApplicationException(ex);
      throw ex2;
    }
    catch (RuntimeException | Error ex2) {
      logger.error("Application exception overridden by rollback exception", ex);
      throw ex2;
    }
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return (this == other || (super.equals(other) && (!(other instanceof TransactionTemplate) ||
            getTransactionManager() == ((TransactionTemplate) other).getTransactionManager())));
  }

}
