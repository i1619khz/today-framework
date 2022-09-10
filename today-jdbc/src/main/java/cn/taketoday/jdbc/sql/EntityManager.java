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

package cn.taketoday.jdbc.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import cn.taketoday.dao.DataAccessException;
import cn.taketoday.lang.Nullable;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2022/8/16 22:47
 */
public interface EntityManager {

  /**
   * persist an entity to underlying repository
   *
   * @param entity entity instance
   * @throws IllegalArgumentException if the instance is not an entity
   */
  void persist(Object entity) throws DataAccessException;

  /**
   * persist an entity to underlying repository
   *
   * @param entity entity instance
   * @param returnGeneratedKeys a flag indicating whether auto-generated keys should be returned;
   * @see PreparedStatement
   * @see Connection#prepareStatement(String, int)
   */
  void persist(Object entity, boolean returnGeneratedKeys) throws DataAccessException;

  /**
   * persist entities to underlying repository
   *
   * @param entities entities instances
   */
  void persist(Iterable<Object> entities) throws DataAccessException;

  /**
   * persist entities to underlying repository
   *
   * @param returnGeneratedKeys a flag indicating whether auto-generated keys should be returned;
   * @param entities entities instances
   */
  void persist(Iterable<Object> entities, boolean returnGeneratedKeys) throws DataAccessException;

  /**
   * Merge the state of the given entity into underlying repository
   *
   * @param entity entity instance
   * @throws IllegalArgumentException if instance is not an
   * entity or is a removed entity
   */
  void update(Object entity);

  void updateById(Object entity);

  void delete(Class<?> entityClass, Object id);

  void delete(Object entity);

  @Nullable
  <T> T findById(Class<T> entityClass, Object id) throws DataAccessException;

  <T> T findFirst(T entity) throws DataAccessException;

  <T> List<T> findFirst(Class<T> entityClass, Object query) throws DataAccessException;

  <T> List<T> find(T entity) throws DataAccessException;

  <T> List<T> find(Class<T> entityClass, Object params) throws DataAccessException;

  <T> void iterate(Class<T> entityClass, Object params, Consumer<T> entityConsumer) throws DataAccessException;

  <T> Iterator<T> iterate(Class<T> entityClass, Object params) throws DataAccessException;

  <T> void iterate(Class<T> entityClass, @Nullable QueryCondition conditions, Consumer<T> entityConsumer) throws DataAccessException;

  <T> Iterator<T> iterate(Class<T> entityClass, @Nullable QueryCondition conditions) throws DataAccessException;
}