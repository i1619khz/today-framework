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

package cn.taketoday.test.context.junit.jupiter.generics;

import org.junit.jupiter.api.Test;

import java.util.List;

import cn.taketoday.beans.factory.annotation.Autowired;
import cn.taketoday.test.context.junit.SpringJUnitJupiterTestSuite;
import cn.taketoday.test.context.junit.jupiter.ApplicationExtension;
import cn.taketoday.test.context.junit.jupiter.JUnitConfig;
import cn.taketoday.test.context.junit.jupiter.TestConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for integration tests that demonstrate support for
 * Java generics in JUnit Jupiter test classes when used with the Spring TestContext
 * Framework and the {@link ApplicationExtension}.
 *
 * <p>To run these tests in an IDE that does not have built-in support for the JUnit
 * Platform, simply run {@link SpringJUnitJupiterTestSuite} as a JUnit 4 test.
 *
 * @author Sam Brannen
 * @since 4.0
 */
@JUnitConfig(TestConfig.class)
abstract class GenericComicCharactersTests<T extends Character> {

  @Autowired
  T character;

  @Autowired
  List<T> characters;

  @Test
  void autowiredFields() {
    assertThat(this.character).as("Character should have been @Autowired by Spring").isNotNull();
    assertThat(this.character).as("character's name").extracting(Character::getName).isEqualTo(getExpectedName());
    assertThat(this.characters).as("Number of characters in context").hasSize(getExpectedNumCharacters());
  }

  @Test
  void autowiredParameterByTypeForSingleGenericBean(@Autowired T character) {
    assertThat(character).as("Character should have been @Autowired by Spring").isNotNull();
    assertThat(this.character).as("character's name").extracting(Character::getName).isEqualTo(getExpectedName());
  }

  abstract int getExpectedNumCharacters();

  abstract String getExpectedName();

}