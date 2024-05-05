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

package cn.taketoday.web.view.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import cn.taketoday.context.annotation.AnnotationConfigApplicationContext;
import cn.taketoday.context.annotation.Bean;
import cn.taketoday.context.annotation.Configuration;
import cn.taketoday.web.servlet.ServletRequestContext;
import cn.taketoday.web.servlet.ServletUtils;
import cn.taketoday.web.servlet.WebApplicationContext;
import cn.taketoday.mock.web.MockHttpServletRequest;
import cn.taketoday.mock.web.MockHttpServletResponse;
import cn.taketoday.mock.web.MockServletContext;
import cn.taketoday.mock.api.ServletContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for String templates running on Jython.
 *
 * @author Sebastien Deleuze
 */
public class JythonScriptTemplateTests {

  private WebApplicationContext webAppContext;

  private ServletContext servletContext;

  @BeforeEach
  public void setup() {
    this.webAppContext = Mockito.mock(WebApplicationContext.class);
    this.servletContext = new MockServletContext();
    this.servletContext.setAttribute(ServletUtils.WEB_APPLICATION_CONTEXT_ATTRIBUTE, this.webAppContext);
  }

  @Test
  public void renderTemplate() throws Exception {
    Map<String, Object> model = new HashMap<>();
    model.put("title", "Layout example");
    model.put("body", "This is the body");
    String url = "cn/taketoday/web/view/script/jython/template.html";
    MockHttpServletResponse response = render(url, model);
    assertThat(response.getContentAsString()).isEqualTo("<html><head><title>Layout example</title></head><body><p>This is the body</p></body></html>");
  }

  private MockHttpServletResponse render(String viewUrl, Map<String, Object> model) throws Exception {
    ScriptTemplateView view = createViewWithUrl(viewUrl);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpServletRequest request = new MockHttpServletRequest();
    view.renderMergedOutputModel(model, new ServletRequestContext(webAppContext, request, response));
    return response;
  }

  private ScriptTemplateView createViewWithUrl(String viewUrl) throws Exception {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.register(ScriptTemplatingConfiguration.class);
    ctx.refresh();

    ScriptTemplateView view = new ScriptTemplateView();
    view.setApplicationContext(ctx);
    view.setUrl(viewUrl);
    view.afterPropertiesSet();
    return view;
  }

  @Configuration
  static class ScriptTemplatingConfiguration {

    @Bean
    public ScriptTemplateConfigurer jythonConfigurer() {
      ScriptTemplateConfigurer configurer = new ScriptTemplateConfigurer();
      configurer.setScripts("cn/taketoday/web/view/script/jython/render.py");
      configurer.setEngineName("jython");
      configurer.setRenderFunction("render");
      return configurer;
    }
  }

}
