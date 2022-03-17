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

package cn.taketoday.core.reflect;

import java.lang.reflect.Method;

/**
 * @author TODAY
 * 2020/9/12 15:20
 */
final class ReadOnlyGetterMethodPropertyAccessor extends ReadOnlyPropertyAccessor {

  private final GetterMethod readMethod;

  ReadOnlyGetterMethodPropertyAccessor(final GetterMethod readMethod) {
    this.readMethod = readMethod;
  }

  @Override
  public Object get(final Object obj) {
    return readMethod.get(obj);
  }

  @Override
  public Method getReadMethod() {
    return readMethod.getReadMethod();
  }

}