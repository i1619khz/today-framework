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

package cn.taketoday.web.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import cn.taketoday.core.ParameterizedTypeReference;
import cn.taketoday.core.ResolvableType;
import cn.taketoday.http.HttpHeaders;
import cn.taketoday.http.HttpMethod;
import cn.taketoday.http.HttpRequest;
import cn.taketoday.http.HttpStatusCode;
import cn.taketoday.http.MediaType;
import cn.taketoday.http.ResponseEntity;
import cn.taketoday.http.StreamingHttpOutputMessage;
import cn.taketoday.http.client.ClientHttpRequest;
import cn.taketoday.http.client.ClientHttpRequestFactory;
import cn.taketoday.http.client.ClientHttpRequestInitializer;
import cn.taketoday.http.client.ClientHttpRequestInterceptor;
import cn.taketoday.http.client.ClientHttpResponse;
import cn.taketoday.http.client.InterceptingClientHttpRequestFactory;
import cn.taketoday.http.converter.GenericHttpMessageConverter;
import cn.taketoday.http.converter.HttpMessageConverter;
import cn.taketoday.http.converter.HttpMessageNotReadableException;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.NullValue;
import cn.taketoday.lang.Nullable;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.util.CollectionUtils;
import cn.taketoday.web.util.UriBuilder;
import cn.taketoday.web.util.UriBuilderFactory;

/**
 * Default implementation of {@link RestClient}.
 *
 * @author Arjen Poutsma
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
final class DefaultRestClient implements RestClient {

  private static final Logger logger = LoggerFactory.getLogger(DefaultRestClient.class);

  private final ClientHttpRequestFactory clientRequestFactory;

  @Nullable
  private volatile ClientHttpRequestFactory interceptingRequestFactory;

  @Nullable
  private final List<ClientHttpRequestInitializer> initializers;

  @Nullable
  private final List<ClientHttpRequestInterceptor> interceptors;

  private final UriBuilderFactory uriBuilderFactory;

  @Nullable
  private final HttpHeaders defaultHeaders;

  private final List<StatusHandler> defaultStatusHandlers;

  private final DefaultRestClientBuilder builder;

  private final List<HttpMessageConverter<?>> messageConverters;

  @Nullable
  private final Consumer<RequestHeadersSpec<?>> defaultRequest;

  DefaultRestClient(ClientHttpRequestFactory clientRequestFactory,
          @Nullable List<ClientHttpRequestInterceptor> interceptors,
          @Nullable List<ClientHttpRequestInitializer> initializers,
          UriBuilderFactory uriBuilderFactory, @Nullable HttpHeaders defaultHeaders,
          @Nullable Consumer<RequestHeadersSpec<?>> defaultRequest, @Nullable List<StatusHandler> statusHandlers,
          List<HttpMessageConverter<?>> messageConverters, DefaultRestClientBuilder builder) {

    this.clientRequestFactory = clientRequestFactory;
    this.initializers = initializers;
    this.interceptors = interceptors;
    this.uriBuilderFactory = uriBuilderFactory;
    this.defaultHeaders = defaultHeaders;
    this.defaultRequest = defaultRequest;
    this.defaultStatusHandlers = (statusHandlers != null) ? new ArrayList<>(statusHandlers) : new ArrayList<>();
    this.messageConverters = messageConverters;
    this.builder = builder;
  }

  @Override
  public RequestHeadersUriSpec<?> get() {
    return methodInternal(HttpMethod.GET);
  }

  @Override
  public RequestHeadersUriSpec<?> head() {
    return methodInternal(HttpMethod.HEAD);
  }

  @Override
  public RequestBodyUriSpec post() {
    return methodInternal(HttpMethod.POST);
  }

  @Override
  public RequestBodyUriSpec put() {
    return methodInternal(HttpMethod.PUT);
  }

  @Override
  public RequestBodyUriSpec patch() {
    return methodInternal(HttpMethod.PATCH);
  }

  @Override
  public RequestBodyUriSpec delete() {
    return methodInternal(HttpMethod.DELETE);
  }

  @Override
  public RequestHeadersUriSpec<?> options() {
    return methodInternal(HttpMethod.OPTIONS);
  }

  @Override
  public RequestBodyUriSpec method(HttpMethod method) {
    Assert.notNull(method, "HttpMethod is required");
    return methodInternal(method);
  }

  private RequestBodyUriSpec methodInternal(HttpMethod httpMethod) {
    DefaultRequestBodyUriSpec spec = new DefaultRequestBodyUriSpec(httpMethod);
    if (this.defaultRequest != null) {
      this.defaultRequest.accept(spec);
    }
    return spec;
  }

  @Override
  public Builder mutate() {
    return new DefaultRestClientBuilder(this.builder);
  }

  @Nullable
  @SuppressWarnings({ "rawtypes", "unchecked" })
  private <T> T readWithMessageConverters(ClientHttpResponse clientResponse, @Nullable Runnable callback, Type bodyType, Class<T> bodyClass) {
    MediaType contentType = getContentType(clientResponse);

    try (IntrospectingClientHttpResponse responseWrapper = new IntrospectingClientHttpResponse(clientResponse)) {
      if (callback != null) {
        callback.run();
      }

      if (!responseWrapper.hasMessageBody() || responseWrapper.hasEmptyMessageBody()) {
        return null;
      }

      for (HttpMessageConverter<?> messageConverter : this.messageConverters) {
        if (messageConverter instanceof GenericHttpMessageConverter genericHttpMessageConverter) {
          if (genericHttpMessageConverter.canRead(bodyType, null, contentType)) {
            if (logger.isDebugEnabled()) {
              logger.debug("Reading to [{}]", ResolvableType.forType(bodyType));
            }
            return (T) genericHttpMessageConverter.read(bodyType, null, responseWrapper);
          }
        }
        if (messageConverter.canRead(bodyClass, contentType)) {
          if (logger.isDebugEnabled()) {
            logger.debug("Reading to [{}] as \"{}\"", bodyClass.getName(), contentType);
          }
          return (T) messageConverter.read((Class) bodyClass, responseWrapper);
        }
      }
      throw new UnknownContentTypeException(bodyType, contentType,
              responseWrapper.getStatusCode(), responseWrapper.getStatusText(),
              responseWrapper.getHeaders(), RestClientUtils.getBody(responseWrapper));
    }
    catch (UncheckedIOException | IOException | HttpMessageNotReadableException ex) {
      Throwable cause;
      if (ex instanceof UncheckedIOException uncheckedIOException) {
        cause = uncheckedIOException.getCause();
      }
      else {
        cause = ex;
      }
      throw new RestClientException("Error while extracting response for type [%s] and content type [%s]"
              .formatted(ResolvableType.forType(bodyType), contentType), cause);
    }
  }

  private static MediaType getContentType(ClientHttpResponse clientResponse) {
    MediaType contentType = clientResponse.getHeaders().getContentType();
    if (contentType == null) {
      contentType = MediaType.APPLICATION_OCTET_STREAM;
    }
    return contentType;
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> bodyClass(Type type) {
    if (type instanceof Class<?> clazz) {
      return (Class<T>) clazz;
    }
    if (type instanceof ParameterizedType parameterizedType &&
            parameterizedType.getRawType() instanceof Class<?> rawType) {
      return (Class<T>) rawType;
    }
    return (Class<T>) Object.class;
  }

  private class DefaultRequestBodyUriSpec implements RequestBodyUriSpec {

    private final HttpMethod httpMethod;

    @Nullable
    private URI uri;

    @Nullable
    private HttpHeaders headers;

    @Nullable
    private InternalBody body;

    @Nullable
    private Consumer<ClientHttpRequest> httpRequestConsumer;

    public DefaultRequestBodyUriSpec(HttpMethod httpMethod) {
      this.httpMethod = httpMethod;
    }

    @Override
    public RequestBodySpec uri(String uriTemplate, Object... uriVariables) {
      return uri(DefaultRestClient.this.uriBuilderFactory.expand(uriTemplate, uriVariables));
    }

    @Override
    public RequestBodySpec uri(String uriTemplate, Map<String, ?> uriVariables) {
      return uri(DefaultRestClient.this.uriBuilderFactory.expand(uriTemplate, uriVariables));
    }

    @Override
    public RequestBodySpec uri(String uriTemplate, Function<UriBuilder, URI> uriFunction) {
      return uri(uriFunction.apply(DefaultRestClient.this.uriBuilderFactory.uriString(uriTemplate)));
    }

    @Override
    public RequestBodySpec uri(Function<UriBuilder, URI> uriFunction) {
      return uri(uriFunction.apply(DefaultRestClient.this.uriBuilderFactory.builder()));
    }

    @Override
    public RequestBodySpec uri(URI uri) {
      this.uri = uri;
      return this;
    }

    private HttpHeaders getHeaders() {
      if (this.headers == null) {
        this.headers = HttpHeaders.forWritable();
      }
      return this.headers;
    }

    @Override
    public DefaultRequestBodyUriSpec header(String headerName, String... headerValues) {
      for (String headerValue : headerValues) {
        getHeaders().add(headerName, headerValue);
      }
      return this;
    }

    @Override
    public DefaultRequestBodyUriSpec headers(Consumer<HttpHeaders> headersConsumer) {
      headersConsumer.accept(getHeaders());
      return this;
    }

    @Override
    public DefaultRequestBodyUriSpec accept(MediaType... acceptableMediaTypes) {
      getHeaders().setAccept(Arrays.asList(acceptableMediaTypes));
      return this;
    }

    @Override
    public DefaultRequestBodyUriSpec acceptCharset(Charset... acceptableCharsets) {
      getHeaders().setAcceptCharset(Arrays.asList(acceptableCharsets));
      return this;
    }

    @Override
    public DefaultRequestBodyUriSpec contentType(MediaType contentType) {
      getHeaders().setContentType(contentType);
      return this;
    }

    @Override
    public DefaultRequestBodyUriSpec contentLength(long contentLength) {
      getHeaders().setContentLength(contentLength);
      return this;
    }

    @Override
    public DefaultRequestBodyUriSpec ifModifiedSince(ZonedDateTime ifModifiedSince) {
      getHeaders().setIfModifiedSince(ifModifiedSince);
      return this;
    }

    @Override
    public DefaultRequestBodyUriSpec ifNoneMatch(String... ifNoneMatches) {
      getHeaders().setIfNoneMatch(Arrays.asList(ifNoneMatches));
      return this;
    }

    @Override
    public RequestBodySpec httpRequest(Consumer<ClientHttpRequest> requestConsumer) {
      this.httpRequestConsumer = (this.httpRequestConsumer != null ?
              this.httpRequestConsumer.andThen(requestConsumer) : requestConsumer);
      return this;
    }

    @Override
    public RequestBodySpec body(Object body) {
      this.body = clientHttpRequest -> writeWithMessageConverters(body, body.getClass(), clientHttpRequest);
      return this;
    }

    @Override
    public <T> RequestBodySpec body(T body, ParameterizedTypeReference<T> bodyType) {
      this.body = clientHttpRequest -> writeWithMessageConverters(body, bodyType.getType(), clientHttpRequest);
      return this;
    }

    @Override
    public RequestBodySpec body(StreamingHttpOutputMessage.Body body) {
      this.body = request -> body.writeTo(request.getBody());
      return this;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void writeWithMessageConverters(Object body, Type bodyType, ClientHttpRequest clientRequest)
            throws IOException {

      MediaType contentType = clientRequest.getHeaders().getContentType();
      Class<?> bodyClass = body.getClass();

      for (HttpMessageConverter messageConverter : DefaultRestClient.this.messageConverters) {
        if (messageConverter instanceof GenericHttpMessageConverter genericMessageConverter) {
          if (genericMessageConverter.canWrite(bodyType, bodyClass, contentType)) {
            logBody(body, contentType, genericMessageConverter);
            genericMessageConverter.write(body, bodyType, contentType, clientRequest);
            return;
          }
        }
        if (messageConverter.canWrite(bodyClass, contentType)) {
          logBody(body, contentType, messageConverter);
          messageConverter.write(body, contentType, clientRequest);
          return;
        }
      }
      String message = "No HttpMessageConverter for " + bodyClass.getName();
      if (contentType != null) {
        message += " and content type \"%s\"".formatted(contentType);
      }
      throw new RestClientException(message);
    }

    private void logBody(Object body, @Nullable MediaType mediaType, HttpMessageConverter<?> converter) {
      if (logger.isDebugEnabled()) {
        StringBuilder msg = new StringBuilder("Writing [");
        msg.append(body);
        msg.append("] ");
        if (mediaType != null) {
          msg.append("as \"");
          msg.append(mediaType);
          msg.append("\" ");
        }
        msg.append("with ");
        msg.append(converter.getClass().getName());
        logger.debug(msg.toString());
      }
    }

    @Override
    public ResponseSpec retrieve() {
      return exchangeInternal(DefaultResponseSpec::new, false);
    }

    @Override
    public void execute() {
      execute(true);
    }

    @Override
    public void execute(boolean close) {
      exchangeInternal((clientRequest, clientResponse) -> NullValue.INSTANCE, close);
    }

    @Override
    public <T> T exchange(ExchangeFunction<T> exchangeFunction, boolean close) {
      return exchangeInternal(exchangeFunction, close);
    }

    private <T> T exchangeInternal(ExchangeFunction<T> exchangeFunction, boolean close) {
      Assert.notNull(exchangeFunction, "ExchangeFunction is required");

      ClientHttpResponse clientResponse = null;
      URI uri = null;
      try {
        uri = initUri();
        HttpHeaders headers = initHeaders();
        ClientHttpRequest clientRequest = createRequest(uri);
        clientRequest.getHeaders().addAll(headers);
        if (this.body != null) {
          this.body.writeTo(clientRequest);
        }
        if (this.httpRequestConsumer != null) {
          this.httpRequestConsumer.accept(clientRequest);
        }
        clientResponse = clientRequest.execute();
        ConvertibleClientHttpResponse convertibleWrapper = new DefaultConvertibleClientHttpResponse(clientResponse);
        return exchangeFunction.exchange(clientRequest, convertibleWrapper);
      }
      catch (IOException ex) {
        throw createResourceAccessException(uri, this.httpMethod, ex);
      }
      finally {
        if (close && clientResponse != null) {
          clientResponse.close();
        }
      }
    }

    private URI initUri() {
      return (this.uri != null ? this.uri : DefaultRestClient.this.uriBuilderFactory.expand(""));
    }

    private HttpHeaders initHeaders() {
      HttpHeaders defaultHeaders = DefaultRestClient.this.defaultHeaders;
      if (CollectionUtils.isEmpty(this.headers)) {
        return (defaultHeaders != null ? defaultHeaders : HttpHeaders.forWritable());
      }
      else if (CollectionUtils.isEmpty(defaultHeaders)) {
        return this.headers;
      }
      else {
        HttpHeaders result = HttpHeaders.forWritable();
        result.putAll(defaultHeaders);
        result.putAll(this.headers);
        return result;
      }
    }

    private ClientHttpRequest createRequest(URI uri) throws IOException {
      ClientHttpRequestFactory factory;
      if (DefaultRestClient.this.interceptors != null) {
        factory = DefaultRestClient.this.interceptingRequestFactory;
        if (factory == null) {
          factory = new InterceptingClientHttpRequestFactory(DefaultRestClient.this.clientRequestFactory, DefaultRestClient.this.interceptors);
          DefaultRestClient.this.interceptingRequestFactory = factory;
        }
      }
      else {
        factory = DefaultRestClient.this.clientRequestFactory;
      }
      ClientHttpRequest request = factory.createRequest(uri, this.httpMethod);
      if (DefaultRestClient.this.initializers != null) {
        DefaultRestClient.this.initializers.forEach(initializer -> initializer.initialize(request));
      }
      return request;
    }

    private static ResourceAccessException createResourceAccessException(URI url, HttpMethod method, IOException ex) {
      StringBuilder msg = new StringBuilder("I/O error on ");
      msg.append(method.name());
      msg.append(" request for \"");
      String urlString = url.toString();
      int idx = urlString.indexOf('?');
      if (idx != -1) {
        msg.append(urlString, 0, idx);
      }
      else {
        msg.append(urlString);
      }
      msg.append("\": ");
      msg.append(ex.getMessage());
      return new ResourceAccessException(msg.toString(), ex);
    }

    @FunctionalInterface
    private interface InternalBody {

      void writeTo(ClientHttpRequest request) throws IOException;
    }
  }

  private class DefaultResponseSpec implements ResponseSpec {

    private final HttpRequest clientRequest;

    private final ClientHttpResponse clientResponse;

    private final ArrayList<StatusHandler> statusHandlers = new ArrayList<>(1);

    private final int defaultStatusHandlerCount;

    DefaultResponseSpec(HttpRequest clientRequest, ClientHttpResponse clientResponse) {
      this.clientRequest = clientRequest;
      this.clientResponse = clientResponse;
      this.statusHandlers.addAll(defaultStatusHandlers);
      this.statusHandlers.add(StatusHandler.defaultHandler(messageConverters));
      this.defaultStatusHandlerCount = this.statusHandlers.size();
    }

    @Override
    public ResponseSpec onStatus(Predicate<HttpStatusCode> statusPredicate, ErrorHandler errorHandler) {
      Assert.notNull(errorHandler, "ErrorHandler is required");
      Assert.notNull(statusPredicate, "StatusPredicate is required");
      return onStatusInternal(StatusHandler.of(statusPredicate, errorHandler));
    }

    @Override
    public ResponseSpec onStatus(ResponseErrorHandler errorHandler) {
      Assert.notNull(errorHandler, "ResponseErrorHandler is required");
      return onStatusInternal(StatusHandler.fromErrorHandler(errorHandler));
    }

    private ResponseSpec onStatusInternal(StatusHandler statusHandler) {
      Assert.notNull(statusHandler, "StatusHandler is required");

      int index = this.statusHandlers.size() - this.defaultStatusHandlerCount;  // Default handlers always last
      this.statusHandlers.add(index, statusHandler);
      return this;
    }

    @Override
    public <T> T body(Class<T> bodyType) {
      return readBody(bodyType, bodyType);
    }

    @Override
    public <T> T body(ParameterizedTypeReference<T> bodyType) {
      Type type = bodyType.getType();
      Class<T> bodyClass = bodyClass(type);
      return readBody(type, bodyClass);
    }

    @Override
    public <T> ResponseEntity<T> toEntity(Class<T> bodyType) {
      return toEntityInternal(bodyType, bodyType);
    }

    @Override
    public <T> ResponseEntity<T> toEntity(ParameterizedTypeReference<T> bodyType) {
      Type type = bodyType.getType();
      Class<T> bodyClass = bodyClass(type);
      return toEntityInternal(type, bodyClass);
    }

    private <T> ResponseEntity<T> toEntityInternal(Type bodyType, Class<T> bodyClass) {
      T body = readBody(bodyType, bodyClass);
      try {
        return ResponseEntity.status(this.clientResponse.getStatusCode())
                .headers(this.clientResponse.getHeaders())
                .body(body);
      }
      catch (IOException ex) {
        throw new ResourceAccessException("Could not retrieve response status code: " + ex.getMessage(), ex);
      }
    }

    @Override
    public ResponseEntity<Void> toBodilessEntity() {
      try (this.clientResponse) {
        applyStatusHandlers();
        return ResponseEntity.status(this.clientResponse.getStatusCode())
                .headers(this.clientResponse.getHeaders())
                .build();
      }
      catch (UncheckedIOException ex) {
        throw new ResourceAccessException("Could not retrieve response status code: " + ex.getMessage(), ex.getCause());
      }
      catch (IOException ex) {
        throw new ResourceAccessException("Could not retrieve response status code: " + ex.getMessage(), ex);
      }
    }

    private <T> T readBody(Type bodyType, Class<T> bodyClass) {
      return readWithMessageConverters(this.clientResponse, this::applyStatusHandlers, bodyType, bodyClass);
    }

    private void applyStatusHandlers() {
      try {
        ClientHttpResponse response = this.clientResponse;
        if (response instanceof DefaultConvertibleClientHttpResponse convertibleResponse) {
          response = convertibleResponse.delegate;
        }
        for (StatusHandler handler : this.statusHandlers) {
          if (handler.test(response)) {
            handler.handle(this.clientRequest, response);
            return;
          }
        }
      }
      catch (IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }

  private class DefaultConvertibleClientHttpResponse implements RequestHeadersSpec.ConvertibleClientHttpResponse {

    private final ClientHttpResponse delegate;

    public DefaultConvertibleClientHttpResponse(ClientHttpResponse delegate) {
      this.delegate = delegate;
    }

    @Nullable
    @Override
    public <T> T bodyTo(Class<T> bodyType) {
      return readWithMessageConverters(this.delegate, null, bodyType, bodyType);
    }

    @Nullable
    @Override
    public <T> T bodyTo(ParameterizedTypeReference<T> bodyType) {
      Type type = bodyType.getType();
      Class<T> bodyClass = bodyClass(type);
      return readWithMessageConverters(this.delegate, null, type, bodyClass);
    }

    @Override
    public InputStream getBody() throws IOException {
      return this.delegate.getBody();
    }

    @Override
    public HttpHeaders getHeaders() {
      return this.delegate.getHeaders();
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
      return this.delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return this.delegate.getStatusText();
    }

    @Override
    public void close() {
      this.delegate.close();
    }

  }

}
