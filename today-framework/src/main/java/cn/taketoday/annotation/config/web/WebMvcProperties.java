/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2023 All Rights Reserved.
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

package cn.taketoday.annotation.config.web;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.taketoday.context.properties.ConfigurationProperties;
import cn.taketoday.http.MediaType;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Constant;
import cn.taketoday.lang.Nullable;
import cn.taketoday.validation.DefaultMessageCodesResolver;

/**
 * @author Phillip Webb
 * @author Sébastien Deleuze
 * @author Stephane Nicoll
 * @author Eddú Meléndez
 * @author Brian Clozel
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0 2022/2/16 15:34
 */
@ConfigurationProperties(prefix = "web.mvc")
public class WebMvcProperties {

  /**
   * Formatting strategy for message codes. For instance, 'PREFIX_ERROR_CODE'.
   */
  private DefaultMessageCodesResolver.Format messageCodesResolverFormat;

  private final Format format = new Format();

  /**
   * Whether to publish a RequestHandledEvent at the end of each request.
   *
   * @see cn.taketoday.web.context.support.RequestHandledEventPublisher
   */
  private boolean publishRequestHandledEvents = false;

  /**
   * Whether a "HandlerNotFoundException" should be thrown if no Handler was found to
   * process a request.
   */
  private boolean throwExceptionIfNoHandlerFound = false;

  /**
   * Whether logging of (potentially sensitive) request details at DEBUG and TRACE level
   * is allowed.
   */
  private boolean logRequestDetails;

  /**
   * Whether to enable warn logging of exceptions resolved by a
   * "HandlerExceptionHandler", except for "SimpleHandlerExceptionHandler".
   */
  private boolean logResolvedException = false;

  /**
   * Path pattern used for static resources.
   */
  private String staticPathPattern = "/**";

  /**
   * Path pattern used for WebJar assets.
   */
  private String webjarsPathPattern = "/webjars/**";

  private final Async async = new Async();

  private final Servlet servlet = new Servlet();

  private final View view = new View();

  private final Contentnegotiation contentnegotiation = new Contentnegotiation();

  public DefaultMessageCodesResolver.Format getMessageCodesResolverFormat() {
    return this.messageCodesResolverFormat;
  }

  public void setMessageCodesResolverFormat(DefaultMessageCodesResolver.Format messageCodesResolverFormat) {
    this.messageCodesResolverFormat = messageCodesResolverFormat;
  }

  public Format getFormat() {
    return this.format;
  }

  public boolean isPublishRequestHandledEvents() {
    return this.publishRequestHandledEvents;
  }

  public void setPublishRequestHandledEvents(boolean publishRequestHandledEvents) {
    this.publishRequestHandledEvents = publishRequestHandledEvents;
  }

  public boolean isThrowExceptionIfNoHandlerFound() {
    return this.throwExceptionIfNoHandlerFound;
  }

  public void setThrowExceptionIfNoHandlerFound(boolean throwExceptionIfNoHandlerFound) {
    this.throwExceptionIfNoHandlerFound = throwExceptionIfNoHandlerFound;
  }

  public boolean isLogRequestDetails() {
    return this.logRequestDetails;
  }

  public void setLogRequestDetails(boolean logRequestDetails) {
    this.logRequestDetails = logRequestDetails;
  }

  public boolean isLogResolvedException() {
    return this.logResolvedException;
  }

  public void setLogResolvedException(boolean logResolvedException) {
    this.logResolvedException = logResolvedException;
  }

  public String getStaticPathPattern() {
    return this.staticPathPattern;
  }

  public void setStaticPathPattern(String staticPathPattern) {
    this.staticPathPattern = staticPathPattern;
  }

  public String getWebjarsPathPattern() {
    return this.webjarsPathPattern;
  }

  public void setWebjarsPathPattern(String webjarsPathPattern) {
    this.webjarsPathPattern = webjarsPathPattern;
  }

  public Async getAsync() {
    return this.async;
  }

  public Servlet getServlet() {
    return this.servlet;
  }

  public View getView() {
    return this.view;
  }

  public Contentnegotiation getContentnegotiation() {
    return this.contentnegotiation;
  }

  public static class Async {

    /**
     * Amount of time before asynchronous request handling times out. If this value is
     * not set, the default timeout of the underlying implementation is used.
     */
    private Duration requestTimeout;

    public Duration getRequestTimeout() {
      return this.requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
    }

  }

  public static class Servlet {

    /**
     * Path of the dispatcher servlet. Setting a custom value for this property is not
     * compatible with the PathPatternParser matching strategy.
     */
    private String path = "/";

    /**
     * Load on startup priority of the dispatcher servlet.
     */
    private int loadOnStartup = -1;

    /**
     * @see jakarta.servlet.ServletContext#setRequestCharacterEncoding(String)
     */
    @Nullable
    private String requestEncoding = Constant.DEFAULT_ENCODING;

    /**
     * @see jakarta.servlet.ServletContext#setResponseCharacterEncoding(String)
     */
    @Nullable
    private String responseEncoding = Constant.DEFAULT_ENCODING;

    public void setRequestEncoding(@Nullable String requestEncoding) {
      this.requestEncoding = requestEncoding;
    }

    public void setResponseEncoding(@Nullable String responseEncoding) {
      this.responseEncoding = responseEncoding;
    }

    @Nullable
    public String getRequestEncoding() {
      return requestEncoding;
    }

    @Nullable
    public String getResponseEncoding() {
      return responseEncoding;
    }

    public String getPath() {
      return this.path;
    }

    public void setPath(String path) {
      Assert.notNull(path, "Path must not be null");
      Assert.isTrue(!path.contains("*"), "Path must not contain wildcards");
      this.path = path;
    }

    public int getLoadOnStartup() {
      return this.loadOnStartup;
    }

    public void setLoadOnStartup(int loadOnStartup) {
      this.loadOnStartup = loadOnStartup;
    }

    public String getServletMapping() {
      if (this.path.equals("") || this.path.equals("/")) {
        return "/";
      }
      if (this.path.endsWith("/")) {
        return this.path + "*";
      }
      return this.path + "/*";
    }

    public String getPath(String path) {
      String prefix = getServletPrefix();
      if (!path.startsWith("/")) {
        path = "/" + path;
      }
      return prefix + path;
    }

    public String getServletPrefix() {
      String result = this.path;
      int index = result.indexOf('*');
      if (index != -1) {
        result = result.substring(0, index);
      }
      if (result.endsWith("/")) {
        result = result.substring(0, result.length() - 1);
      }
      return result;
    }

  }

  public static class View {

    /**
     * Framework MVC view prefix.
     */
    private String prefix;

    /**
     * Framework MVC view suffix.
     */
    private String suffix;

    private boolean putAllOutputRedirectModel = true;

    public String getPrefix() {
      return this.prefix;
    }

    public void setPrefix(String prefix) {
      this.prefix = prefix;
    }

    public String getSuffix() {
      return this.suffix;
    }

    public void setSuffix(String suffix) {
      this.suffix = suffix;
    }

    public void setPutAllOutputRedirectModel(boolean putAllOutputRedirectModel) {
      this.putAllOutputRedirectModel = putAllOutputRedirectModel;
    }

    public boolean isPutAllOutputRedirectModel() {
      return putAllOutputRedirectModel;
    }
  }

  public static class Contentnegotiation {

    /**
     * Whether a request parameter ("format" by default) should be used to determine
     * the requested media type.
     */
    private boolean favorParameter = false;

    /**
     * Map file extensions to media types for content negotiation. For instance, yml
     * to text/yaml.
     */
    private Map<String, MediaType> mediaTypes = new LinkedHashMap<>();

    /**
     * Query parameter name to use when "favor-parameter" is enabled.
     */
    private String parameterName;

    public boolean isFavorParameter() {
      return this.favorParameter;
    }

    public void setFavorParameter(boolean favorParameter) {
      this.favorParameter = favorParameter;
    }

    public Map<String, MediaType> getMediaTypes() {
      return this.mediaTypes;
    }

    public void setMediaTypes(Map<String, MediaType> mediaTypes) {
      this.mediaTypes = mediaTypes;
    }

    public String getParameterName() {
      return this.parameterName;
    }

    public void setParameterName(String parameterName) {
      this.parameterName = parameterName;
    }

  }

  public static class Format {

    /**
     * Date format to use, for example 'dd/MM/yyyy'.
     */
    private String date;

    /**
     * Time format to use, for example 'HH:mm:ss'.
     */
    private String time;

    /**
     * Date-time format to use, for example 'yyyy-MM-dd HH:mm:ss'.
     */
    private String dateTime;

    public String getDate() {
      return this.date;
    }

    public void setDate(String date) {
      this.date = date;
    }

    public String getTime() {
      return this.time;
    }

    public void setTime(String time) {
      this.time = time;
    }

    public String getDateTime() {
      return this.dateTime;
    }

    public void setDateTime(String dateTime) {
      this.dateTime = dateTime;
    }

  }

}