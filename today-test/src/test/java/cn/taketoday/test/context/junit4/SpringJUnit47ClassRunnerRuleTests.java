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

package cn.taketoday.test.context.junit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import cn.taketoday.test.context.TestExecutionListeners;
import cn.taketoday.test.context.aot.DisabledInAotMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies support for JUnit 4.7 {@link Rule Rules} in conjunction with the
 * {@link InfraRunner}. The body of this test class is taken from the
 * JUnit 4.7 release notes.
 *
 * @author JUnit 4.7 Team
 * @author Sam Brannen
 * @since 4.0
 */
@RunWith(InfraRunner.class)
@TestExecutionListeners({})
// Since this test class does not load an ApplicationContext,
// this test class simply is not supported for AOT processing.
@DisabledInAotMode
public class SpringJUnit47ClassRunnerRuleTests {

  @Rule
  public TestName name = new TestName();

  @Test
  public void testA() {
    assertThat(name.getMethodName()).isEqualTo("testA");
  }

  @Test
  public void testB() {
    assertThat(name.getMethodName()).isEqualTo("testB");
  }
}
