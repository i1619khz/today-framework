/**
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © Today & 2017 - 2018 All Rights Reserved.
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cn.taketoday.jdbc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cn.taketoday.jdbc.Constant;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
/**
 * @author Today <br>
 * 
 *         2018-08-28 21:53
 */
public @interface ManyToMany {

    /**
     * the target class
     * 
     * @return
     */
    Class<?> value() default void.class;

    /**
     * the field
     * 
     * @return
     */
    String orderBy() default "";

    /**
     * 
     * @return
     */
    int batchSize() default 20;

    /**
     * @return {@link Constant#FETCH_JOIN}, {@link Constant#FETCH_SELECT},
     *         {@link Constant#FETCH_SUB_SELECT}
     */
    String fetch() default Constant.FETCH_JOIN;

    /**
     * default return RESTRICT
     * 
     * @return {@link Constant#CASCADE}, {@link Constant#NO_ACTION},
     *         {@link Constant#SET_NULL}, {@link Constant#RESTRICT}
     */
    String onDelete() default Constant.SET_NULL;

    /**
     * 
     * default return RESTRICT
     * 
     * @return {@link Constant#CASCADE}, {@link Constant#NO_ACTION},
     *         {@link Constant#SET_NULL}, {@link Constant#RESTRICT}
     */
    String onUpdate() default Constant.CASCADE;

}
