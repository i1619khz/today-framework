/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2020 All Rights Reserved.
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
package cn.taketoday.jdbc.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import cn.taketoday.lang.Assert;
import cn.taketoday.jdbc.utils.DataSourceUtils;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;

/**
 * @author TODAY <br>
 * 2019-08-18 20:39
 */
public abstract class Executor implements BasicOperation {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  private DataSource dataSource; //data source

  private Integer maxRows;
  private Integer fetchSize;
  private Integer queryTimeout;

  /**
   * Get {@link DataSource} from this {@link Executor}
   *
   * @return {@link DataSource} object
   */
  public DataSource getDataSource() {
    return dataSource;
  }

  public final DataSource obtainDataSource() {
    final DataSource dataSource = getDataSource();
    Assert.state(dataSource != null, "Data source is required");
    return dataSource;
  }

  /**
   * Setting {@link DataSource} to this {@link Executor}
   *
   * @param dataSource
   *         Target {@link DataSource}
   *
   * @return This {@link Executor}
   */
  public Executor setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
    return this;
  }

  /**
   * Gives the JDBC driver a hint as to the number of rows that should be fetched
   * from the database when more rows are needed for <code>ResultSet</code>
   * objects generated by this <code>Statement</code>. If the value specified is
   * zero, then the hint is ignored. The default value is zero.
   *
   * @param fetchSize
   *         the number of rows to fetch
   *
   * @see java.sql.Statement#setFetchSize
   */
  public Executor setFetchSize(final Integer fetchSize) {
    this.fetchSize = fetchSize;
    return this;
  }

  /**
   * Retrieves the number of result set rows that is the default fetch size for
   * <code>ResultSet</code> objects generated from this <code>Statement</code>
   * object. If this <code>Statement</code> object has not set a fetch size by
   * calling the method <code>setFetchSize</code>, the return value is
   * implementation-specific.
   *
   * @see #setFetchSize
   */
  public Integer getFetchSize() {
    return this.fetchSize;
  }

  /**
   * Sets the limit for the maximum number of rows that any <code>ResultSet</code>
   * object generated by this <code>Statement</code> object can contain to the
   * given number. If the limit is exceeded, the excess rows are silently dropped.
   *
   * @param maxRows
   *         the new max rows limit; zero means there is no limit
   *
   * @see java.sql.Statement#setMaxRows
   */
  public Executor setMaxRows(final Integer maxRows) {
    this.maxRows = maxRows;
    return this;
  }

  /**
   * Retrieves the maximum number of rows that a <code>ResultSet</code> object
   * produced by this <code>Statement</code> object can contain. If this limit is
   * exceeded, the excess rows are silently dropped.
   *
   * @return the current maximum number of rows for a <code>ResultSet</code>
   * object produced by this <code>Statement</code> object; zero means
   * there is no limit
   *
   * @see #setMaxRows
   */
  public Integer getMaxRows() {
    return this.maxRows;
  }

  /**
   * Sets the number of seconds the driver will wait for a <code>Statement</code>
   * object to execute to the given number of seconds. By default there is no
   * limit on the amount of time allowed for a running statement to complete. If
   * the limit is exceeded, an <code>SQLTimeoutException</code> is thrown. A JDBC
   * driver must apply this limit to the <code>execute</code>,
   * <code>executeQuery</code> and <code>executeUpdate</code> methods.
   * <p>
   * <strong>Note:</strong> JDBC driver implementations may also apply this limit
   * to {@code ResultSet} methods (consult your driver vendor documentation for
   * details).
   * <p>
   * <strong>Note:</strong> In the case of {@code Statement} batching, it is
   * implementation defined as to whether the time-out is applied to individual
   * SQL commands added via the {@code addBatch} method or to the entire batch of
   * SQL commands invoked by the {@code executeBatch} method (consult your driver
   * vendor documentation for details).
   *
   * @param queryTimeout
   *         the new query timeout limit in seconds; zero means there is no
   *         limit
   *
   * @see java.sql.Statement#setQueryTimeout
   */
  public Executor setQueryTimeout(final Integer queryTimeout) {
    this.queryTimeout = queryTimeout;
    return this;
  }

  /**
   * Retrieves the number of seconds the driver will wait for a
   * <code>Statement</code> object to execute. If the limit is exceeded, a
   * <code>SQLException</code> is thrown.
   *
   * @return the current query timeout limit in seconds; zero means there is no
   * limit
   *
   * @see #setQueryTimeout
   */
  public Integer getQueryTimeout() {
    return this.queryTimeout;
  }

  /**
   * Setting the Statement's settings
   *
   * @param stmt
   *         Target {@link Statement}
   *
   * @throws SQLException
   *         If a database access error occurs
   */
  protected void applyStatementSettings(final Statement stmt) throws SQLException {

    final Integer fetchSize = getFetchSize();
    if (fetchSize != null) {
      stmt.setFetchSize(fetchSize);
    }

    final Integer maxRows = getMaxRows();
    if (maxRows != null) {
      stmt.setMaxRows(maxRows);
    }

    DataSourceUtils.applyTimeout(stmt, obtainDataSource(), getQueryTimeout());
  }

  protected void applyParameters(
          final PreparedStatement ps, final Object[] args) throws SQLException {
    int i = 1;
    for (final Object o : args) {
      ps.setObject(i++, o);
    }
  }

  protected void applyStatementSettings(
          final PreparedStatement stmt, final Object[] args) throws SQLException {
    applyStatementSettings(stmt);
    if (args != null) {
      applyParameters(stmt, args);
    }
  }

  // BasicOperation
  // -----------------------------

  @Override
  public <T> T execute(final ConnectionCallback<T> action) throws SQLException {
    final DataSource dataSource = obtainDataSource();
    final Connection con = DataSourceUtils.getConnection(dataSource);
    try {
      return action.doInConnection(con);
    }
    finally {
      DataSourceUtils.releaseConnection(con, dataSource);
    }
  }

  @Override
  public <T> T execute(final StatementCallback<T> action) throws SQLException {
    final class StatementConnectionCallback implements ConnectionCallback<T> {
      @Override
      public T doInConnection(final Connection con) throws SQLException {
        try (final Statement statement = con.createStatement()) {
          applyStatementSettings(statement);
          return action.doInStatement(statement);
        }
      }
    }

    return execute(new StatementConnectionCallback());
  }

  @Override
  public <T> T execute(final String sql, final PreparedStatementCallback<T> action) throws SQLException {
    final class PreparedConnectionCallback implements ConnectionCallback<T> {
      @Override
      public T doInConnection(final Connection con) throws SQLException {
        try (final PreparedStatement statement = con.prepareStatement(sql)) {
          applyStatementSettings(statement);
          return action.doInPreparedStatement(statement);
        }
      }
    }
    return execute(new PreparedConnectionCallback());
  }

  @Override
  public void execute(final String sql) throws SQLException {
    if (log.isDebugEnabled()) {
      log.debug("Executing SQL statement [{}]", sql);
    }
    final class ExecuteStatementCallback implements StatementCallback<Object> {
      @Override
      public Object doInStatement(Statement stmt) throws SQLException {
        return stmt.execute(sql);
      }
    }
    execute(new ExecuteStatementCallback());
  }

  @Override
  public <T> T execute(final String sql, final CallableStatementCallback<T> action) throws SQLException {
    final class CallableConnectionCallback implements ConnectionCallback<T> {
      @Override
      public T doInConnection(final Connection con) throws SQLException {
        return action.doInCallableStatement(con.prepareCall(sql));
      }
    }
    return execute(new CallableConnectionCallback());
  }

}
