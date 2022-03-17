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

package cn.taketoday.framework.web.servlet.filter;

import cn.taketoday.core.Ordered;
import jakarta.servlet.Filter;

/**
 * An {@link Ordered} {@link Filter}.
 *
 * @author Phillip Webb
 * @since 4.0
 */
public interface OrderedFilter extends Filter, Ordered {

  /**
   * Filters that wrap the servlet request should be ordered less than or equal to this.
   */
  int REQUEST_WRAPPER_FILTER_MAX_ORDER = 0;

}