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

package cn.taketoday.web.client.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import cn.taketoday.aot.hint.RuntimeHints;
import cn.taketoday.aot.hint.predicate.ReflectionHintsPredicates;
import cn.taketoday.aot.hint.predicate.RuntimeHintsPredicates;
import cn.taketoday.http.client.ClientHttpRequestFactoryWrapper;
import cn.taketoday.http.client.HttpComponentsClientHttpRequestFactory;
import cn.taketoday.http.client.JettyClientHttpRequestFactory;
import cn.taketoday.http.client.OkHttp3ClientHttpRequestFactory;
import cn.taketoday.http.client.SimpleClientHttpRequestFactory;
import cn.taketoday.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2023/7/3 22:48
 */
class ClientHttpRequestFactoriesRuntimeHintsTests {

  @Test
  void shouldRegisterHints() {
    RuntimeHints hints = new RuntimeHints();
    new ClientHttpRequestFactoriesRuntimeHints().registerHints(hints, getClass().getClassLoader());
    ReflectionHintsPredicates reflection = RuntimeHintsPredicates.reflection();
    Field requestFactoryField = ReflectionUtils.findField(ClientHttpRequestFactoryWrapper.class,
            "requestFactory");
    assertThat(requestFactoryField).isNotNull();
    assertThat(reflection.onField(requestFactoryField)).accepts(hints);
  }

  @Test
  void shouldRegisterHttpComponentHints() {
    RuntimeHints hints = new RuntimeHints();
    new ClientHttpRequestFactoriesRuntimeHints().registerHints(hints, getClass().getClassLoader());
    ReflectionHintsPredicates reflection = RuntimeHintsPredicates.reflection();
    assertThat(reflection
            .onMethod(method(HttpComponentsClientHttpRequestFactory.class, "setConnectTimeout", int.class)))
            .accepts(hints);
  }

  @Test
  void shouldRegisterOkHttpHints() {
    RuntimeHints hints = new RuntimeHints();
    new ClientHttpRequestFactoriesRuntimeHints().registerHints(hints, getClass().getClassLoader());
    ReflectionHintsPredicates reflection = RuntimeHintsPredicates.reflection();
    assertThat(reflection.onMethod(method(OkHttp3ClientHttpRequestFactory.class, "setConnectTimeout", int.class)))
            .accepts(hints);
    assertThat(reflection.onMethod(method(OkHttp3ClientHttpRequestFactory.class, "setReadTimeout", int.class)))
            .accepts(hints);
    assertThat(hints.reflection().getTypeHint(OkHttp3ClientHttpRequestFactory.class).methods()).hasSize(2);
  }

  @Test
  void shouldRegisterJettyClientHints() {
    RuntimeHints hints = new RuntimeHints();
    new ClientHttpRequestFactoriesRuntimeHints().registerHints(hints, getClass().getClassLoader());
    ReflectionHintsPredicates reflection = RuntimeHintsPredicates.reflection();
    assertThat(reflection.onMethod(method(JettyClientHttpRequestFactory.class, "setConnectTimeout", int.class)))
            .accepts(hints);
    assertThat(reflection.onMethod(method(JettyClientHttpRequestFactory.class, "setReadTimeout", long.class)))
            .accepts(hints);
  }

  @Test
  void shouldRegisterSimpleHttpHints() {
    RuntimeHints hints = new RuntimeHints();
    new ClientHttpRequestFactoriesRuntimeHints().registerHints(hints, getClass().getClassLoader());
    ReflectionHintsPredicates reflection = RuntimeHintsPredicates.reflection();
    assertThat(reflection.onMethod(method(SimpleClientHttpRequestFactory.class, "setConnectTimeout", int.class)))
            .accepts(hints);
    assertThat(reflection.onMethod(method(SimpleClientHttpRequestFactory.class, "setReadTimeout", int.class)))
            .accepts(hints);
  }

  private static Method method(Class<?> target, String name, Class<?>... parameterTypes) {
    Method method = ReflectionUtils.findMethod(target, name, parameterTypes);
    assertThat(method).isNotNull();
    return method;
  }

}