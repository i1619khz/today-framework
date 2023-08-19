/*
 * Copyright 2017 - 2023 the original author or authors.
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

package cn.taketoday.jdbc.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import cn.taketoday.jdbc.core.test.ConstructorPerson;
import cn.taketoday.jdbc.core.test.ConstructorPersonWithGenerics;
import cn.taketoday.jdbc.core.test.ConstructorPersonWithSetters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Juergen Hoeller
 */
public class DataClassRowMapperTests extends AbstractRowMapperTests {

  @Test
  void staticQueryWithDataClass() throws Exception {
    Mock mock = new Mock();
    ConstructorPerson person = mock.getJdbcTemplate().queryForObject(
            "select name, age, birth_date, balance from people",
            new DataClassRowMapper<>(ConstructorPerson.class));
    verifyPerson(person);

    mock.verifyClosed();
  }

  @Test
  void staticQueryWithDataClassAndGenerics() throws Exception {
    Mock mock = new Mock();
    ConstructorPersonWithGenerics person = mock.getJdbcTemplate().queryForObject(
            "select name, age, birth_date, balance from people",
            new DataClassRowMapper<>(ConstructorPersonWithGenerics.class));
    assertThat(person.name()).isEqualTo("Bubba");
    assertThat(person.age()).isEqualTo(22L);
    assertThat(person.birthDate()).usingComparator(Date::compareTo).isEqualTo(new Date(1221222L));
    assertThat(person.balance()).containsExactly(new BigDecimal("1234.56"));

    mock.verifyClosed();
  }

  @Test
  void staticQueryWithDataClassAndSetters() throws Exception {
    Mock mock = new Mock(MockType.FOUR);
    ConstructorPersonWithSetters person = mock.getJdbcTemplate().queryForObject(
            "select name, age, birthdate, balance from people",
            new DataClassRowMapper<>(ConstructorPersonWithSetters.class));
    assertThat(person.name()).isEqualTo("BUBBA");
    assertThat(person.age()).isEqualTo(22L);
    assertThat(person.birthDate()).usingComparator(Date::compareTo).isEqualTo(new Date(1221222L));
    assertThat(person.balance()).isEqualTo(new BigDecimal("1234.56"));

    mock.verifyClosed();
  }

  @Test
  void staticQueryWithDataRecord() throws Exception {
    Mock mock = new Mock();
    RecordPerson person = mock.getJdbcTemplate().queryForObject(
            "select name, age, birth_date, balance from people",
            new DataClassRowMapper<>(RecordPerson.class));
    verifyPerson(person);

    mock.verifyClosed();
  }

  protected void verifyPerson(RecordPerson person) {
    assertThat(person.name()).isEqualTo("Bubba");
    assertThat(person.age()).isEqualTo(22L);
    assertThat(person.birth_date()).usingComparator(Date::compareTo).isEqualTo(new Date(1221222L));
    assertThat(person.balance()).isEqualTo(new BigDecimal("1234.56"));
    verifyPersonViaBeanWrapper(person);
  }

  record RecordPerson(String name, long age, Date birth_date, BigDecimal balance) {
  }

}
