/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © Harry Yang & 2017 - 2023 All Rights Reserved.
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

package org.test;

public class SampleApplication {

	public static void main(String[] args) {
		String foo = System.getProperty("foo");
		if (!"value 1".equals(foo)) {
			throw new IllegalStateException("foo system property mismatch (got [" + foo + "]");
		}
		String bar = System.getProperty("bar");
		if (!"value2".equals(bar)) {
			throw new IllegalStateException("bar system property mismatch (got [" + bar + "]");
		}
		String property1 = System.getProperty("property1");
		if (!"value1".equals(property1)) {
			throw new IllegalStateException("property1 system property mismatch (got [" + property1 + "]");
		}
		String property2 = System.getProperty("property2");
		if (!"".equals(property2)) {
			throw new IllegalStateException("property2 system property mismatch (got [" + property2 + "]");
		}
		String property3 = System.getProperty("property3");
		if (!"run-jvmargs".equals(property3)) {
			throw new IllegalStateException("property3 system property mismatch (got [" + property3 + "]");
		}
		System.out.println("I haz been run");
	}

}