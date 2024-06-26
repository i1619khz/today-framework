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

package cn.taketoday.annotation.config.context;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

import cn.taketoday.aot.hint.RuntimeHints;
import cn.taketoday.aot.hint.RuntimeHintsRegistrar;
import cn.taketoday.context.MessageSource;
import cn.taketoday.context.annotation.ConditionContext;
import cn.taketoday.context.annotation.Conditional;
import cn.taketoday.context.annotation.ImportRuntimeHints;
import cn.taketoday.context.annotation.Lazy;
import cn.taketoday.context.annotation.config.AutoConfigureOrder;
import cn.taketoday.context.annotation.config.DisableDIAutoConfiguration;
import cn.taketoday.context.annotation.config.EnableAutoConfiguration;
import cn.taketoday.context.condition.ConditionMessage;
import cn.taketoday.context.condition.ConditionOutcome;
import cn.taketoday.context.condition.ConditionalOnMissingBean;
import cn.taketoday.context.condition.InfraCondition;
import cn.taketoday.context.condition.SearchStrategy;
import cn.taketoday.context.properties.EnableConfigurationProperties;
import cn.taketoday.context.support.AbstractApplicationContext;
import cn.taketoday.context.support.ResourceBundleMessageSource;
import cn.taketoday.core.Ordered;
import cn.taketoday.core.io.PathMatchingPatternResourceLoader;
import cn.taketoday.core.io.Resource;
import cn.taketoday.core.type.AnnotatedTypeMetadata;
import cn.taketoday.lang.Nullable;
import cn.taketoday.stereotype.Component;
import cn.taketoday.util.ConcurrentReferenceHashMap;
import cn.taketoday.util.StringUtils;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link MessageSource}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Eddú Meléndez
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
@Lazy
@DisableDIAutoConfiguration
@ConditionalOnMissingBean(name = AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME, search = SearchStrategy.CURRENT)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@Conditional(MessageSourceAutoConfiguration.ResourceBundleCondition.class)
@EnableConfigurationProperties(MessageSourceProperties.class)
@ImportRuntimeHints(MessageSourceAutoConfiguration.Hints.class)
public class MessageSourceAutoConfiguration {

  @Component
  public static MessageSource messageSource(MessageSourceProperties properties) {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    if (StringUtils.hasText(properties.getBasename())) {
      messageSource.setBasenames(
              StringUtils.commaDelimitedListToStringArray(
                      StringUtils.trimAllWhitespace(properties.getBasename())
              )
      );
    }
    if (properties.getEncoding() != null) {
      messageSource.setDefaultEncoding(properties.getEncoding().name());
    }
    messageSource.setFallbackToSystemLocale(properties.isFallbackToSystemLocale());
    Duration cacheDuration = properties.getCacheDuration();
    if (cacheDuration != null) {
      messageSource.setCacheMillis(cacheDuration.toMillis());
    }
    messageSource.setAlwaysUseMessageFormat(properties.isAlwaysUseMessageFormat());
    messageSource.setUseCodeAsDefaultMessage(properties.isUseCodeAsDefaultMessage());
    return messageSource;
  }

  protected static class ResourceBundleCondition extends InfraCondition {

    private static final ConcurrentReferenceHashMap<String, ConditionOutcome>
            cache = new ConcurrentReferenceHashMap<>();

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
      String basename = context.getEnvironment().getProperty("infra.messages.basename", "messages");
      ConditionOutcome outcome = cache.get(basename);
      if (outcome == null) {
        outcome = getMatchOutcomeForBasename(context, basename);
        cache.put(basename, outcome);
      }
      return outcome;
    }

    private ConditionOutcome getMatchOutcomeForBasename(ConditionContext context, String basename) {
      ConditionMessage.Builder message = ConditionMessage.forCondition("ResourceBundle");
      for (String name : StringUtils.commaDelimitedListToStringArray(StringUtils.trimAllWhitespace(basename))) {
        for (Resource resource : getResources(context.getClassLoader(), name)) {
          if (resource.exists()) {
            return ConditionOutcome.match(message.found("bundle").items(resource));
          }
        }
      }
      return ConditionOutcome.noMatch(message.didNotFind("bundle with basename " + basename).atAll());
    }

    private Set<Resource> getResources(@Nullable ClassLoader classLoader, String name) {
      String target = name.replace('.', '/');
      try {
        return new PathMatchingPatternResourceLoader(classLoader)
                .getResources("classpath*:%s.properties".formatted(target));
      }
      catch (Exception ex) {
        return Collections.emptySet();
      }
    }

  }

  static class Hints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      hints.resources().registerPattern("messages.properties").registerPattern("messages_*.properties");
    }

  }

}
