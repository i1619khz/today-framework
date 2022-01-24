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

package cn.taketoday.web.util;

import java.net.URLDecoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Map;

import cn.taketoday.core.MultiValueMap;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.util.CollectionUtils;
import cn.taketoday.util.StringUtils;
import cn.taketoday.web.RequestContext;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.MappingMatch;

/**
 * Helper class for URL path matching. Provides support for URL paths in
 * {@code RequestDispatcher} includes and support for consistent URL decoding.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Rossen Stoyanchev
 * @see #getLookupPathForRequest
 * @see RequestDispatcher
 * @since 4.0
 */
public class UrlPathHelper {

  /**
   * Name of Servlet request attribute that holds a
   * {@link #getLookupPathForRequest resolved} lookupPath.
   */
  public static final String PATH_ATTRIBUTE = UrlPathHelper.class.getName() + ".PATH";

  private static final Logger logger = LoggerFactory.getLogger(UrlPathHelper.class);

  private boolean alwaysUseFullPath = false;

  private boolean urlDecode = true;

  private boolean removeSemicolonContent = true;

  private String defaultEncoding = WebUtils.DEFAULT_CHARACTER_ENCODING;

  private boolean readOnly = false;

  /**
   * Whether URL lookups should always use the full path within the current
   * web application context, i.e. within
   * {@link jakarta.servlet.ServletContext#getContextPath()}.
   * <p>If set to {@literal false} the path within the current servlet mapping
   * is used instead if applicable (i.e. in the case of a prefix based Servlet
   * mapping such as "/myServlet/*").
   * <p>By default this is set to "false".
   */
  public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
    checkReadOnly();
    this.alwaysUseFullPath = alwaysUseFullPath;
  }

  /**
   * Whether the context path and request URI should be decoded -- both of
   * which are returned <i>undecoded</i> by the Servlet API, in contrast to
   * the servlet path.
   * <p>Either the request encoding or the default Servlet spec encoding
   * (ISO-8859-1) is used when set to "true".
   * <p>By default this is set to {@literal true}.
   * <p><strong>Note:</strong> Be aware the servlet path will not match when
   * compared to encoded paths. Therefore use of {@code urlDecode=false} is
   * not compatible with a prefix-based Servlet mapping and likewise implies
   * also setting {@code alwaysUseFullPath=true}.
   *
   * @see #getServletPath
   * @see #getContextPath
   * @see #getRequestUri
   * @see WebUtils#DEFAULT_CHARACTER_ENCODING
   * @see ServletRequest#getCharacterEncoding()
   * @see URLDecoder#decode(String, String)
   */
  public void setUrlDecode(boolean urlDecode) {
    checkReadOnly();
    this.urlDecode = urlDecode;
  }

  /**
   * Whether to decode the request URI when determining the lookup path.
   *
   * @since 4.0
   */
  public boolean isUrlDecode() {
    return this.urlDecode;
  }

  /**
   * Set if ";" (semicolon) content should be stripped from the request URI.
   * <p>Default is "true".
   */
  public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
    checkReadOnly();
    this.removeSemicolonContent = removeSemicolonContent;
  }

  /**
   * Whether configured to remove ";" (semicolon) content from the request URI.
   */
  public boolean shouldRemoveSemicolonContent() {
    return this.removeSemicolonContent;
  }

  /**
   * Set the default character encoding to use for URL decoding.
   * Default is ISO-8859-1, according to the Servlet spec.
   * <p>If the request specifies a character encoding itself, the request
   * encoding will override this setting. This also allows for generically
   * overriding the character encoding in a filter that invokes the
   * {@code ServletRequest.setCharacterEncoding} method.
   *
   * @param defaultEncoding the character encoding to use
   * @see #determineEncoding
   * @see ServletRequest#getCharacterEncoding()
   * @see ServletRequest#setCharacterEncoding(String)
   * @see WebUtils#DEFAULT_CHARACTER_ENCODING
   */
  public void setDefaultEncoding(String defaultEncoding) {
    checkReadOnly();
    this.defaultEncoding = defaultEncoding;
  }

  /**
   * Return the default character encoding to use for URL decoding.
   */
  protected String getDefaultEncoding() {
    return this.defaultEncoding;
  }

  /**
   * Switch to read-only mode where further configuration changes are not allowed.
   */
  private void setReadOnly() {
    this.readOnly = true;
  }

  private void checkReadOnly() {
    Assert.isTrue(!this.readOnly, "This instance cannot be modified");
  }

  /**
   * {@link #getLookupPathForRequest Resolve} the lookupPath and cache it in a
   * request attribute with the key {@link #PATH_ATTRIBUTE} for subsequent
   * access via {@link #getResolvedLookupPath(ServletRequest)}.
   *
   * @param request the current request
   * @return the resolved path
   */
  public String resolveAndCacheLookupPath(HttpServletRequest request) {
    String lookupPath = getLookupPathForRequest(request);
    request.setAttribute(PATH_ATTRIBUTE, lookupPath);
    return lookupPath;
  }

  /**
   * Return a previously {@link #getLookupPathForRequest resolved} lookupPath.
   *
   * @param request the current request
   * @return the previously resolved lookupPath
   * @throws IllegalArgumentException if the not found
   */
  public static String getResolvedLookupPath(ServletRequest request) {
    String lookupPath = (String) request.getAttribute(PATH_ATTRIBUTE);
    Assert.notNull(lookupPath, "Expected lookupPath in request attribute \"" + PATH_ATTRIBUTE + "\".");
    return lookupPath;
  }

  /**
   * Return the mapping lookup path for the given request, within the current
   * servlet mapping if applicable, else within the web application.
   * <p>Detects include request URL if called within a RequestDispatcher include.
   *
   * @param request current HTTP request
   * @return the lookup path
   * @see #getPathWithinServletMapping
   * @see #getPathWithinApplication
   */
  public String getLookupPathForRequest(HttpServletRequest request) {
    String pathWithinApp = getPathWithinApplication(request);
    // Always use full path within current servlet context?
    if (this.alwaysUseFullPath || skipServletPathDetermination(request)) {
      return pathWithinApp;
    }
    // Else, use path within current servlet mapping if applicable
    String rest = getPathWithinServletMapping(request, pathWithinApp);
    if (StringUtils.isNotEmpty(rest)) {
      return rest;
    }
    else {
      return pathWithinApp;
    }
  }

  /**
   * Check whether servlet path determination can be skipped for the given request.
   *
   * @param request current HTTP request
   * @return {@code true} if the request mapping has not been achieved using a path
   * or if the servlet has been mapped to root; {@code false} otherwise
   */
  private boolean skipServletPathDetermination(HttpServletRequest request) {
    HttpServletMapping mapping = (HttpServletMapping) request.getAttribute(RequestDispatcher.INCLUDE_MAPPING);
    if (mapping == null) {
      mapping = request.getHttpServletMapping();
    }
    MappingMatch match = mapping.getMappingMatch();
    return match != null && (!match.equals(MappingMatch.PATH) || mapping.getPattern().equals("/*"));
  }

  /**
   * Return the path within the servlet mapping for the given request,
   * i.e. the part of the request's URL beyond the part that called the servlet,
   * or "" if the whole URL has been used to identify the servlet.
   *
   * @param request current HTTP request
   * @return the path within the servlet mapping, or ""
   * @see #getPathWithinServletMapping(HttpServletRequest, String)
   */
  public String getPathWithinServletMapping(HttpServletRequest request) {
    return getPathWithinServletMapping(request, getPathWithinApplication(request));
  }

  /**
   * Return the path within the servlet mapping for the given request,
   * i.e. the part of the request's URL beyond the part that called the servlet,
   * or "" if the whole URL has been used to identify the servlet.
   * <p>Detects include request URL if called within a RequestDispatcher include.
   * <p>E.g.: servlet mapping = "/*"; request URI = "/test/a" &rarr; "/test/a".
   * <p>E.g.: servlet mapping = "/"; request URI = "/test/a" &rarr; "/test/a".
   * <p>E.g.: servlet mapping = "/test/*"; request URI = "/test/a" &rarr; "/a".
   * <p>E.g.: servlet mapping = "/test"; request URI = "/test" &rarr; "".
   * <p>E.g.: servlet mapping = "/*.test"; request URI = "/a.test" &rarr; "".
   *
   * @param request current HTTP request
   * @param pathWithinApp a precomputed path within the application
   * @return the path within the servlet mapping, or ""
   * @see #getLookupPathForRequest
   */
  protected String getPathWithinServletMapping(HttpServletRequest request, String pathWithinApp) {
    String servletPath = getServletPath(request);
    String sanitizedPathWithinApp = getSanitizedPath(pathWithinApp);
    String path;

    // If the app container sanitized the servletPath, check against the sanitized version
    if (servletPath.contains(sanitizedPathWithinApp)) {
      path = getRemainingPath(sanitizedPathWithinApp, servletPath, false);
    }
    else {
      path = getRemainingPath(pathWithinApp, servletPath, false);
    }

    if (path != null) {
      // Normal case: URI contains servlet path.
      return path;
    }
    else {
      // Special case: URI is different from servlet path.
      String pathInfo = request.getPathInfo();
      if (pathInfo != null) {
        // Use path info if available. Indicates index page within a servlet mapping?
        // e.g. with index page: URI="/", servletPath="/index.html"
        return pathInfo;
      }
      if (!this.urlDecode) {
        // No path info... (not mapped by prefix, nor by extension, nor "/*")
        // For the default servlet mapping (i.e. "/"), urlDecode=false can
        // cause issues since getServletPath() returns a decoded path.
        // If decoding pathWithinApp yields a match just use pathWithinApp.
        path = getRemainingPath(decodeInternal(request, pathWithinApp), servletPath, false);
        if (path != null) {
          return pathWithinApp;
        }
      }
      // Otherwise, use the full servlet path.
      return servletPath;
    }
  }

  /**
   * Return the path within the web application for the given request.
   * <p>Detects include request URL if called within a RequestDispatcher include.
   *
   * @param request current HTTP request
   * @return the path within the web application
   * @see #getLookupPathForRequest
   */
  public String getPathWithinApplication(HttpServletRequest request) {
    String contextPath = getContextPath(request);
    String requestUri = getRequestUri(request);
    String path = getRemainingPath(requestUri, contextPath, true);
    if (path != null) {
      // Normal case: URI contains context path.
      return (StringUtils.hasText(path) ? path : "/");
    }
    else {
      return requestUri;
    }
  }

  /**
   * Match the given "mapping" to the start of the "requestUri" and if there
   * is a match return the extra part. This method is needed because the
   * context path and the servlet path returned by the HttpServletRequest are
   * stripped of semicolon content unlike the requestUri.
   */
  @Nullable
  private String getRemainingPath(String requestUri, String mapping, boolean ignoreCase) {
    int index1 = 0;
    int index2 = 0;
    for (; (index1 < requestUri.length()) && (index2 < mapping.length()); index1++, index2++) {
      char c1 = requestUri.charAt(index1);
      char c2 = mapping.charAt(index2);
      if (c1 == ';') {
        index1 = requestUri.indexOf('/', index1);
        if (index1 == -1) {
          return null;
        }
        c1 = requestUri.charAt(index1);
      }
      if (c1 == c2 || (ignoreCase && (Character.toLowerCase(c1) == Character.toLowerCase(c2)))) {
        continue;
      }
      return null;
    }
    if (index2 != mapping.length()) {
      return null;
    }
    else if (index1 == requestUri.length()) {
      return "";
    }
    else if (requestUri.charAt(index1) == ';') {
      index1 = requestUri.indexOf('/', index1);
    }
    return (index1 != -1 ? requestUri.substring(index1) : "");
  }

  /**
   * Sanitize the given path. Uses the following rules:
   * <ul>
   * <li>replace all "//" by "/"</li>
   * </ul>
   */
  public static String getSanitizedPath(final String path) {
    int index = path.indexOf("//");
    if (index >= 0) {
      StringBuilder sanitized = new StringBuilder(path);
      while (index != -1) {
        sanitized.deleteCharAt(index);
        index = sanitized.indexOf("//", index);
      }
      return sanitized.toString();
    }
    return path;
  }

  /**
   * Return the request URI for the given request, detecting an include request
   * URL if called within a RequestDispatcher include.
   * <p>As the value returned by {@code request.getRequestURI()} is <i>not</i>
   * decoded by the servlet container, this method will decode it.
   * <p>The URI that the web container resolves <i>should</i> be correct, but some
   * containers like JBoss/Jetty incorrectly include ";" strings like ";jsessionid"
   * in the URI. This method cuts off such incorrect appendices.
   *
   * @param request current HTTP request
   * @return the request URI
   */
  public String getRequestUri(HttpServletRequest request) {
    String uri = (String) request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI);
    if (uri == null) {
      uri = request.getRequestURI();
    }
    return decodeAndCleanUriString(request, uri);
  }

  /**
   * Return the context path for the given request, detecting an include request
   * URL if called within a RequestDispatcher include.
   * <p>As the value returned by {@code request.getContextPath()} is <i>not</i>
   * decoded by the servlet container, this method will decode it.
   *
   * @param request current HTTP request
   * @return the context path
   */
  public String getContextPath(HttpServletRequest request) {
    String contextPath = (String) request.getAttribute(RequestDispatcher.INCLUDE_CONTEXT_PATH);
    if (contextPath == null) {
      contextPath = request.getContextPath();
    }
    if (StringUtils.matchesCharacter(contextPath, '/')) {
      // Invalid case, but happens for includes on Jetty: silently adapt it.
      contextPath = "";
    }
    return decodeRequestString(request, contextPath);
  }

  /**
   * Return the servlet path for the given request, regarding an include request
   * URL if called within a RequestDispatcher include.
   * <p>As the value returned by {@code request.getServletPath()} is already
   * decoded by the servlet container, this method will not attempt to decode it.
   *
   * @param request current HTTP request
   * @return the servlet path
   */
  public String getServletPath(HttpServletRequest request) {
    String servletPath = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
    if (servletPath == null) {
      servletPath = request.getServletPath();
    }
    return servletPath;
  }

  public String getOriginatingRequestUri(RequestContext request) {
    String uri = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
    if (uri == null) {
      uri = request.getRequestPath();
    }
    return decodeAndCleanUriString(request, uri);
  }

  /**
   * Return the request URI for the given request. If this is a forwarded request,
   * correctly resolves to the request URI of the original request.
   */
  public String getOriginatingRequestUri(HttpServletRequest request) {
    String uri = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
    if (uri == null) {
      uri = request.getRequestURI();
    }
    return decodeAndCleanUriString(request, uri);
  }

  /**
   * Return the context path for the given request, detecting an include request
   * URL if called within a RequestDispatcher include.
   * <p>As the value returned by {@code request.getContextPath()} is <i>not</i>
   * decoded by the servlet container, this method will decode it.
   *
   * @param request current HTTP request
   * @return the context path
   */
  public String getOriginatingContextPath(HttpServletRequest request) {
    String contextPath = (String) request.getAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH);
    if (contextPath == null) {
      contextPath = request.getContextPath();
    }
    return decodeRequestString(request, contextPath);
  }

  /**
   * Return the servlet path for the given request, detecting an include request
   * URL if called within a RequestDispatcher include.
   *
   * @param request current HTTP request
   * @return the servlet path
   */
  public String getOriginatingServletPath(HttpServletRequest request) {
    String servletPath = (String) request.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
    if (servletPath == null) {
      servletPath = request.getServletPath();
    }
    return servletPath;
  }

  /**
   * Return the query string part of the given request's URL. If this is a forwarded request,
   * correctly resolves to the query string of the original request.
   *
   * @param request current HTTP request
   * @return the query string
   */
  public String getOriginatingQueryString(HttpServletRequest request) {
    if ((request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null) ||
            (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) != null)) {
      return (String) request.getAttribute(RequestDispatcher.FORWARD_QUERY_STRING);
    }
    else {
      return request.getQueryString();
    }
  }

  /**
   * Decode the supplied URI string and strips any extraneous portion after a ';'.
   */
  private String decodeAndCleanUriString(HttpServletRequest request, String uri) {
    uri = removeSemicolonContent(uri);
    uri = decodeRequestString(request, uri);
    uri = getSanitizedPath(uri);
    return uri;
  }

  /**
   * Decode the given source string with a URLDecoder. The encoding will be taken
   * from the request, falling back to the default "ISO-8859-1".
   * <p>The default implementation uses {@code URLDecoder.decode(input, enc)}.
   *
   * @param request current HTTP request
   * @param source the String to decode
   * @return the decoded String
   * @see WebUtils#DEFAULT_CHARACTER_ENCODING
   * @see ServletRequest#getCharacterEncoding
   * @see URLDecoder#decode(String, String)
   * @see URLDecoder#decode(String)
   */
  public String decodeRequestString(HttpServletRequest request, String source) {
    if (this.urlDecode) {
      return decodeInternal(request, source);
    }
    return source;
  }

  @SuppressWarnings("deprecation")
  private String decodeInternal(RequestContext request, String source) {
    String enc = getDefaultEncoding();
    try {
      return UriUtils.decode(source, enc);
    }
    catch (UnsupportedCharsetException ex) {
      if (logger.isWarnEnabled()) {
        logger.warn("Could not decode request string [" + source + "] with encoding '" + enc +
                "': falling back to platform default encoding; exception message: " + ex.getMessage());
      }
      return URLDecoder.decode(source);
    }
  }

  @Deprecated
  private String decodeInternal(HttpServletRequest request, String source) {
    String enc = determineEncoding(request);
    try {
      return UriUtils.decode(source, enc);
    }
    catch (UnsupportedCharsetException ex) {
      if (logger.isWarnEnabled()) {
        logger.warn("Could not decode request string [" + source + "] with encoding '" + enc +
                "': falling back to platform default encoding; exception message: " + ex.getMessage());
      }
      return URLDecoder.decode(source);
    }
  }

  /**
   * Determine the encoding for the given request.
   * Can be overridden in subclasses.
   * <p>The default implementation checks the request encoding,
   * falling back to the default encoding specified for this resolver.
   *
   * @param request current HTTP request
   * @return the encoding for the request (never {@code null})
   * @see ServletRequest#getCharacterEncoding()
   * @see #setDefaultEncoding
   */
  protected String determineEncoding(HttpServletRequest request) {
    String enc = request.getCharacterEncoding();
    if (enc == null) {
      enc = getDefaultEncoding();
    }
    return enc;
  }

  /**
   * Remove ";" (semicolon) content from the given request URI if the
   * {@linkplain #setRemoveSemicolonContent removeSemicolonContent}
   * property is set to "true". Note that "jsessionid" is always removed.
   *
   * @param requestUri the request URI string to remove ";" content from
   * @return the updated URI string
   */
  public String removeSemicolonContent(String requestUri) {
    return (this.removeSemicolonContent ?
            removeSemicolonContentInternal(requestUri) : removeJsessionid(requestUri));
  }

  private static String removeSemicolonContentInternal(String requestUri) {
    int semicolonIndex = requestUri.indexOf(';');
    if (semicolonIndex == -1) {
      return requestUri;
    }
    StringBuilder sb = new StringBuilder(requestUri);
    while (semicolonIndex != -1) {
      int slashIndex = sb.indexOf("/", semicolonIndex + 1);
      if (slashIndex == -1) {
        return sb.substring(0, semicolonIndex);
      }
      sb.delete(semicolonIndex, slashIndex);
      semicolonIndex = sb.indexOf(";", semicolonIndex);
    }
    return sb.toString();
  }

  private String removeJsessionid(String requestUri) {
    String key = ";jsessionid=";
    int index = requestUri.toLowerCase().indexOf(key);
    if (index == -1) {
      return requestUri;
    }
    String start = requestUri.substring(0, index);
    for (int i = index + key.length(); i < requestUri.length(); i++) {
      char c = requestUri.charAt(i);
      if (c == ';' || c == '/') {
        return start + requestUri.substring(i);
      }
    }
    return start;
  }

  /**
   * Decode the given URI path variables via {@link #decodeRequestString} unless
   * {@link #setUrlDecode} is set to {@code true} in which case it is assumed
   * the URL path from which the variables were extracted is already decoded
   * through a call to {@link #getLookupPathForRequest(HttpServletRequest)}.
   *
   * @param request current HTTP request
   * @param vars the URI variables extracted from the URL path
   * @return the same Map or a new Map instance
   */
  public Map<String, String> decodePathVariables(HttpServletRequest request, Map<String, String> vars) {
    if (this.urlDecode) {
      return vars;
    }
    else {
      Map<String, String> decodedVars = CollectionUtils.newLinkedHashMap(vars.size());
      vars.forEach((key, value) -> decodedVars.put(key, decodeInternal(request, value)));
      return decodedVars;
    }
  }

  /**
   * Decode the given matrix variables via {@link #decodeRequestString} unless
   * {@link #setUrlDecode} is set to {@code true} in which case it is assumed
   * the URL path from which the variables were extracted is already decoded
   * through a call to {@link #getLookupPathForRequest(HttpServletRequest)}.
   *
   * @param request current HTTP request context
   * @param vars the URI variables extracted from the URL path
   * @return the same Map or a new Map instance
   */
  public MultiValueMap<String, String> decodeMatrixVariables(
          RequestContext request, MultiValueMap<String, String> vars) {
    if (this.urlDecode) {
      return vars;
    }
    else {
      MultiValueMap<String, String> decodedVars = MultiValueMap.fromLinkedHashMap(vars.size());
      for (Map.Entry<String, List<String>> entry : vars.entrySet()) {
        String key = entry.getKey();
        List<String> values = entry.getValue();
        for (String value : values) {
          decodedVars.add(key, decodeInternal(request, value));
        }
      }
      return decodedVars;
    }
  }

  /**
   * Shared, read-only instance with defaults. The following apply:
   * <ul>
   * <li>{@code alwaysUseFullPath=false}
   * <li>{@code urlDecode=true}
   * <li>{@code removeSemicolon=true}
   * <li>{@code defaultEncoding=}{@link WebUtils#DEFAULT_CHARACTER_ENCODING}
   * </ul>
   */
  public static final UrlPathHelper defaultInstance = new UrlPathHelper();

  static {
    defaultInstance.setReadOnly();
  }

  /**
   * Shared, read-only instance for the full, encoded path. The following apply:
   * <ul>
   * <li>{@code alwaysUseFullPath=true}
   * <li>{@code urlDecode=false}
   * <li>{@code removeSemicolon=false}
   * <li>{@code defaultEncoding=}{@link WebUtils#DEFAULT_CHARACTER_ENCODING}
   * </ul>
   */
  public static final UrlPathHelper rawPathInstance = new UrlPathHelper() {

    @Override
    public String removeSemicolonContent(String requestUri) {
      return requestUri;
    }
  };

  static {
    rawPathInstance.setAlwaysUseFullPath(true);
    rawPathInstance.setUrlDecode(false);
    rawPathInstance.setRemoveSemicolonContent(false);
    rawPathInstance.setReadOnly();
  }

}
