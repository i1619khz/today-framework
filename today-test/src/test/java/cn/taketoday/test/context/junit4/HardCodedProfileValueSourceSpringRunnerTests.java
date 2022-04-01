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

package cn.taketoday.test.context.junit4;

import org.junit.BeforeClass;
import cn.taketoday.test.annotation.ProfileValueSource;
import cn.taketoday.test.annotation.ProfileValueSourceConfiguration;

/**
 * <p>
 * Verifies proper handling of JUnit's {@link org.junit.Ignore &#064;Ignore} and
 * Spring's {@link cn.taketoday.test.annotation.IfProfileValue
 * &#064;IfProfileValue} and {@link ProfileValueSourceConfiguration
 * &#064;ProfileValueSourceConfiguration} (with an
 * <em>explicit, custom defined {@link ProfileValueSource}</em>) annotations in
 * conjunction with the {@link Runner}.
 * </p>
 *
 * @author Sam Brannen
 * @since 2.5
 * @see EnabledAndIgnoredSpringRunnerTests
 */
@ProfileValueSourceConfiguration(HardCodedProfileValueSourceSpringRunnerTests.HardCodedProfileValueSource.class)
public class HardCodedProfileValueSourceSpringRunnerTests extends EnabledAndIgnoredSpringRunnerTests {

	@BeforeClass
	public static void setProfileValue() {
		numTestsExecuted = 0;
		// Set the system property to something other than VALUE as a sanity
		// check.
		System.setProperty(NAME, "999999999999");
	}


	public static class HardCodedProfileValueSource implements ProfileValueSource {

		@Override
		public String get(final String key) {
			return (key.equals(NAME) ? VALUE : null);
		}
	}
}