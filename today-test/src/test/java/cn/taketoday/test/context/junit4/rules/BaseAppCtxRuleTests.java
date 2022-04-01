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

package cn.taketoday.test.context.junit4.rules;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import cn.taketoday.beans.factory.annotation.Autowired;
import cn.taketoday.context.annotation.Bean;
import cn.taketoday.context.annotation.Configuration;
import cn.taketoday.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for integration tests involving Spring {@code ApplicationContexts}
 * in conjunction with {@link ApplicationClassRule} and {@link ApplicationMethodRule}.
 *
 * <p>The goal of this class and its subclasses is to ensure that Rule-based
 * configuration can be inherited without requiring {@link ApplicationClassRule}
 * or {@link ApplicationMethodRule} to be redeclared on subclasses.
 *
 * @author Sam Brannen
 * @since 4.0
 * @see Subclass1AppCtxRuleTests
 * @see Subclass2AppCtxRuleTests
 */
@ContextConfiguration
public class BaseAppCtxRuleTests {

	@ClassRule
	public static final ApplicationClassRule applicationClassRule = new ApplicationClassRule();

	@Rule
	public final ApplicationMethodRule applicationMethodRule = new ApplicationMethodRule();

	@Autowired
	private String foo;


	@Test
	public void foo() {
		assertThat(foo).isEqualTo("foo");
	}


	@Configuration
	static class Config {

		@Bean
		public String foo() {
			return "foo";
		}
	}
}