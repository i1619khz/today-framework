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

package cn.taketoday.test.web.mock.samples.client.standalone;

import org.junit.jupiter.api.Test;

import java.util.List;

import cn.taketoday.context.ApplicationContext;
import cn.taketoday.http.HttpHeaders;
import cn.taketoday.http.client.reactive.ClientHttpConnector;
import cn.taketoday.mock.web.HttpMockRequestImpl;
import cn.taketoday.stereotype.Controller;
import cn.taketoday.test.web.reactive.server.WebTestClient;
import cn.taketoday.test.web.reactive.server.WebTestClientConfigurer;
import cn.taketoday.test.web.mock.client.MockMvcHttpConnector;
import cn.taketoday.test.web.mock.client.MockMvcWebTestClient;
import cn.taketoday.test.web.mock.request.RequestPostProcessor;
import cn.taketoday.test.web.mock.setup.ConfigurableMockMvcBuilder;
import cn.taketoday.test.web.mock.setup.MockMvcConfigurerAdapter;
import cn.taketoday.web.annotation.RequestMapping;
import cn.taketoday.web.annotation.ResponseBody;

import static cn.taketoday.test.web.mock.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

/**
 * {@link MockMvcWebTestClient} equivalent of the MockMvc
 * {@link cn.taketoday.test.web.mock.samples.standalone.FrameworkExtensionTests}.
 *
 * @author Rossen Stoyanchev
 */
public class FrameworkExtensionTests {

  private final WebTestClient client =
          MockMvcWebTestClient.bindToController(new SampleController())
                  .apply(defaultSetup())
                  .build();

  @Test
  public void fooHeader() {
    this.client.mutateWith(headers().foo("a=b"))
            .get().uri("/")
            .exchange()
            .expectBody(String.class).isEqualTo("Foo");
  }

  @Test
  public void barHeader() {
    this.client.mutateWith(headers().bar("a=b"))
            .get().uri("/")
            .exchange()
            .expectBody(String.class).isEqualTo("Bar");
  }

  private static TestMockMvcConfigurer defaultSetup() {
    return new TestMockMvcConfigurer();
  }

  private static TestWebTestClientConfigurer headers() {
    return new TestWebTestClientConfigurer();
  }

  /**
   * Test WebTestClientConfigurer that re-creates the MockMvcHttpConnector
   * with a {@code TestRequestPostProcessor}.
   */
  private static class TestWebTestClientConfigurer implements WebTestClientConfigurer {

    private final TestRequestPostProcessor requestPostProcessor = new TestRequestPostProcessor();

    public TestWebTestClientConfigurer foo(String value) {
      this.requestPostProcessor.foo(value);
      return this;
    }

    public TestWebTestClientConfigurer bar(String value) {
      this.requestPostProcessor.bar(value);
      return this;
    }

    @Override
    public void afterConfigurerAdded(WebTestClient.Builder builder, ClientHttpConnector connector) {
      if (connector instanceof MockMvcHttpConnector mockMvcConnector) {
        builder.clientConnector(mockMvcConnector.with(List.of(this.requestPostProcessor)));
      }
    }
  }

  /**
   * Test {@code RequestPostProcessor} for custom headers.
   */
  private static class TestRequestPostProcessor implements RequestPostProcessor {

    private final HttpHeaders headers = HttpHeaders.forWritable();

    public TestRequestPostProcessor foo(String value) {
      this.headers.add("Foo", value);
      return this;
    }

    public TestRequestPostProcessor bar(String value) {
      this.headers.add("Bar", value);
      return this;
    }

    @Override
    public HttpMockRequestImpl postProcessRequest(HttpMockRequestImpl request) {
      for (String headerName : this.headers.keySet()) {
        request.addHeader(headerName, this.headers.get(headerName));
      }
      return request;
    }
  }

  /**
   * Test {@code MockMvcConfigurer}.
   */
  private static class TestMockMvcConfigurer extends MockMvcConfigurerAdapter {

    @Override
    public void afterConfigurerAdded(ConfigurableMockMvcBuilder<?> builder) {
      builder.alwaysExpect(status().isOk());
    }

    @Override
    public RequestPostProcessor beforeMockMvcCreated(
            ConfigurableMockMvcBuilder<?> builder, ApplicationContext context) {

      return request -> {
        request.setUserPrincipal(mock());
        return request;
      };
    }
  }

  @Controller
  @RequestMapping("/")
  private static class SampleController {

    @RequestMapping(headers = "Foo")
    @ResponseBody
    public String handleFoo() {
      return "Foo";
    }

    @RequestMapping(headers = "Bar")
    @ResponseBody
    public String handleBar() {
      return "Bar";
    }
  }

}
