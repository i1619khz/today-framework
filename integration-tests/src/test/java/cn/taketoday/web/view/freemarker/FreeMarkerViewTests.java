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

package cn.taketoday.web.view.freemarker;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import cn.taketoday.context.ApplicationContextException;
import cn.taketoday.mock.web.MockContextImpl;
import cn.taketoday.web.LocaleResolver;
import cn.taketoday.web.i18n.AcceptHeaderLocaleResolver;
import cn.taketoday.web.mock.MockRequestContext;
import cn.taketoday.web.mock.MockUtils;
import cn.taketoday.web.mock.WebApplicationContext;
import cn.taketoday.web.mock.support.StaticWebApplicationContext;
import cn.taketoday.mock.web.HttpMockRequestImpl;
import cn.taketoday.mock.web.MockHttpResponseImpl;
import cn.taketoday.web.view.AbstractView;
import cn.taketoday.web.view.RedirectView;
import cn.taketoday.web.view.View;
import freemarker.template.Configuration;
import freemarker.template.SimpleHash;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import cn.taketoday.mock.api.http.HttpMockResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 14.03.2004
 */
public class FreeMarkerViewTests {

  @Test
  public void noFreeMarkerConfig() throws Exception {
    FreeMarkerView fv = new FreeMarkerView();

    WebApplicationContext wac = Mockito.mock(WebApplicationContext.class);
    given(wac.getBeansOfType(FreeMarkerConfig.class, true, false)).willReturn(new HashMap<>());
    given(wac.getMockContext()).willReturn(new MockContextImpl());

    fv.setUrl("anythingButNull");

    assertThatExceptionOfType(ApplicationContextException.class)
            .isThrownBy(() -> fv.setApplicationContext(wac))
            .withMessageContaining("FreeMarkerConfig");
  }

  @Test
  public void noTemplateName() throws Exception {
    FreeMarkerView fv = new FreeMarkerView();

    assertThatIllegalArgumentException()
            .isThrownBy(() -> fv.afterPropertiesSet())
            .withMessageContaining("url");
  }

  @Test
  public void validTemplateName() throws Exception {
    FreeMarkerView fv = new FreeMarkerView();

    WebApplicationContext wac = Mockito.mock(WebApplicationContext.class);
    MockContextImpl sc = new MockContextImpl();

    Map<String, FreeMarkerConfig> configs = new HashMap<>();
    FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
    configurer.setConfiguration(new TestConfiguration());
    configs.put("configurer", configurer);
    given(wac.getBeansOfType(FreeMarkerConfig.class, true, false)).willReturn(configs);
    given(wac.getMockContext()).willReturn(sc);

    fv.setUrl("templateName");
    fv.setApplicationContext(wac);

    HttpMockRequestImpl request = new HttpMockRequestImpl();
    request.addPreferredLocale(Locale.US);
    given(wac.getBean(LocaleResolver.BEAN_NAME)).willReturn(new AcceptHeaderLocaleResolver());
    given(wac.getBean(LocaleResolver.BEAN_NAME, LocaleResolver.class)).willReturn(new AcceptHeaderLocaleResolver());

    HttpMockResponse response = new MockHttpResponseImpl();

    Map<String, Object> model = new HashMap<>();
    model.put("myattr", "myvalue");
    MockRequestContext context = new MockRequestContext(wac, request, response);
    fv.render(model, context);
    assertThat(response.getContentType()).isEqualTo(AbstractView.DEFAULT_CONTENT_TYPE);
  }

  @Test
  public void keepExistingContentType() throws Exception {
    FreeMarkerView fv = new FreeMarkerView();

    WebApplicationContext wac = Mockito.mock(WebApplicationContext.class);
    MockContextImpl sc = new MockContextImpl();

    Map<String, FreeMarkerConfig> configs = new HashMap<>();
    FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
    configurer.setConfiguration(new TestConfiguration());
    configs.put("configurer", configurer);
    given(wac.getBeansOfType(FreeMarkerConfig.class, true, false)).willReturn(configs);
    given(wac.getMockContext()).willReturn(sc);
    given(wac.getBean(LocaleResolver.BEAN_NAME)).willReturn(new AcceptHeaderLocaleResolver());
    given(wac.getBean(LocaleResolver.BEAN_NAME, LocaleResolver.class)).willReturn(new AcceptHeaderLocaleResolver());

    fv.setUrl("templateName");
    fv.setApplicationContext(wac);

    HttpMockRequestImpl request = new HttpMockRequestImpl();
    request.addPreferredLocale(Locale.US);

    HttpMockResponse response = new MockHttpResponseImpl();
    response.setContentType("myContentType");

    Map<String, Object> model = new HashMap<>();
    model.put("myattr", "myvalue");
    MockRequestContext context = new MockRequestContext(wac, request, response);
    fv.render(model, context);

    assertThat(response.getContentType()).isEqualTo("myContentType");
  }

  @Test
  public void requestAttributeVisible() throws Exception {
    FreeMarkerView fv = new FreeMarkerView();

    WebApplicationContext wac = mock();
    MockContextImpl sc = new MockContextImpl();

    Map<String, FreeMarkerConfig> configs = new HashMap<>();
    FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
    configurer.setConfiguration(new TestConfiguration());
    configs.put("configurer", configurer);
    given(wac.getBeansOfType(FreeMarkerConfig.class, true, false)).willReturn(configs);
    given(wac.getMockContext()).willReturn(sc);

    fv.setUrl("templateName");
    fv.setApplicationContext(wac);

    HttpMockRequestImpl request = new HttpMockRequestImpl();
    request.addPreferredLocale(Locale.US);
    request.setAttribute(MockUtils.WEB_APPLICATION_CONTEXT_ATTRIBUTE, wac);
    HttpMockResponse response = new MockHttpResponseImpl();

    request.setAttribute("myattr", "myvalue");
    fv.render(null, new MockRequestContext(wac, request, response));
  }

  @Test
  public void freeMarkerViewResolver() throws Exception {
    MockContextImpl sc = new MockContextImpl();

    FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
    configurer.setConfiguration(new TestConfiguration());

    StaticWebApplicationContext wac = new StaticWebApplicationContext();
    wac.setMockContext(sc);
    wac.getBeanFactory().registerSingleton("configurer", configurer);
    wac.refresh();

    FreeMarkerViewResolver vr = new FreeMarkerViewResolver("prefix_", "_suffix");
    vr.setApplicationContext(wac);

    View view = vr.resolveViewName("test", Locale.CANADA);
    assertThat(view.getClass()).as("Correct view class").isEqualTo(FreeMarkerView.class);
    assertThat(((FreeMarkerView) view).getUrl()).as("Correct URL").isEqualTo("prefix_test_suffix");

    view = vr.resolveViewName("non-existing", Locale.CANADA);
    assertThat(view).isNull();

    view = vr.resolveViewName("redirect:myUrl", Locale.getDefault());
    assertThat(view.getClass()).as("Correct view class").isEqualTo(RedirectView.class);
    assertThat(((RedirectView) view).getUrl()).as("Correct URL").isEqualTo("myUrl");
  }

  private class TestConfiguration extends Configuration {

    TestConfiguration() {
      super(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
    }

    @Override
    public Template getTemplate(String name, final Locale locale) throws IOException {
      if (name.equals("templateName") || name.equals("prefix_test_suffix")) {
        return new Template(name, new StringReader("test"), this) {
          @Override
          public void process(Object model, Writer writer) throws TemplateException, IOException {
            assertThat(locale).isEqualTo(Locale.US);
            assertThat(model instanceof SimpleHash).isTrue();
            SimpleHash fmModel = (SimpleHash) model;
            Assertions.assertThat(fmModel.get("myattr").toString()).isEqualTo("myvalue");
          }
        };
      }
      else {
        throw new FileNotFoundException();
      }
    }
  }

}
