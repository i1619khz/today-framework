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

package demo;

import cn.taketoday.context.annotation.Import;
import cn.taketoday.web.config.EnableWebMvc;
import demo.config.AppConfig;
import cn.taketoday.framework.web.WebApplication;

/**
 * @author TODAY 2021/8/29 22:22
 */
@EnableWebMvc
//@EnableTomcatHandling
@Import(AppConfig.class)
public class DemoApplication {

  public static void main(String[] args) {
    WebApplication.run(DemoApplication.class, args);
  }

}