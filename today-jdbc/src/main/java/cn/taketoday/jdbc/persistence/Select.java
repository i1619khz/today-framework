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


package cn.taketoday.jdbc.persistence;

import cn.taketoday.jdbc.persistence.dialect.Platform;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.StringUtils;

/**
 * A simple SQL <tt>SELECT</tt> statement
 *
 * @author Gavin King
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 */
public class Select {

  protected CharSequence selectClause;

  protected CharSequence fromClause;

  @Nullable
  protected CharSequence outerJoinsAfterFrom;

  @Nullable
  protected CharSequence whereClause;

  protected CharSequence outerJoinsAfterWhere;

  @Nullable
  protected CharSequence orderByClause;

  protected CharSequence groupByClause;

  @Nullable
  protected CharSequence comment;

  protected boolean forUpdate;

  public final Platform platform;

  private int guesstimatedBufferSize = 20;

  public Select(Platform platform) {
    this.platform = platform;
  }

  /**
   * Construct an SQL <tt>SELECT</tt> statement from the given clauses
   */
  public String toStatementString() {
    StringBuilder buf = new StringBuilder(guesstimatedBufferSize);
    if (StringUtils.isNotEmpty(comment)) {
      buf.append("/* ").append(Platform.escapeComment(comment)).append(" */ ");
    }

    buf.append("SELECT ").append(selectClause)
            .append(" FROM ").append(fromClause);

    if (outerJoinsAfterFrom != null) {
      buf.append(outerJoinsAfterFrom);
    }

    if (StringUtils.isNotEmpty(whereClause) || StringUtils.isNotEmpty(outerJoinsAfterWhere)) {
      buf.append(" WHERE ");
      // the outerJoinsAfterWhere needs to come before where clause to properly
      // handle dynamic filters
      if (StringUtils.isNotEmpty(outerJoinsAfterWhere)) {
        buf.append(outerJoinsAfterWhere);
        if (StringUtils.isNotEmpty(whereClause)) {
          buf.append(" AND ");
        }
      }
      if (StringUtils.isNotEmpty(whereClause)) {
        buf.append(whereClause);
      }
    }

    if (StringUtils.isNotEmpty(groupByClause)) {
      buf.append(" group by ").append(groupByClause);
    }

    if (orderByClause != null) {
      buf.append(" order by ").append(orderByClause);
    }

    if (forUpdate) {
      buf.append(platform.getForUpdateString());
    }

    return buf.toString();
  }

  /**
   * Sets the fromClause.
   *
   * @param fromClause The fromClause to set
   */
  public Select setFromClause(CharSequence fromClause) {
    this.fromClause = fromClause;
    this.guesstimatedBufferSize += fromClause.length();
    return this;
  }

  public Select setFromClause(String tableName, String alias) {
    this.fromClause = tableName + ' ' + alias;
    this.guesstimatedBufferSize += fromClause.length();
    return this;
  }

  public Select setOrderByClause(CharSequence orderByClause) {
    this.orderByClause = orderByClause;
    this.guesstimatedBufferSize += orderByClause.length();
    return this;
  }

  public Select setGroupByClause(CharSequence groupByClause) {
    this.groupByClause = groupByClause;
    this.guesstimatedBufferSize += groupByClause.length();
    return this;
  }

  public Select setOuterJoins(CharSequence outerJoinsAfterFrom, String outerJoinsAfterWhere) {
    this.outerJoinsAfterFrom = outerJoinsAfterFrom;

    // strip off any leading 'and' token
    String tmpOuterJoinsAfterWhere = outerJoinsAfterWhere.trim();
    if (tmpOuterJoinsAfterWhere.startsWith("and")) {
      tmpOuterJoinsAfterWhere = tmpOuterJoinsAfterWhere.substring(4);
    }
    this.outerJoinsAfterWhere = tmpOuterJoinsAfterWhere;

    this.guesstimatedBufferSize += outerJoinsAfterFrom.length() + outerJoinsAfterWhere.length();
    return this;
  }

  /**
   * Sets the selectClause.
   *
   * @param selectClause The selectClause to set
   */
  public Select setSelectClause(CharSequence selectClause) {
    this.selectClause = selectClause;
    this.guesstimatedBufferSize += selectClause.length();
    return this;
  }

  public Select setSelectClause(SelectFragment selectFragment) {
    setSelectClause(selectFragment.toFragmentString().substring(2));
    return this;
  }

  /**
   * Sets the whereClause.
   *
   * @param whereClause The whereClause to set
   */
  public Select setWhereClause(CharSequence whereClause) {
    this.whereClause = whereClause;
    this.guesstimatedBufferSize += whereClause.length();
    return this;
  }

  public Select setComment(CharSequence comment) {
    this.comment = comment;
    this.guesstimatedBufferSize += comment.length();
    return this;
  }

  public Select setForUpdate(boolean forUpdate) {
    this.forUpdate = forUpdate;
    return this;
  }

}
