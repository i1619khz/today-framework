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

package cn.taketoday.expression;

import java.util.List;

import cn.taketoday.core.TypeDescriptor;
import cn.taketoday.lang.Nullable;

/**
 * A method resolver attempts to locate a method and returns a command executor that can be
 * used to invoke that method. The command executor will be cached, but if it 'goes stale'
 * the resolvers will be called again.
 *
 * @author Andy Clement
 * @since 4.0
 */
public interface MethodResolver {

  /**
   * Within the supplied context determine a suitable method on the supplied object that
   * can handle the specified arguments. Return a {@link MethodExecutor} that can be used
   * to invoke that method, or {@code null} if no method could be found.
   *
   * @param context the current evaluation context
   * @param targetObject the object upon which the method is being called
   * @param argumentTypes the arguments that the constructor must be able to handle
   * @return a MethodExecutor that can invoke the method, or null if the method cannot be found
   */
  @Nullable
  MethodExecutor resolve(EvaluationContext context, Object targetObject, String name,
          List<TypeDescriptor> argumentTypes) throws AccessException;

}
