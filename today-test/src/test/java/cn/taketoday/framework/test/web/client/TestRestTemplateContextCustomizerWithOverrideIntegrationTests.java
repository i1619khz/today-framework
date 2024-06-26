/*
 * Copyright 2017 - 2024 the original author or authors.
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
 * along with this program. If not, see [https://www.gnu.org/licenses/]
 */

package cn.taketoday.framework.test.web.client;

import org.junit.jupiter.api.Test;

import cn.taketoday.beans.factory.annotation.Autowired;
import cn.taketoday.context.annotation.Bean;
import cn.taketoday.context.annotation.Configuration;
import cn.taketoday.context.annotation.Import;
import cn.taketoday.framework.test.RandomPortWebServerConfig;
import cn.taketoday.framework.test.context.InfraTest;
import cn.taketoday.framework.test.context.InfraTest.WebEnvironment;
import cn.taketoday.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TestRestTemplateContextCustomizer} with a custom
 * {@link TestRestTemplate} bean.
 *
 * @author Phillip Webb
 */
@InfraTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
class TestRestTemplateContextCustomizerWithOverrideIntegrationTests {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void test() {
    assertThat(this.restTemplate).isInstanceOf(CustomTestRestTemplate.class);
  }

  @Configuration(proxyBeanMethods = false)
  @Import({ NoTestRestTemplateBeanChecker.class, RandomPortWebServerConfig.class })
  static class TestConfig {

    @Bean
    TestRestTemplate template() {
      return new CustomTestRestTemplate();
    }

  }

  static class CustomTestRestTemplate extends TestRestTemplate {

  }

}
