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

package cn.taketoday.test.web.servlet.result;

import org.junit.jupiter.api.Test;

import cn.taketoday.test.web.servlet.StubMvcResult;
import cn.taketoday.web.servlet.FlashMap;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Craig Walls
 */
public class FlashAttributeResultMatchersTests {

	@Test
	public void attributeExists() throws Exception {
		new FlashAttributeResultMatchers().attributeExists("good").match(getStubMvcResult());
	}

	@Test
	public void attributeExists_doesntExist() throws Exception {
		assertThatExceptionOfType(AssertionError.class).isThrownBy(() ->
				new FlashAttributeResultMatchers().attributeExists("bad").match(getStubMvcResult()));
	}

	@Test
	public void attribute() throws Exception {
		new FlashAttributeResultMatchers().attribute("good", "good").match(getStubMvcResult());
	}

	@Test
	public void attribute_incorrectValue() throws Exception {
		assertThatExceptionOfType(AssertionError.class).isThrownBy(() ->
				new FlashAttributeResultMatchers().attribute("good", "not good").match(getStubMvcResult()));
	}

	private StubMvcResult getStubMvcResult() {
		FlashMap flashMap = new FlashMap();
		flashMap.put("good", "good");
		StubMvcResult mvcResult = new StubMvcResult(null, null, null, null, null, flashMap, null);
		return mvcResult;
	}

}