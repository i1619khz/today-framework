/*
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
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

package cn.taketoday.beans.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author TODAY 2021/8/28 20:48
 */
class BeanConstructorTests {

  static class BeanConstructorTestsBean {
    final int code;

    BeanConstructorTestsBean(int code) {
      this.code = code;
    }
  }

  @Test
  void fromFunction() {
    FunctionConstructor<BeanConstructorTestsBean> constructor
            = BeanConstructor.fromFunction(objects -> new BeanConstructorTestsBean(1000));

    BeanConstructorTestsBean testsBean = constructor.newInstance();

    assertThat(testsBean).isNotNull();
    assertThat(testsBean.code).isEqualTo(1000);
    assertThat(testsBean).isNotEqualTo(constructor.newInstance());

  }

  @Test
  void fromSupplier() {

    SupplierConstructor<BeanConstructorTestsBean> constructor =
            BeanConstructor.fromSupplier(() -> new BeanConstructorTestsBean(1000));

    BeanConstructorTestsBean testsBean = constructor.newInstance();
    assertThat(testsBean).isNotNull();
    assertThat(testsBean.code).isEqualTo(1000);
    assertThat(testsBean).isNotEqualTo(constructor.newInstance());

  }
}
