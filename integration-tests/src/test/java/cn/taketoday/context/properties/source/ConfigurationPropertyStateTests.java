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

package cn.taketoday.context.properties.source;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link ConfigurationPropertyState}.
 *
 * @author Phillip Webb
 */
class ConfigurationPropertyStateTests {

  @Test
  void searchWhenIterableIsNullShouldThrowException() {
    assertThatIllegalArgumentException().isThrownBy(() -> ConfigurationPropertyState.search(null, (e) -> true))
            .withMessageContaining("Source must not be null");
  }

  @Test
  void searchWhenPredicateIsNullShouldThrowException() {
    assertThatIllegalArgumentException()
            .isThrownBy(() -> ConfigurationPropertyState.search(Collections.emptyList(), null))
            .withMessageContaining("Predicate must not be null");
  }

  @Test
  void searchWhenContainsItemShouldReturnPresent() {
    List<String> source = Arrays.asList("a", "b", "c");
    ConfigurationPropertyState result = ConfigurationPropertyState.search(source, "b"::equals);
    assertThat(result).isEqualTo(ConfigurationPropertyState.PRESENT);
  }

  @Test
  void searchWhenContainsNoItemShouldReturnAbsent() {
    List<String> source = Arrays.asList("a", "x", "c");
    ConfigurationPropertyState result = ConfigurationPropertyState.search(source, "b"::equals);
    assertThat(result).isEqualTo(ConfigurationPropertyState.ABSENT);
  }

}