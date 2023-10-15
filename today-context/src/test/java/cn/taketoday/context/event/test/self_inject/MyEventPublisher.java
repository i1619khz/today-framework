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

package cn.taketoday.context.event.test.self_inject;

import cn.taketoday.beans.factory.annotation.Autowired;
import cn.taketoday.context.ApplicationEventPublisher;
import cn.taketoday.stereotype.Component;

@Component
public class MyEventPublisher {

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  public void publishMyEvent(String message) {
    eventPublisher.publishEvent(new MyEvent(this, message));
  }

}
