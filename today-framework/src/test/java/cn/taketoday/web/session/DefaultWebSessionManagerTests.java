/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2021 All Rights Reserved.
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

package cn.taketoday.web.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import cn.taketoday.context.support.StandardApplicationContext;
import cn.taketoday.http.HttpCookie;
import cn.taketoday.web.MockRequestContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author TODAY 2021/4/30 22:24
 * @since 3.0
 */
class DefaultWebSessionManagerTests {

  @EnableWebSession
  static class AppConfig {

  }

  @Test
  public void testWebSession() {

    try (StandardApplicationContext applicationContext = new StandardApplicationContext()) {
      applicationContext.register(AppConfig.class);
      applicationContext.refresh();
      SessionManager sessionManager = applicationContext.getBean(SessionManager.class);
      MockRequestContext context = new MockRequestContext();

      WebSession noneExistingSession = sessionManager.getSession(context, false);

      assertThat(noneExistingSession).isNull();

      WebSession createdSession = sessionManager.getSession(context);
      assertThat(createdSession).isNotNull();

      // CookieTokenResolver
      CookieSessionIdResolver cookieTokenResolver = applicationContext.getBean(CookieSessionIdResolver.class);
      List<HttpCookie> responseCookies = context.responseCookies();
      String sessionId = createdSession.getId();
      HttpCookie sessionCookie = cookieTokenResolver.buildCookie(sessionId);

      assertThat(responseCookies).hasSize(1);
      assertThat(responseCookies.get(0)).isEqualTo(sessionCookie);

      // WebSessionStorage
      SessionRepository sessionStorage = applicationContext.getBean(SessionRepository.class);
      WebSession webSession = sessionStorage.retrieveSession(sessionId);

      assertThat(webSession).isEqualTo(createdSession);
      assertThat(sessionStorage.contains(sessionId)).isTrue();
      sessionStorage.removeSession(sessionId);
      assertThat(sessionStorage.contains(sessionId)).isFalse();

      // WebSessionStorage#store
      sessionStorage.storeSession(sessionId, createdSession);
      assertThat(sessionStorage.contains(sessionId)).isTrue();
    }

  }

}
