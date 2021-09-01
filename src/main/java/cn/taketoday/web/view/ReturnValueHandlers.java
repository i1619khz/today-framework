/**
 * Original Author -> 杨海健 (taketoday@foxmail.com) https://taketoday.cn
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
package cn.taketoday.web.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.taketoday.context.Environment;
import cn.taketoday.core.Assert;
import cn.taketoday.util.ClassUtils;
import cn.taketoday.util.OrderUtils;
import cn.taketoday.web.WebApplicationContext;
import cn.taketoday.web.WebApplicationContextSupport;
import cn.taketoday.web.config.CompositeWebMvcConfiguration;
import cn.taketoday.web.config.WebMvcConfiguration;
import cn.taketoday.web.handler.JacksonObjectNotationProcessor;
import cn.taketoday.web.view.template.AbstractTemplateRenderer;
import cn.taketoday.web.view.template.DefaultTemplateRenderer;
import cn.taketoday.web.view.template.TemplateRenderer;

/**
 * return-value handlers
 *
 * @author TODAY 2019-12-28 13:47
 */
public class ReturnValueHandlers extends WebApplicationContextSupport {
  public static final String DOWNLOAD_BUFF_SIZE = "download.buff.size";

  private final ArrayList<ReturnValueHandler> handlers = new ArrayList<>(8);
  /**
   * @since 3.0.1
   */
  private int downloadFileBufferSize = 10240;
  /**
   * @since 3.0.1
   */
  private MessageConverter messageConverter;
  /**
   * @since 3.0.1
   */
  private RedirectModelManager redirectModelManager;
  /**
   * @since 3.0.1
   */
  private TemplateRenderer templateRenderer;

  public void addHandlers(ReturnValueHandler... handlers) {
    Assert.notNull(handlers, "handler must not be null");
    Collections.addAll(this.handlers, handlers);
    sort();
  }

  public void addHandlers(List<ReturnValueHandler> handlers) {
    Assert.notNull(handlers, "handler must not be null");
    this.handlers.addAll(handlers);
    sort();
  }

  public void setHandlers(List<ReturnValueHandler> handlers) {
    Assert.notNull(handlers, "handler must not be null");
    this.handlers.clear();
    this.handlers.addAll(handlers);
    sort();
  }

  public void sort() {
    OrderUtils.reversedSort(handlers);
  }

  public List<ReturnValueHandler> getHandlers() {
    return handlers;
  }

  public RuntimeReturnValueHandler[] getRuntimeHandlers() {
    final ArrayList<RuntimeReturnValueHandler> ret = new ArrayList<>();
    for (final ReturnValueHandler handler : handlers) {
      if (handler instanceof RuntimeReturnValueHandler) {
        ret.add((RuntimeReturnValueHandler) handler);
      }
    }
    return ret.toArray(new RuntimeReturnValueHandler[0]);
  }

  public ReturnValueHandler getHandler(final Object handler) {
    Assert.notNull(handler, "handler must not be null");
    for (final ReturnValueHandler resolver : getHandlers()) {
      if (resolver.supportsHandler(handler)) {
        return resolver;
      }
    }
    return null;
  }

  /**
   * Get correspond view resolver, If there isn't a suitable resolver will be
   * throws {@link IllegalArgumentException}
   *
   * @return A suitable {@link ReturnValueHandler}
   */
  public ReturnValueHandler obtainHandler(final Object handler) {
    final ReturnValueHandler returnValueHandler = getHandler(handler);
    if (returnValueHandler == null) {
      throw new IllegalStateException("There isn't have a result resolver to resolve : [" + handler + "]");
    }
    return returnValueHandler;
  }

  //

  /**
   * init handlers
   */
  public void initHandlers(WebApplicationContext context) {
    Environment environment = context.getEnvironment();
    Integer bufferSize = environment.getProperty(DOWNLOAD_BUFF_SIZE, Integer.class);
    if (bufferSize != null) {
      setDownloadFileBufferSize(bufferSize);
    }
    // @since 3.0.3
    if (messageConverter == null) {
      setMessageConverter(context.getBean(MessageConverter.class));
    }
    if (redirectModelManager == null) {
      setRedirectModelManager(context.getBean(RedirectModelManager.class));
    }
    if (templateRenderer == null) {
      WebMvcConfiguration mvcConfiguration
              = new CompositeWebMvcConfiguration(context.getBeans(WebMvcConfiguration.class));
      setTemplateViewResolver(getTemplateResolver(context, mvcConfiguration));
    }
  }

  protected TemplateRenderer getTemplateResolver(WebApplicationContext context, WebMvcConfiguration mvcConfiguration) {
    TemplateRenderer templateResolver = context.getBean(TemplateRenderer.class);
    if (templateResolver == null) {
      context.registerBean(DefaultTemplateRenderer.class);
      templateResolver = context.getBean(TemplateRenderer.class);
    }

    if (templateResolver instanceof AbstractTemplateRenderer) {
      mvcConfiguration.configureTemplateViewResolver((AbstractTemplateRenderer) templateResolver);
    }
    return templateResolver;
  }

  /**
   * register default return-value handlers
   */
  public void registerDefaultResultHandlers() {
    registerDefaultResultHandlers(this.templateRenderer);
  }

  /**
   * register default {@link ReturnValueHandler}s
   *
   * @since 3.0
   */
  public void registerDefaultResultHandlers(TemplateRenderer templateRenderer) {
    log.info("Registering default return-value handlers");

    final List<ReturnValueHandler> handlers = getHandlers();
    final int bufferSize = getDownloadFileBufferSize();
    final MessageConverter messageConverter = obtainMessageConverter();
    Assert.state(messageConverter != null, "No MessageConverter in this web application");

    final RedirectModelManager modelManager = getRedirectModelManager();

    VoidReturnValueHandler voidResultHandler
            = new VoidReturnValueHandler(templateRenderer, messageConverter, bufferSize);
    ObjectReturnValueHandler objectResultHandler
            = new ObjectReturnValueHandler(templateRenderer, messageConverter, bufferSize);
    ModelAndViewReturnValueHandler modelAndViewResultHandler
            = new ModelAndViewReturnValueHandler(templateRenderer, messageConverter, bufferSize);
    ResponseEntityReturnValueHandler responseEntityResultHandler
            = new ResponseEntityReturnValueHandler(templateRenderer, messageConverter, bufferSize);
    TemplateReturnValueHandler templateResultHandler = new TemplateReturnValueHandler(templateRenderer);

    voidResultHandler.setModelManager(modelManager);
    objectResultHandler.setModelManager(modelManager);
    templateResultHandler.setModelManager(modelManager);
    modelAndViewResultHandler.setModelManager(modelManager);
    responseEntityResultHandler.setModelManager(modelManager);

    handlers.add(new RenderedImageReturnValueHandler());
    handlers.add(new ResourceReturnValueHandler(bufferSize));
    handlers.add(templateResultHandler);

    handlers.add(voidResultHandler);
    handlers.add(objectResultHandler);
    handlers.add(modelAndViewResultHandler);
    handlers.add(responseEntityResultHandler);

    handlers.add(new ResponseBodyReturnValueHandler(messageConverter));
    handlers.add(new HttpStatusReturnValueHandler());

    // ordering
    sort();
  }

  //

  public void setRedirectModelManager(RedirectModelManager redirectModelManager) {
    this.redirectModelManager = redirectModelManager;
  }

  public RedirectModelManager getRedirectModelManager() {
    return redirectModelManager;
  }

  public void setMessageConverter(MessageConverter messageConverter) {
    this.messageConverter = messageConverter;
  }

  public MessageConverter getMessageConverter() {
    return messageConverter;
  }

  /**
   * get MessageConverter or auto detect jackson and fast-json
   */
  private MessageConverter obtainMessageConverter() {
    MessageConverter messageConverter = getMessageConverter();
    if (messageConverter == null) {
      if (ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper")) {
        messageConverter = new ObjectNotationProcessorMessageConverter(new JacksonObjectNotationProcessor());
      }
      else if (ClassUtils.isPresent("com.alibaba.fastjson.JSON")) {
        messageConverter = new FastJSONMessageConverter();
      }
      if (messageConverter != null) {
        log.info("auto detect MessageConverter: [{}]", messageConverter);
      }
    }
    return messageConverter;
  }

  public void setDownloadFileBufferSize(int downloadFileBufferSize) {
    this.downloadFileBufferSize = downloadFileBufferSize;
  }

  public int getDownloadFileBufferSize() {
    return downloadFileBufferSize;
  }

  public void setTemplateViewResolver(TemplateRenderer templateRenderer) {
    this.templateRenderer = templateRenderer;
  }

  public TemplateRenderer getTemplateResolver() {
    return templateRenderer;
  }

}
