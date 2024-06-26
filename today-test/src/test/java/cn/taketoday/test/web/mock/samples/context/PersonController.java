/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2023 All Rights Reserved.
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

package cn.taketoday.test.web.mock.samples.context;

import cn.taketoday.test.web.Person;
import cn.taketoday.web.annotation.GetMapping;
import cn.taketoday.web.annotation.PathVariable;
import cn.taketoday.web.annotation.RequestMapping;
import cn.taketoday.web.annotation.RestController;

@RestController
@RequestMapping("/person")
public class PersonController {

  private final PersonDao personDao;

  public PersonController(PersonDao personDao) {
    this.personDao = personDao;
  }

  @GetMapping("/{id}")
  public Person getPerson(@PathVariable long id) {
    return this.personDao.getPerson(id);
  }

}
