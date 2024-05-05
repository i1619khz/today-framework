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

package cn.taketoday.framework.annotation;

import cn.taketoday.context.annotation.Condition;
import cn.taketoday.context.annotation.ConditionContext;
import cn.taketoday.context.annotation.config.AutoConfigurationMetadata;
import cn.taketoday.context.condition.ConditionMessage;
import cn.taketoday.context.condition.ConditionOutcome;
import cn.taketoday.context.condition.FilteringInfraCondition;
import cn.taketoday.core.Ordered;
import cn.taketoday.core.io.ResourceLoader;
import cn.taketoday.core.type.AnnotatedTypeMetadata;
import cn.taketoday.framework.ApplicationType;
import cn.taketoday.framework.annotation.ConditionalOnWebApplication.Type;
import cn.taketoday.web.server.context.GenericWebServerApplicationContext;
import cn.taketoday.web.server.support.ConfigurableNettyWebEnvironment;
import cn.taketoday.web.server.reactive.context.ConfigurableReactiveWebEnvironment;
import cn.taketoday.web.server.reactive.context.ReactiveWebApplicationContext;
import cn.taketoday.util.ClassUtils;

/**
 * {@link Condition} that checks for the presence or absence of
 * web.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @see ConditionalOnWebApplication
 * @see ConditionalOnNotWebApplication
 * @since 4.0
 */
class OnWebApplicationCondition extends FilteringInfraCondition implements Ordered {

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 20;
  }

  @Override
  protected ConditionOutcome[] getOutcomes(String[] configClasses, AutoConfigurationMetadata configMetadata) {
    ConditionOutcome[] outcomes = new ConditionOutcome[configClasses.length];
    for (int i = 0; i < outcomes.length; i++) {
      String autoConfigurationClass = configClasses[i];
      if (autoConfigurationClass != null) {
        outcomes[i] = getOutcome(
                configMetadata.get(autoConfigurationClass, "ConditionalOnWebApplication"));
      }
    }
    return outcomes;
  }

  private ConditionOutcome getOutcome(String type) {
    if (type == null) {
      return null;
    }
    ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnWebApplication.class);
    if (ConditionalOnWebApplication.Type.NETTY.name().equals(type)) {
      if (!ClassUtils.isPresent(ApplicationType.NETTY_INDICATOR_CLASS, getBeanClassLoader())) {
        return ConditionOutcome.noMatch(message.didNotFind("netty web application classes").atAll());
      }
    }
    else if (ConditionalOnWebApplication.Type.REACTIVE.name().equals(type)) {
      if (!ClassUtils.isPresent(ApplicationType.REACTOR_INDICATOR_CLASS, getBeanClassLoader())) {
        return ConditionOutcome.noMatch(message.didNotFind("reactive web application classes").atAll());
      }
    }
    if (!ClassUtils.isPresent(ApplicationType.NETTY_INDICATOR_CLASS, getBeanClassLoader())
            && !ClassUtils.isPresent(ApplicationType.REACTOR_INDICATOR_CLASS, getBeanClassLoader())) {
      return ConditionOutcome.noMatch(message.didNotFind("reactive or servlet, netty web application classes").atAll());
    }
    return null;
  }

  @Override
  public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
    boolean required = metadata.isAnnotated(ConditionalOnWebApplication.class.getName());
    ConditionOutcome outcome = isWebApplication(context, metadata, required);
    if (required && !outcome.isMatch()) {
      return ConditionOutcome.noMatch(outcome.getConditionMessage());
    }
    if (!required && outcome.isMatch()) {
      return ConditionOutcome.noMatch(outcome.getConditionMessage());
    }
    return ConditionOutcome.match(outcome.getConditionMessage());
  }

  private ConditionOutcome isWebApplication(ConditionContext context, AnnotatedTypeMetadata metadata, boolean required) {
    return switch (deduceType(metadata)) {
      case NETTY -> isNettyWebApplication(context);
      case REACTIVE -> isReactiveWebApplication(context);
      default -> isAnyApplication(context, required);
    };
  }

  private ConditionOutcome isAnyApplication(ConditionContext context, boolean required) {
    var message = ConditionMessage.forCondition(ConditionalOnWebApplication.class, required ? "(required)" : "");

    ConditionOutcome nettyOutcome = isNettyWebApplication(context);
    if (nettyOutcome.isMatch() && required) {
      return new ConditionOutcome(nettyOutcome.isMatch(), message.because(nettyOutcome.getMessage()));
    }
    ConditionOutcome reactiveOutcome = isReactiveWebApplication(context);
    if (reactiveOutcome.isMatch() && required) {
      return new ConditionOutcome(reactiveOutcome.isMatch(), message.because(reactiveOutcome.getMessage()));
    }
    return new ConditionOutcome(reactiveOutcome.isMatch() || nettyOutcome.isMatch(),
            message.because(nettyOutcome.getMessage())
                    .append("and").append(reactiveOutcome.getMessage()));
  }

  private ConditionOutcome isReactiveWebApplication(ConditionContext context) {
    var message = ConditionMessage.forCondition("");

    if (!ClassUtils.isPresent(ApplicationType.REACTOR_INDICATOR_CLASS, context.getClassLoader())) {
      return ConditionOutcome.noMatch(message.didNotFind("reactive web application classes").atAll());
    }

    if (context.getEnvironment() instanceof ConfigurableReactiveWebEnvironment) {
      return ConditionOutcome.match(message.foundExactly("ConfigurableReactiveWebEnvironment"));
    }

    ResourceLoader resourceLoader = context.getResourceLoader();
    if (resourceLoader instanceof ReactiveWebApplicationContext) {
      return ConditionOutcome.match(message.foundExactly("ReactiveWebApplicationContext"));
    }
    return ConditionOutcome.noMatch(message.because("not a reactive web application"));
  }

  /**
   * no Mock classes
   */
  private ConditionOutcome isNettyWebApplication(ConditionContext context) {
    var message = ConditionMessage.forCondition("");

    if (context.getEnvironment() instanceof ConfigurableNettyWebEnvironment) {
      return ConditionOutcome.match(message.foundExactly("NettyWebConfigurableEnvironment"));
    }

    ResourceLoader resourceLoader = context.getResourceLoader();
    if (resourceLoader instanceof GenericWebServerApplicationContext) {
      return ConditionOutcome.match(message.foundExactly("GenericWebServerApplicationContext"));
    }
    return ConditionOutcome.noMatch(message.because("not a netty web application"));
  }

  private Type deduceType(AnnotatedTypeMetadata metadata) {
    var annotation = metadata.getAnnotation(ConditionalOnWebApplication.class);
    if (annotation.isPresent()) {
      return annotation.getEnum("type", Type.class);
    }
    return Type.ANY;
  }

}
