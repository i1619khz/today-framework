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

package cn.taketoday.framework;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import cn.taketoday.beans.CachedIntrospectionResults;
import cn.taketoday.beans.factory.BeanCreationException;
import cn.taketoday.beans.factory.BeanCurrentlyInCreationException;
import cn.taketoday.beans.factory.ObjectProvider;
import cn.taketoday.beans.factory.UnsatisfiedDependencyException;
import cn.taketoday.beans.factory.annotation.Autowired;
import cn.taketoday.beans.factory.config.ConfigurableBeanFactory;
import cn.taketoday.beans.factory.support.BeanDefinitionOverrideException;
import cn.taketoday.beans.factory.support.BeanDefinitionRegistry;
import cn.taketoday.beans.factory.support.BeanNameGenerator;
import cn.taketoday.beans.factory.support.DefaultBeanNameGenerator;
import cn.taketoday.context.ApplicationContext;
import cn.taketoday.context.ApplicationContextException;
import cn.taketoday.context.ApplicationContextInitializer;
import cn.taketoday.context.ApplicationEvent;
import cn.taketoday.context.ApplicationListener;
import cn.taketoday.context.ConfigurableApplicationContext;
import cn.taketoday.context.annotation.AnnotationConfigApplicationContext;
import cn.taketoday.context.annotation.AnnotationConfigUtils;
import cn.taketoday.context.annotation.Bean;
import cn.taketoday.context.annotation.Configuration;
import cn.taketoday.context.annotation.Lazy;
import cn.taketoday.context.aware.ApplicationContextAware;
import cn.taketoday.context.event.ApplicationEventMulticaster;
import cn.taketoday.context.event.ContextRefreshedEvent;
import cn.taketoday.context.event.SimpleApplicationEventMulticaster;
import cn.taketoday.context.event.SmartApplicationListener;
import cn.taketoday.context.support.AbstractApplicationContext;
import cn.taketoday.context.support.MockEnvironment;
import cn.taketoday.context.support.StaticApplicationContext;
import cn.taketoday.core.LinkedMultiValueMap;
import cn.taketoday.core.MultiValueMap;
import cn.taketoday.core.Ordered;
import cn.taketoday.core.env.CommandLinePropertySource;
import cn.taketoday.core.env.CompositePropertySource;
import cn.taketoday.core.env.ConfigurableEnvironment;
import cn.taketoday.core.env.Environment;
import cn.taketoday.core.env.MapPropertySource;
import cn.taketoday.core.env.Profiles;
import cn.taketoday.core.env.PropertySource;
import cn.taketoday.core.env.StandardEnvironment;
import cn.taketoday.core.io.ClassPathResource;
import cn.taketoday.core.io.DefaultResourceLoader;
import cn.taketoday.core.io.Resource;
import cn.taketoday.core.io.ResourceLoader;
import cn.taketoday.format.support.ApplicationConversionService;
import cn.taketoday.framework.BootstrapRegistry.InstanceSupplier;
import cn.taketoday.framework.availability.AvailabilityChangeEvent;
import cn.taketoday.framework.availability.AvailabilityState;
import cn.taketoday.framework.availability.LivenessState;
import cn.taketoday.framework.availability.ReadinessState;
import cn.taketoday.framework.builder.ApplicationBuilder;
import cn.taketoday.framework.context.event.ApplicationContextInitializedEvent;
import cn.taketoday.framework.context.event.ApplicationEnvironmentPreparedEvent;
import cn.taketoday.framework.context.event.ApplicationFailedEvent;
import cn.taketoday.framework.context.event.ApplicationPreparedEvent;
import cn.taketoday.framework.context.event.ApplicationReadyEvent;
import cn.taketoday.framework.context.event.ApplicationStartedEvent;
import cn.taketoday.framework.context.event.ApplicationStartingEvent;
import cn.taketoday.framework.web.embedded.netty.NettyReactiveWebServerFactory;
import cn.taketoday.framework.web.embedded.tomcat.TomcatServletWebServerFactory;
import cn.taketoday.framework.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext;
import cn.taketoday.framework.web.reactive.context.ReactiveWebApplicationContext;
import cn.taketoday.framework.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import cn.taketoday.http.server.reactive.HttpHandler;
import cn.taketoday.util.StringUtils;
import cn.taketoday.web.WebApplicationContext;
import cn.taketoday.web.context.ConfigurableWebEnvironment;
import cn.taketoday.web.context.support.StandardServletEnvironment;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

/**
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2022/1/21 14:47
 */
@ExtendWith(OutputCaptureExtension.class)
class ApplicationTests {

  private String headlessProperty;

  private ConfigurableApplicationContext context;

  private Environment getEnvironment() {
    if (this.context != null) {
      return this.context.getEnvironment();
    }
    throw new IllegalStateException("Could not obtain Environment");
  }

  @BeforeEach
  void storeAndClearHeadlessProperty() {
    this.headlessProperty = System.getProperty("java.awt.headless");
    System.clearProperty("java.awt.headless");
  }

  @AfterEach
  void reinstateHeadlessProperty() {
    if (this.headlessProperty == null) {
      System.clearProperty("java.awt.headless");
    }
    else {
      System.setProperty("java.awt.headless", this.headlessProperty);
    }
  }

  @AfterEach
  void cleanUp() {
    if (this.context != null) {
      this.context.close();
    }
    System.clearProperty("context.main.banner-mode");
    System.clearProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME);
    ApplicationShutdownHookInstance.reset();
  }

  @Test
  void sourcesMustNotBeNull() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Application((Class<?>[]) null).run())
            .withMessageContaining("PrimarySources must not be null");
  }

  @Test
  void sourcesMustNotBeEmpty() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Application().run())
            .withMessageContaining("Sources must not be empty");
  }

  @Test
  void sourcesMustBeAccessible() {
    assertThatIllegalArgumentException()
            .isThrownBy(() -> new Application(InaccessibleConfiguration.class).run())
            .withMessageContaining("No visible constructors");
  }

  @Test
  void customBanner(CapturedOutput output) {
    Application application = spy(new Application(ExampleConfig.class));
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--spring.banner.location=classpath:test-banner.txt");
    assertThat(output).startsWith("Running a Test!");
  }

  @Test
  void customBannerWithProperties(CapturedOutput output) {
    Application application = spy(new Application(ExampleConfig.class));
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--spring.banner.location=classpath:test-banner-with-placeholder.txt",
            "--test.property=123456");
    assertThat(output).containsPattern("Running a Test!\\s+123456");
  }

  @Test
  void logsActiveProfilesWithoutProfileAndSingleDefault(CapturedOutput output) {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(output).contains("No active profile set, falling back to 1 default profile: \"default\"");
  }

  @Test
  void logsActiveProfilesWithoutProfileAndMultipleDefaults(CapturedOutput output) {
    MockEnvironment environment = new MockEnvironment();
    environment.setDefaultProfiles("p0,p1", "default");
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setEnvironment(environment);
    this.context = application.run();
    assertThat(output)
            .contains("No active profile set, falling back to 2 default profiles: \"p0,p1\", \"default\"");
  }

  @Test
  void logsActiveProfilesWithSingleProfile(CapturedOutput output) {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--context.profiles.active=myprofiles");
    assertThat(output).contains("The following 1 profile is active: \"myprofiles\"");
  }

  @Test
  void logsActiveProfilesWithMultipleProfiles(CapturedOutput output) {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setAdditionalProfiles("p1,p2", "p3");
    application.run();
    assertThat(output).contains("The following 2 profiles are active: \"p1,p2\", \"p3\"");
  }

  @Test
  void enableBannerInLogViaProperty(CapturedOutput output) {
    Application application = spy(new Application(ExampleConfig.class));
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--context.main.banner-mode=log");
    then(application).should(atLeastOnce()).setBannerMode(Banner.Mode.LOG);
    assertThat(output).contains("o.s.b.Application");
  }

  @Test
  void setIgnoreBeanInfoPropertyByDefault(CapturedOutput output) {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    String property = System.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME);
    assertThat(property).isEqualTo("true");
  }

  @Test
  void disableIgnoreBeanInfoProperty() {
    System.setProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME, "false");
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    String property = System.getProperty(CachedIntrospectionResults.IGNORE_BEANINFO_PROPERTY_NAME);
    assertThat(property).isEqualTo("false");
  }

  @Test
  void triggersConfigFileApplicationListenerBeforeBinding() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--context.config.name=bindtoapplication");
    assertThat(application).hasFieldOrPropertyWithValue("bannerMode", Banner.Mode.OFF);
  }

  @Test
  void bindsSystemPropertyToApplication() {
    System.setProperty("context.main.banner-mode", "off");
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(application).hasFieldOrPropertyWithValue("bannerMode", Banner.Mode.OFF);
  }

  @Test
  void bindsYamlStyleBannerModeToApplication() {
    Application application = new Application(ExampleConfig.class);
    application.setDefaultProperties(Collections.singletonMap("context.main.banner-mode", false));
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(application).hasFieldOrPropertyWithValue("bannerMode", Banner.Mode.OFF);
  }

  @Test
  void bindsBooleanAsStringBannerModeToApplication() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--context.main.banner-mode=false");
    assertThat(application).hasFieldOrPropertyWithValue("bannerMode", Banner.Mode.OFF);
  }

  @Test
  void customId() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--spring.application.name=foo");
    assertThat(this.context.getId()).startsWith("foo");
  }

  @Test
  void specificApplicationContextFactory() {
    Application application = new Application(ExampleConfig.class);
    application
            .setApplicationContextFactory(ApplicationContextFactory.fromClass(StaticApplicationContext.class));
    this.context = application.run();
    assertThat(this.context).isInstanceOf(StaticApplicationContext.class);
  }

  @Test
  void specificApplicationContextInitializer() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    final AtomicReference<ApplicationContext> reference = new AtomicReference<>();
    application.setInitializers(Collections
            .singletonList((ApplicationContextInitializer) reference::set));
    this.context = application.run("--foo=bar");
    assertThat(this.context).isSameAs(reference.get());
    // Custom initializers do not switch off the defaults
    assertThat(getEnvironment().getProperty("foo")).isEqualTo("bar");
  }

  @Test
  void applicationRunningEventListener() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    AtomicReference<ApplicationReadyEvent> reference = addListener(application, ApplicationReadyEvent.class);
    this.context = application.run("--foo=bar");
    assertThat(application).isSameAs(reference.get().getApplication());
  }

  @Test
  void contextRefreshedEventListener() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    AtomicReference<ContextRefreshedEvent> reference = addListener(application, ContextRefreshedEvent.class);
    this.context = application.run("--foo=bar");
    assertThat(this.context).isSameAs(reference.get().getApplicationContext());
    // Custom initializers do not switch off the defaults
    assertThat(getEnvironment().getProperty("foo")).isEqualTo("bar");
  }

  @Test
  @SuppressWarnings("unchecked")
  void eventsArePublishedInExpectedOrder() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    application.addListeners(listener);
    this.context = application.run();
    InOrder inOrder = Mockito.inOrder(listener);
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationStartingEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationEnvironmentPreparedEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationContextInitializedEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationPreparedEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ContextRefreshedEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationStartedEvent.class));
    then(listener).should(inOrder)
            .onApplicationEvent(argThat(isAvailabilityChangeEventWithState(LivenessState.CORRECT)));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationReadyEvent.class));
    then(listener).should(inOrder)
            .onApplicationEvent(argThat(isAvailabilityChangeEventWithState(ReadinessState.ACCEPTING_TRAFFIC)));
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  void applicationStartedEventHasStartedTime() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    AtomicReference<ApplicationStartedEvent> reference = addListener(application, ApplicationStartedEvent.class);
    this.context = application.run();
    assertThat(reference.get()).isNotNull().extracting(ApplicationStartedEvent::getTimeTaken).isNotNull();
  }

  @Test
  void applicationReadyEventHasReadyTime() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    AtomicReference<ApplicationReadyEvent> reference = addListener(application, ApplicationReadyEvent.class);
    this.context = application.run();
    assertThat(reference.get()).isNotNull().extracting(ApplicationReadyEvent::getTimeTaken).isNotNull();
  }

  @Test
  void defaultApplicationContext() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(this.context).isInstanceOf(AnnotationConfigApplicationContext.class);
  }

  @Test
  void defaultApplicationContextForWeb() {
    Application application = new Application(ExampleWebConfig.class);
    application.setApplicationType(ApplicationType.SERVLET_WEB);
    this.context = application.run();
    assertThat(this.context).isInstanceOf(AnnotationConfigServletWebServerApplicationContext.class);
  }

  @Test
  void defaultApplicationContextForReactiveWeb() {
    Application application = new Application(ExampleReactiveWebConfig.class);
    application.setApplicationType(ApplicationType.REACTIVE_WEB);
    this.context = application.run();
    assertThat(this.context).isInstanceOf(AnnotationConfigReactiveWebServerApplicationContext.class);
  }

  @Test
  void environmentForWeb() {
    Application application = new Application(ExampleWebConfig.class);
    application.setApplicationType(ApplicationType.SERVLET_WEB);
    this.context = application.run();
    assertThat(this.context.getEnvironment()).isInstanceOf(ApplicationServletEnvironment.class);
  }

  @Test
  void environmentForReactiveWeb() {
    Application application = new Application(ExampleReactiveWebConfig.class);
    application.setApplicationType(ApplicationType.REACTIVE_WEB);
    this.context = application.run();
    assertThat(this.context.getEnvironment()).isInstanceOf(ApplicationReactiveWebEnvironment.class);
  }

  @Test
  void customEnvironment() {
    TestApplication application = new TestApplication(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    ConfigurableEnvironment environment = new StandardEnvironment();
    application.setEnvironment(environment);
    this.context = application.run();
    then(application.getLoader()).should().setEnvironment(environment);
  }

  @Test
  void customResourceLoader() {
    TestApplication application = new TestApplication(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    application.setResourceLoader(resourceLoader);
    this.context = application.run();
    then(application.getLoader()).should().setResourceLoader(resourceLoader);
  }

  @Test
  void customResourceLoaderFromConstructor() {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    TestApplication application = new TestApplication(resourceLoader, ExampleWebConfig.class);
    this.context = application.run();
    then(application.getLoader()).should().setResourceLoader(resourceLoader);
  }

  @Test
  void customBeanNameGenerator() {
    TestApplication application = new TestApplication(ExampleWebConfig.class);
    BeanNameGenerator beanNameGenerator = new DefaultBeanNameGenerator();
    application.setBeanNameGenerator(beanNameGenerator);
    this.context = application.run();
    then(application.getLoader()).should().setBeanNameGenerator(beanNameGenerator);
    Object actualGenerator = this.context.getBean(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
    assertThat(actualGenerator).isSameAs(beanNameGenerator);
  }

  @Test
  void customBeanNameGeneratorWithNonWebApplication() {
    TestApplication application = new TestApplication(ExampleWebConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    BeanNameGenerator beanNameGenerator = new DefaultBeanNameGenerator();
    application.setBeanNameGenerator(beanNameGenerator);
    this.context = application.run();
    then(application.getLoader()).should().setBeanNameGenerator(beanNameGenerator);
    Object actualGenerator = this.context.getBean(AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR);
    assertThat(actualGenerator).isSameAs(beanNameGenerator);
  }

  @Test
  void commandLinePropertySource() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    ConfigurableEnvironment environment = new StandardEnvironment();
    application.setEnvironment(environment);
    this.context = application.run("--foo=bar");
    assertThat(environment).has(matchingPropertySource(CommandLinePropertySource.class, "commandLineArgs"));
  }

  @Test
  void commandLinePropertySourceEnhancesEnvironment() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    ConfigurableEnvironment environment = new StandardEnvironment();
    environment.getPropertySources()
            .addFirst(new MapPropertySource("commandLineArgs", Collections.singletonMap("foo", "original")));
    application.setEnvironment(environment);
    this.context = application.run("--foo=bar", "--bar=foo");
    assertThat(environment).has(matchingPropertySource(CompositePropertySource.class, "commandLineArgs"));
    assertThat(environment.getProperty("bar")).isEqualTo("foo");
    // New command line properties take precedence
    assertThat(environment.getProperty("foo")).isEqualTo("bar");
    CompositePropertySource composite = (CompositePropertySource) environment.getPropertySources()
            .get("commandLineArgs");
    assertThat(composite.getPropertySources()).hasSize(2);
    assertThat(composite.getPropertySources()).first().matches(
            (source) -> source.getName().equals("ApplicationCommandLineArgs"),
            "is named ApplicationCommandLineArgs");
    assertThat(composite.getPropertySources()).element(1)
            .matches((source) -> source.getName().equals("commandLineArgs"), "is named commandLineArgs");
  }

  @Test
  void propertiesFileEnhancesEnvironment() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    ConfigurableEnvironment environment = new StandardEnvironment();
    application.setEnvironment(environment);
    this.context = application.run();
    assertThat(environment.getProperty("foo")).isEqualTo("bucket");
  }

  @Test
  void addProfiles() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setAdditionalProfiles("foo");
    ConfigurableEnvironment environment = new StandardEnvironment();
    application.setEnvironment(environment);
    this.context = application.run();
    assertThat(environment.acceptsProfiles(Profiles.of("foo"))).isTrue();
  }

  @Test
  void additionalProfilesOrderedBeforeActiveProfiles() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setAdditionalProfiles("foo");
    ConfigurableEnvironment environment = new StandardEnvironment();
    application.setEnvironment(environment);
    this.context = application.run("--context.profiles.active=bar,spam");
    assertThat(environment.getActiveProfiles()).containsExactly("foo", "bar", "spam");
  }

  @Test
  void addProfilesOrderWithProperties() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setAdditionalProfiles("other");
    ConfigurableEnvironment environment = new StandardEnvironment();
    application.setEnvironment(environment);
    this.context = application.run();
    // Active profile should win over default
    assertThat(environment.getProperty("my.property")).isEqualTo("fromotherpropertiesfile");
  }

  @Test
  void emptyCommandLinePropertySourceNotAdded() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    ConfigurableEnvironment environment = new StandardEnvironment();
    application.setEnvironment(environment);
    this.context = application.run();
    assertThat(environment.getProperty("foo")).isEqualTo("bucket");
  }

  @Test
  void disableCommandLinePropertySource() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setAddCommandLineProperties(false);
    ConfigurableEnvironment environment = new StandardEnvironment();
    application.setEnvironment(environment);
    this.context = application.run("--foo=bar");
    assertThat(environment).doesNotHave(matchingPropertySource(PropertySource.class, "commandLineArgs"));
  }

  @Test
  void contextUsesApplicationConversionService() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(this.context.getBeanFactory().getConversionService())
            .isInstanceOf(ApplicationConversionService.class);
    assertThat(this.context.getEnvironment().getConversionService())
            .isInstanceOf(ApplicationConversionService.class);
  }

  @Test
  void contextWhenHasAddConversionServiceFalseUsesRegularConversionService() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setAddConversionService(false);
    this.context = application.run();
    assertThat(this.context.getBeanFactory().getConversionService()).isNull();
    assertThat(this.context.getEnvironment().getConversionService())
            .isNotInstanceOf(ApplicationConversionService.class);
  }

  @Test
  void runCommandLineRunnersAndApplicationRunners() {
    Application application = new Application(CommandLineRunConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("arg");
    assertThat(this.context).has(runTestRunnerBean("runnerA"));
    assertThat(this.context).has(runTestRunnerBean("runnerB"));
    assertThat(this.context).has(runTestRunnerBean("runnerC"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void runnersAreCalledAfterStartedIsLoggedAndBeforeApplicationReadyEventIsPublished(CapturedOutput output)
          throws Exception {
    Application application = new Application(ExampleConfig.class);
    ApplicationRunner applicationRunner = mock(ApplicationRunner.class);
    CommandLineRunner commandLineRunner = mock(CommandLineRunner.class);
    application.addInitializers((context) -> {
      ConfigurableBeanFactory beanFactory = context.getBeanFactory();
      beanFactory.registerSingleton("commandLineRunner", (CommandLineRunner) (args) -> {
        assertThat(output).contains("Started");
        commandLineRunner.run(args);
      });
      beanFactory.registerSingleton("applicationRunner", (ApplicationRunner) (args) -> {
        assertThat(output).contains("Started");
        applicationRunner.run(args);
      });
    });
    application.setApplicationType(ApplicationType.NONE_WEB);
    ApplicationListener<ApplicationReadyEvent> eventListener = mock(ApplicationListener.class);
    application.addListeners(eventListener);
    this.context = application.run();
    InOrder applicationRunnerOrder = Mockito.inOrder(eventListener, applicationRunner);
    applicationRunnerOrder.verify(applicationRunner).run(any(ApplicationArguments.class));
    applicationRunnerOrder.verify(eventListener).onApplicationEvent(any(ApplicationReadyEvent.class));
    InOrder commandLineRunnerOrder = Mockito.inOrder(eventListener, commandLineRunner);
    commandLineRunnerOrder.verify(commandLineRunner).run();
    commandLineRunnerOrder.verify(eventListener).onApplicationEvent(any(ApplicationReadyEvent.class));
  }

  @Test
  void applicationRunnerFailureCausesApplicationFailedEventToBePublished() throws Exception {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    @SuppressWarnings("unchecked")
    ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    application.addListeners(listener);
    ApplicationRunner runner = mock(ApplicationRunner.class);
    Exception failure = new Exception();
    willThrow(failure).given(runner).run(isA(ApplicationArguments.class));
    application.addInitializers((context) -> context.getBeanFactory().registerSingleton("runner", runner));
    assertThatIllegalStateException().isThrownBy(application::run).withCause(failure);
    then(listener).should().onApplicationEvent(isA(ApplicationStartedEvent.class));
    then(listener).should().onApplicationEvent(isA(ApplicationFailedEvent.class));
    then(listener).should(never()).onApplicationEvent(isA(ApplicationReadyEvent.class));
  }

  @Test
  void commandLineRunnerFailureCausesApplicationFailedEventToBePublished() throws Exception {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    @SuppressWarnings("unchecked")
    ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    application.addListeners(listener);
    CommandLineRunner runner = mock(CommandLineRunner.class);
    Exception failure = new Exception();
    willThrow(failure).given(runner).run();
    application.addInitializers((context) -> context.getBeanFactory().registerSingleton("runner", runner));
    assertThatIllegalStateException().isThrownBy(application::run).withCause(failure);
    then(listener).should().onApplicationEvent(isA(ApplicationStartedEvent.class));
    then(listener).should().onApplicationEvent(isA(ApplicationFailedEvent.class));
    then(listener).should(never()).onApplicationEvent(isA(ApplicationReadyEvent.class));
  }

  @Test
  void failureInReadyEventListenerDoesNotCausePublicationOfFailedEvent() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    @SuppressWarnings("unchecked")
    ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    application.addListeners(listener);
    RuntimeException failure = new RuntimeException();
    willThrow(failure).given(listener).onApplicationEvent(isA(ApplicationReadyEvent.class));
    assertThatExceptionOfType(RuntimeException.class).isThrownBy(application::run).isEqualTo(failure);
    then(listener).should().onApplicationEvent(isA(ApplicationReadyEvent.class));
    then(listener).should(never()).onApplicationEvent(isA(ApplicationFailedEvent.class));
  }

  @Test
  void failureInReadyEventListenerCloseApplicationContext(CapturedOutput output) {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    ExitCodeListener exitCodeListener = new ExitCodeListener();
    application.addListeners(exitCodeListener);
    @SuppressWarnings("unchecked")
    ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    application.addListeners(listener);
    ExitStatusException failure = new ExitStatusException();
    willThrow(failure).given(listener).onApplicationEvent(isA(ApplicationReadyEvent.class));
    assertThatExceptionOfType(RuntimeException.class).isThrownBy(application::run);
    then(listener).should().onApplicationEvent(isA(ApplicationReadyEvent.class));
    then(listener).should(never()).onApplicationEvent(isA(ApplicationFailedEvent.class));
    assertThat(exitCodeListener.getExitCode()).isEqualTo(11);
    assertThat(output).contains("Application run failed");
  }

  @Test
  void loadSources() {
    Class<?>[] sources = { ExampleConfig.class, TestCommandLineRunner.class };
    TestApplication application = new TestApplication(sources);
    application.getSources().add("a");
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setUseMockLoader(true);
    this.context = application.run();
    Set<Object> allSources = application.getAllSources();
    assertThat(allSources).contains(ExampleConfig.class, TestCommandLineRunner.class, "a");
  }

  @Test
  void wildcardSources() {
    TestApplication application = new TestApplication();
    application.getSources().add("classpath*:cn/taketoday/framework/sample-${sample.app.test.prop}.xml");
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
  }

  @Test
  void run() {
    this.context = Application.run(ExampleWebConfig.class);
    assertThat(this.context).isNotNull();
  }

  @Test
  void runComponents() {
    this.context = Application.run(new Class<?>[] { ExampleWebConfig.class, Object.class }, new String[0]);
    assertThat(this.context).isNotNull();
  }

  @Test
  void exit() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(this.context).isNotNull();
    assertThat(Application.exit(this.context)).isEqualTo(0);
  }

  @Test
  void exitWithExplicitCode() {
    Application application = new Application(ExampleConfig.class);
    ExitCodeListener listener = new ExitCodeListener();
    application.addListeners(listener);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(this.context).isNotNull();
    assertThat(Application.exit(this.context, (ExitCodeGenerator) () -> 2)).isEqualTo(2);
    assertThat(listener.getExitCode()).isEqualTo(2);
  }

  @Test
  void exitWithExplicitCodeFromException() {
    final StartupExceptionHandler handler = mock(StartupExceptionHandler.class);
    Application application = new Application(ExitCodeCommandLineRunConfig.class) {

      @Override
      StartupExceptionHandler getApplicationExceptionHandler() {
        return handler;
      }

    };
    ExitCodeListener listener = new ExitCodeListener();
    application.addListeners(listener);
    application.setApplicationType(ApplicationType.NONE_WEB);
    assertThatIllegalStateException().isThrownBy(application::run);
    then(handler).should().registerExitCode(11);
    assertThat(listener.getExitCode()).isEqualTo(11);
  }

  @Test
  void exitWithExplicitCodeFromMappedException() {
    final StartupExceptionHandler handler = mock(StartupExceptionHandler.class);
    Application application = new Application(MappedExitCodeCommandLineRunConfig.class) {

      @Override
      StartupExceptionHandler getApplicationExceptionHandler() {
        return handler;
      }

    };
    ExitCodeListener listener = new ExitCodeListener();
    application.addListeners(listener);
    application.setApplicationType(ApplicationType.NONE_WEB);
    assertThatIllegalStateException().isThrownBy(application::run);
    then(handler).should().registerExitCode(11);
    assertThat(listener.getExitCode()).isEqualTo(11);
  }

  @Test
  void exceptionFromRefreshIsHandledGracefully(CapturedOutput output) {
    final StartupExceptionHandler handler = mock(StartupExceptionHandler.class);
    Application application = new Application(RefreshFailureConfig.class) {

      @Override
      StartupExceptionHandler getApplicationExceptionHandler() {
        return handler;
      }

    };
    ExitCodeListener listener = new ExitCodeListener();
    application.addListeners(listener);
    application.setApplicationType(ApplicationType.NONE_WEB);
    assertThatExceptionOfType(RuntimeException.class).isThrownBy(application::run);
    ArgumentCaptor<RuntimeException> exceptionCaptor = ArgumentCaptor.forClass(RuntimeException.class);
    then(handler).should().registerLoggedException(exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue()).hasCauseInstanceOf(RefreshFailureException.class);
    assertThat(output).doesNotContain("NullPointerException");
  }

  @Test
  void defaultCommandLineArgs() {
    Application application = new Application(ExampleConfig.class);
    application.setDefaultProperties(
            StringUtils.splitArrayElementsIntoProperties(new String[] { "baz=", "bar=spam" }, "="));
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--bar=foo", "bucket", "crap");
    assertThat(this.context).isInstanceOf(AnnotationConfigApplicationContext.class);
    assertThat(getEnvironment().getProperty("bar")).isEqualTo("foo");
    assertThat(getEnvironment().getProperty("baz")).isEqualTo("");
  }

  @Test
  void defaultPropertiesShouldBeMerged() {
    MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(
            new MapPropertySource(DefaultPropertiesPropertySource.NAME, Collections.singletonMap("bar", "foo")));
    Application application = new ApplicationBuilder(ExampleConfig.class).environment(environment)
            .properties("baz=bing").type(ApplicationType.NONE_WEB).build();
    this.context = application.run();
    assertThat(getEnvironment().getProperty("bar")).isEqualTo("foo");
    assertThat(getEnvironment().getProperty("baz")).isEqualTo("bing");
  }

  @Test
  void commandLineArgsApplyToApplication() {
    TestApplication application = new TestApplication(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--context.main.banner-mode=OFF");
    assertThat(application.getBannerMode()).isEqualTo(Banner.Mode.OFF);
  }

  @Test
  void registerShutdownHook() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(ApplicationShutdownHookInstance.get()).registeredApplicationContext(this.context);
  }

  @Test
  void registerShutdownHookOff() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setRegisterShutdownHook(false);
    this.context = application.run();
    assertThat(ApplicationShutdownHookInstance.get()).didNotRegisterApplicationContext(this.context);
  }

  @Test
  void registerListener() {
    Application application = new Application(ExampleConfig.class, ListenerConfig.class);
    application.setApplicationContextFactory(ApplicationContextFactory.fromClass(SpyApplicationContext.class));
    Set<ApplicationEvent> events = new LinkedHashSet<>();
    application.addListeners((ApplicationListener<ApplicationEvent>) events::add);
    this.context = application.run();
    assertThat(events).hasAtLeastOneElementOfType(ApplicationPreparedEvent.class);
    assertThat(events).hasAtLeastOneElementOfType(ContextRefreshedEvent.class);
    verifyRegisteredListenerSuccessEvents();
  }

  @Test
  void registerListenerWithCustomMulticaster() {
    Application application = new Application(ExampleConfig.class, ListenerConfig.class,
            Multicaster.class);
    application.setApplicationContextFactory(ApplicationContextFactory.fromClass(SpyApplicationContext.class));
    Set<ApplicationEvent> events = new LinkedHashSet<>();
    application.addListeners((ApplicationListener<ApplicationEvent>) events::add);
    this.context = application.run();
    assertThat(events).hasAtLeastOneElementOfType(ApplicationPreparedEvent.class);
    assertThat(events).hasAtLeastOneElementOfType(ContextRefreshedEvent.class);
    verifyRegisteredListenerSuccessEvents();
  }

  @SuppressWarnings("unchecked")
  private void verifyRegisteredListenerSuccessEvents() {
    ApplicationListener<ApplicationEvent> listener = this.context.getBean("testApplicationListener",
            ApplicationListener.class);
    InOrder inOrder = Mockito.inOrder(listener);
    then(listener).should(inOrder).onApplicationEvent(isA(ContextRefreshedEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationStartedEvent.class));
    then(listener).should(inOrder)
            .onApplicationEvent(argThat(isAvailabilityChangeEventWithState(LivenessState.CORRECT)));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationReadyEvent.class));
    then(listener).should(inOrder)
            .onApplicationEvent(argThat(isAvailabilityChangeEventWithState(ReadinessState.ACCEPTING_TRAFFIC)));
    inOrder.verifyNoMoreInteractions();
  }

  @SuppressWarnings("unchecked")
  @Test
  void applicationListenerFromApplicationIsCalledWhenContextFailsRefreshBeforeListenerRegistration() {
    ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    Application application = new Application(ExampleConfig.class);
    application.addListeners(listener);
    assertThatExceptionOfType(ApplicationContextException.class).isThrownBy(application::run);
    verifyRegisteredListenerFailedFromApplicationEvents(listener);
  }

  @SuppressWarnings("unchecked")
  @Test
  void applicationListenerFromApplicationIsCalledWhenContextFailsRefreshAfterListenerRegistration() {
    ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    Application application = new Application(BrokenPostConstructConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.addListeners(listener);
    assertThatExceptionOfType(BeanCreationException.class).isThrownBy(application::run);
    verifyRegisteredListenerFailedFromApplicationEvents(listener);
  }

  private void verifyRegisteredListenerFailedFromApplicationEvents(ApplicationListener<ApplicationEvent> listener) {
    InOrder inOrder = Mockito.inOrder(listener);
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationStartingEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationEnvironmentPreparedEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationContextInitializedEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationPreparedEvent.class));
    then(listener).should(inOrder).onApplicationEvent(isA(ApplicationFailedEvent.class));
    inOrder.verifyNoMoreInteractions();
  }

  @SuppressWarnings("unchecked")
  @Test
  void applicationListenerFromContextIsCalledWhenContextFailsRefreshBeforeListenerRegistration() {
    final ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    Application application = new Application(ExampleConfig.class);
    application.addInitializers((applicationContext) -> applicationContext.addApplicationListener(listener));
    assertThatExceptionOfType(ApplicationContextException.class).isThrownBy(application::run);
    then(listener).should().onApplicationEvent(isA(ApplicationFailedEvent.class));
    then(listener).shouldHaveNoMoreInteractions();
  }

  @SuppressWarnings("unchecked")
  @Test
  void applicationListenerFromContextIsCalledWhenContextFailsRefreshAfterListenerRegistration() {
    ApplicationListener<ApplicationEvent> listener = mock(ApplicationListener.class);
    Application application = new Application(BrokenPostConstructConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.addInitializers((applicationContext) -> applicationContext.addApplicationListener(listener));
    assertThatExceptionOfType(BeanCreationException.class).isThrownBy(application::run);
    then(listener).should().onApplicationEvent(isA(ApplicationFailedEvent.class));
    then(listener).shouldHaveNoMoreInteractions();
  }

  @Test
  void headless() {
    TestApplication application = new TestApplication(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(System.getProperty("java.awt.headless")).isEqualTo("true");
  }

  @Test
  void headlessFalse() {
    TestApplication application = new TestApplication(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.setHeadless(false);
    this.context = application.run();
    assertThat(System.getProperty("java.awt.headless")).isEqualTo("false");
  }

  @Test
  void headlessSystemPropertyTakesPrecedence() {
    System.setProperty("java.awt.headless", "false");
    TestApplication application = new TestApplication(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(System.getProperty("java.awt.headless")).isEqualTo("false");
  }

  @Test
  void getApplicationArgumentsBean() {
    TestApplication application = new TestApplication(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--debug", "spring", "boot");
    ApplicationArguments args = this.context.getBean(ApplicationArguments.class);
    assertThat(args.getNonOptionArgs()).containsExactly("spring", "boot");
    assertThat(args.containsOption("debug")).isTrue();
  }

  @Test
  void webApplicationSwitchedOffInListener() {
    TestApplication application = new TestApplication(ExampleConfig.class);
    application.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) (event) -> {
      assertThat(event.getEnvironment()).isInstanceOf(ApplicationServletEnvironment.class);
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(event.getEnvironment(), "foo=bar");
      event.getApplication().setApplicationType(ApplicationType.NONE_WEB);
    });
    this.context = application.run();
    assertThat(this.context.getEnvironment()).isNotInstanceOf(StandardServletEnvironment.class);
    assertThat(this.context.getEnvironment().getProperty("foo")).isEqualTo("bar");
    Iterator<PropertySource<?>> iterator = this.context.getEnvironment().getPropertySources().iterator();
    assertThat(iterator.next().getName()).isEqualTo("configurationProperties");
    assertThat(iterator.next().getName())
            .isEqualTo(TestPropertySourceUtils.INLINED_PROPERTIES_PROPERTY_SOURCE_NAME);
  }

  @Test
  void nonWebApplicationConfiguredViaAPropertyHasTheCorrectTypeOfContextAndEnvironment() {
    ConfigurableApplicationContext context = new Application(ExampleConfig.class)
            .run("--context.main.web-application-type=NONE_WEB");
    assertThat(context).isNotInstanceOfAny(WebApplicationContext.class, ReactiveWebApplicationContext.class);
    assertThat(context.getEnvironment()).isNotInstanceOfAny(ConfigurableWebEnvironment.class);
  }

  @Test
  void webApplicationConfiguredViaAPropertyHasTheCorrectTypeOfContextAndEnvironment() {
    ConfigurableApplicationContext context = new Application(ExampleWebConfig.class)
            .run("--context.main.web-application-type=servlet");
    assertThat(context).isInstanceOf(WebApplicationContext.class);
    assertThat(context.getEnvironment()).isInstanceOf(ApplicationServletEnvironment.class);
  }

  @Test
  void reactiveApplicationConfiguredViaAPropertyHasTheCorrectTypeOfContextAndEnvironment() {
    ConfigurableApplicationContext context = new Application(ExampleReactiveWebConfig.class)
            .run("--context.main.web-application-type=reactive");
    assertThat(context).isInstanceOf(ReactiveWebApplicationContext.class);
    assertThat(context.getEnvironment()).isInstanceOf(ApplicationReactiveWebEnvironment.class);
  }

  @Test
  void environmentIsConvertedIfTypeDoesNotMatch() {
    ConfigurableApplicationContext context = new Application(ExampleReactiveWebConfig.class)
            .run("--context.profiles.active=withApplicationType");
    assertThat(context).isInstanceOf(ReactiveWebApplicationContext.class);
    assertThat(context.getEnvironment()).isInstanceOf(ApplicationReactiveWebEnvironment.class);
  }

  @Test
  void failureResultsInSingleStackTrace(CapturedOutput output) throws Exception {
    ThreadGroup group = new ThreadGroup("main");
    Thread thread = new Thread(group, "main") {

      @Override
      public void run() {
        Application application = new Application(FailingConfig.class);
        application.setApplicationType(ApplicationType.NONE_WEB);
        application.run();
      }

    };
    thread.start();
    thread.join(6000);
    assertThat(output).containsOnlyOnce("Caused by: java.lang.RuntimeException: ExpectedError");
  }

  @Test
  void beanDefinitionOverridingIsDisabledByDefault() {
    assertThatExceptionOfType(BeanDefinitionOverrideException.class)
            .isThrownBy(() -> new Application(ExampleConfig.class, OverrideConfig.class).run());
  }

  @Test
  void beanDefinitionOverridingCanBeEnabled() {
    assertThat(new Application(ExampleConfig.class, OverrideConfig.class)
            .run("--context.main.allow-bean-definition-overriding=true", "--context.main.web-application-type=NONE_WEB")
            .getBean("someBean")).isEqualTo("override");
  }

  @Test
  void circularReferencesAreDisabledByDefault() {
    assertThatExceptionOfType(UnsatisfiedDependencyException.class)
            .isThrownBy(() -> new Application(ExampleProducerConfiguration.class,
                    ExampleConsumerConfiguration.class).run("--context.main.web-application-type=NONE_WEB"))
            .withRootCauseInstanceOf(BeanCurrentlyInCreationException.class);
  }

  @Test
  void circularReferencesCanBeEnabled() {
    assertThatNoException().isThrownBy(
            () -> new Application(ExampleProducerConfiguration.class, ExampleConsumerConfiguration.class).run(
                    "--context.main.web-application-type=NONE_WEB", "--context.main.allow-circular-references=true"));
  }

  @Test
  void relaxedBindingShouldWorkBeforeEnvironmentIsPrepared() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run("--context.config.additionalLocation=classpath:custom-config/");
    assertThat(this.context.getEnvironment().getProperty("hello")).isEqualTo("world");
  }

  @Test
  void lazyInitializationIsDisabledByDefault() {
    assertThat(new Application(LazyInitializationConfig.class).run("--context.main.web-application-type=NONE_WEB")
            .getBean(AtomicInteger.class)).hasValue(1);
  }

  @Test
  void lazyInitializationCanBeEnabled() {
    assertThat(new Application(LazyInitializationConfig.class)
            .run("--context.main.web-application-type=NONE_WEB", "--context.main.lazy-initialization=true")
            .getBean(AtomicInteger.class)).hasValue(0);
  }

  @Test
  void lazyInitializationIgnoresBeansThatAreExplicitlyNotLazy() {
    assertThat(new Application(NotLazyInitializationConfig.class)
            .run("--context.main.web-application-type=NONE_WEB", "--context.main.lazy-initialization=true")
            .getBean(AtomicInteger.class)).hasValue(1);
  }

  @Test
  void lazyInitializationIgnoresLazyInitializationExcludeFilteredBeans() {
    assertThat(new Application(LazyInitializationExcludeFilterConfig.class)
            .run("--context.main.web-application-type=NONE_WEB", "--context.main.lazy-initialization=true")
            .getBean(AtomicInteger.class)).hasValue(1);
  }

  @Test
  void addBootstrapRegistryInitializer() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.addBootstrapRegistryInitializer(
            (bootstrapContext) -> bootstrapContext.register(String.class, InstanceSupplier.of("boot")));
    TestApplicationListener listener = new TestApplicationListener();
    application.addListeners(listener);
    application.run();
    ApplicationStartingEvent startingEvent = listener.getEvent(ApplicationStartingEvent.class);
    assertThat(startingEvent.getBootstrapContext().get(String.class)).isEqualTo("boot");
    ApplicationEnvironmentPreparedEvent environmentPreparedEvent = listener
            .getEvent(ApplicationEnvironmentPreparedEvent.class);
    assertThat(environmentPreparedEvent.getBootstrapContext().get(String.class)).isEqualTo("boot");
  }

  @Test
  void addBootstrapRegistryInitializerCanRegisterBeans() {
    Application application = new Application(ExampleConfig.class);
    application.setApplicationType(ApplicationType.NONE_WEB);
    application.addBootstrapRegistryInitializer((bootstrapContext) -> {
      bootstrapContext.register(String.class, InstanceSupplier.of("boot"));
      bootstrapContext.addCloseListener((event) -> event.getApplicationContext().getBeanFactory()
              .registerSingleton("test", event.getBootstrapContext().get(String.class)));
    });
    ConfigurableApplicationContext applicationContext = application.run();
    assertThat(applicationContext.getBean("test")).isEqualTo("boot");
  }

  @Test
  void settingEnvironmentPrefixViaPropertiesThrowsException() {
    assertThatIllegalStateException()
            .isThrownBy(() -> new Application().run("--context.main.environment-prefix=my"));
  }

  @Test
  void bindsEnvironmentPrefixToApplication() {
    Application application = new Application(ExampleConfig.class);
    application.setEnvironmentPrefix("my");
    application.setApplicationType(ApplicationType.NONE_WEB);
    this.context = application.run();
    assertThat(application.getEnvironmentPrefix()).isEqualTo("my");
  }

  @Test
  void deregistersShutdownHookForFailedApplicationContext() {
    Application application = new Application(BrokenPostConstructConfig.class);
    List<ApplicationEvent> events = new ArrayList<>();
    application.addListeners(events::add);
    application.setApplicationType(ApplicationType.NONE_WEB);
    assertThatExceptionOfType(BeanCreationException.class).isThrownBy(application::run);
    assertThat(events).hasAtLeastOneElementOfType(ApplicationFailedEvent.class);
    ApplicationFailedEvent failure = events.stream().filter((event) -> event instanceof ApplicationFailedEvent)
            .map(ApplicationFailedEvent.class::cast).findFirst().get();
    assertThat(ApplicationShutdownHookInstance.get())
            .didNotRegisterApplicationContext(failure.getApplicationContext());
  }

  private <S extends AvailabilityState> ArgumentMatcher<ApplicationEvent> isAvailabilityChangeEventWithState(
          S state) {
    return (argument) -> (argument instanceof AvailabilityChangeEvent<?>)
            && ((AvailabilityChangeEvent<?>) argument).getState().equals(state);
  }

  private <E extends ApplicationEvent> AtomicReference<E> addListener(Application application,
          Class<E> eventType) {
    AtomicReference<E> reference = new AtomicReference<>();
    application.addListeners(new TestEventListener<>(eventType, reference));
    return reference;
  }

  private Condition<ConfigurableEnvironment> matchingPropertySource(final Class<?> propertySourceClass,
          final String name) {

    return new Condition<>("has property source") {

      @Override
      public boolean matches(ConfigurableEnvironment value) {
        for (PropertySource<?> source : value.getPropertySources()) {
          if (propertySourceClass.isInstance(source) && (name == null || name.equals(source.getName()))) {
            return true;
          }
        }
        return false;
      }

    };
  }

  private Condition<ConfigurableApplicationContext> runTestRunnerBean(final String name) {
    return new Condition<>("run testrunner bean") {

      @Override
      public boolean matches(ConfigurableApplicationContext value) {
        return value.getBean(name, AbstractTestRunner.class).hasRun();
      }

    };
  }

  static class TestEventListener<E extends ApplicationEvent> implements SmartApplicationListener {

    private final Class<E> eventType;

    private final AtomicReference<E> reference;

    TestEventListener(Class<E> eventType, AtomicReference<E> reference) {
      this.eventType = eventType;
      this.reference = reference;
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
      return this.eventType.isAssignableFrom(eventType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onApplicationEvent(ApplicationEvent event) {
      this.reference.set((E) event);
    }

  }

  @Configuration
  static class InaccessibleConfiguration {

    private InaccessibleConfiguration() {
    }

  }

  static class SpyApplicationContext extends AnnotationConfigApplicationContext {

    ConfigurableApplicationContext applicationContext = spy(new AnnotationConfigApplicationContext());

    @Override
    public void registerShutdownHook() {
      this.applicationContext.registerShutdownHook();
    }

    ConfigurableApplicationContext getApplicationContext() {
      return this.applicationContext;
    }

    @Override
    public void close() {
      this.applicationContext.close();
      super.close();
    }

  }

  static class TestApplication extends Application {

    private ApplicationBeanDefinitionLoader loader;

    private boolean useMockLoader;

    private Banner.Mode bannerMode;

    TestApplication(Class<?>... primarySources) {
      super(primarySources);
    }

    TestApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
      super(resourceLoader, primarySources);
    }

    void setUseMockLoader(boolean useMockLoader) {
      this.useMockLoader = useMockLoader;
    }

    @Override
    protected ApplicationBeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
      if (this.useMockLoader) {
        this.loader = mock(ApplicationBeanDefinitionLoader.class);
      }
      else {
        this.loader = spy(super.createBeanDefinitionLoader(registry, sources));
      }
      return this.loader;
    }

    ApplicationBeanDefinitionLoader getLoader() {
      return this.loader;
    }

    @Override
    public void setBannerMode(Banner.Mode bannerMode) {
      super.setBannerMode(bannerMode);
      this.bannerMode = bannerMode;
    }

    Banner.Mode getBannerMode() {
      return this.bannerMode;
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class ExampleConfig {

    @Bean
    String someBean() {
      return "test";
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class OverrideConfig {

    @Bean
    String someBean() {
      return "override";
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class BrokenPostConstructConfig {

    @Bean
    Thing thing() {
      return new Thing();
    }

    static class Thing {

      @javax.annotation.PostConstruct
      void boom() {
        throw new IllegalStateException();
      }

    }

  }

  @Configuration(proxyBeanMethods = false)
  static class ListenerConfig {

    @Bean
    ApplicationListener<?> testApplicationListener() {
      return mock(ApplicationListener.class);
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class Multicaster {

    @Bean(name = AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME)
    ApplicationEventMulticaster applicationEventMulticaster() {
      return spy(new SimpleApplicationEventMulticaster());
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class ExampleWebConfig {

    @Bean
    TomcatServletWebServerFactory webServer() {
      return new TomcatServletWebServerFactory(0);
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class ExampleReactiveWebConfig {

    @Bean
    NettyReactiveWebServerFactory webServerFactory() {
      return new NettyReactiveWebServerFactory(0);
    }

    @Bean
    HttpHandler httpHandler() {
      return (serverHttpRequest, serverHttpResponse) -> Mono.empty();
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class FailingConfig {

    @Bean
    Object fail() {
      throw new RuntimeException("ExpectedError");
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class CommandLineRunConfig {

    @Bean
    TestCommandLineRunner runnerC() {
      return new TestCommandLineRunner(Ordered.LOWEST_PRECEDENCE, "runnerB", "runnerA");
    }

    @Bean
    TestApplicationRunner runnerB() {
      return new TestApplicationRunner(Ordered.LOWEST_PRECEDENCE - 1, "runnerA");
    }

    @Bean
    TestCommandLineRunner runnerA() {
      return new TestCommandLineRunner(Ordered.HIGHEST_PRECEDENCE);
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class ExitCodeCommandLineRunConfig {

    @Bean
    CommandLineRunner runner() {
      return (args) -> {
        throw new IllegalStateException(new ExitStatusException());
      };
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class MappedExitCodeCommandLineRunConfig {

    @Bean
    CommandLineRunner runner() {
      return (args) -> {
        throw new IllegalStateException();
      };
    }

    @Bean
    ExitCodeExceptionMapper exceptionMapper() {
      return (exception) -> {
        if (exception instanceof IllegalStateException) {
          return 11;
        }
        return 0;
      };
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class RefreshFailureConfig {

    @PostConstruct
    void fail() {
      throw new RefreshFailureException();
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class LazyInitializationConfig {

    @Bean
    AtomicInteger counter() {
      return new AtomicInteger();
    }

    @Bean
    LazyBean lazyBean(AtomicInteger counter) {
      return new LazyBean(counter);
    }

    static class LazyBean {

      LazyBean(AtomicInteger counter) {
        counter.incrementAndGet();
      }

    }

  }

  @Configuration(proxyBeanMethods = false)
  static class NotLazyInitializationConfig {

    @Bean
    AtomicInteger counter() {
      return new AtomicInteger();
    }

    @Bean
    @Lazy(false)
    NotLazyBean NotLazyBean(AtomicInteger counter) {
      return new NotLazyBean(counter);
    }

    static class NotLazyBean {

      NotLazyBean(AtomicInteger counter) {
        counter.getAndIncrement();
      }

    }

  }

  @Configuration(proxyBeanMethods = false)
  static class LazyInitializationExcludeFilterConfig {

    @Bean
    AtomicInteger counter() {
      return new AtomicInteger();
    }

    @Bean
    NotLazyBean notLazyBean(AtomicInteger counter) {
      return new NotLazyBean(counter);
    }

    @Bean
    static LazyInitializationExcludeFilter lazyInitializationExcludeFilter() {
      return LazyInitializationExcludeFilter.forBeanTypes(NotLazyBean.class);
    }

  }

  static class NotLazyBean {

    NotLazyBean(AtomicInteger counter) {
      counter.getAndIncrement();
    }

  }

  static class ExitStatusException extends RuntimeException implements ExitCodeGenerator {

    @Override
    public int getExitCode() {
      return 11;
    }

  }

  static class RefreshFailureException extends RuntimeException {

  }

  abstract static class AbstractTestRunner implements ApplicationContextAware, Ordered {

    private final String[] expectedBefore;

    private ApplicationContext applicationContext;

    private final int order;

    private boolean run;

    AbstractTestRunner(int order, String... expectedBefore) {
      this.expectedBefore = expectedBefore;
      this.order = order;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
    }

    @Override
    public int getOrder() {
      return this.order;
    }

    void markAsRan() {
      this.run = true;
      for (String name : this.expectedBefore) {
        AbstractTestRunner bean = this.applicationContext.getBean(name, AbstractTestRunner.class);
        assertThat(bean.hasRun()).isTrue();
      }
    }

    boolean hasRun() {
      return this.run;
    }

  }

  static class TestCommandLineRunner extends AbstractTestRunner implements CommandLineRunner {

    TestCommandLineRunner(int order, String... expectedBefore) {
      super(order, expectedBefore);
    }

    @Override
    public void run(String... args) {
      markAsRan();
    }

  }

  static class TestApplicationRunner extends AbstractTestRunner implements ApplicationRunner {

    TestApplicationRunner(int order, String... expectedBefore) {
      super(order, expectedBefore);
    }

    @Override
    public void run(ApplicationArguments args) {
      markAsRan();
    }

  }

  static class ExitCodeListener implements ApplicationListener<ExitCodeEvent> {

    private Integer exitCode;

    @Override
    public void onApplicationEvent(ExitCodeEvent event) {
      this.exitCode = event.getExitCode();
    }

    Integer getExitCode() {
      return this.exitCode;
    }

  }

  static class MockResourceLoader implements ResourceLoader {

    private final Map<String, Resource> resources = new HashMap<>();

    void addResource(String source, String path) {
      this.resources.put(source, new ClassPathResource(path, getClass()));
    }

    @Override
    public Resource getResource(String path) {
      Resource resource = this.resources.get(path);
      return (resource != null) ? resource : new ClassPathResource("doesnotexist");
    }

    @Override
    public ClassLoader getClassLoader() {
      return getClass().getClassLoader();
    }

  }

  static class TestApplicationListener implements ApplicationListener<ApplicationEvent> {

    private final MultiValueMap<Class<?>, ApplicationEvent> events = new LinkedMultiValueMap<>();

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
      this.events.add(event.getClass(), event);
    }

    @SuppressWarnings("unchecked")
    <E extends ApplicationEvent> E getEvent(Class<E> type) {
      return (E) this.events.get(type).get(0);
    }

  }

  static class Example {

  }

  @FunctionalInterface
  interface ExampleConfigurer {

    void configure(Example example);

  }

  @Configuration(proxyBeanMethods = false)
  static class ExampleProducerConfiguration {

    @Bean
    Example example(ObjectProvider<ExampleConfigurer> configurers) {
      Example example = new Example();
      configurers.orderedStream().forEach((configurer) -> configurer.configure(example));
      return example;
    }

  }

  @Configuration(proxyBeanMethods = false)
  static class ExampleConsumerConfiguration {

    @Autowired
    Example example;

    @Bean
    ExampleConfigurer configurer() {
      return (example) -> {
      };
    }

  }

}
