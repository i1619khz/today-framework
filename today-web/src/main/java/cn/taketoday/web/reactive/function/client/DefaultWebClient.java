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

package cn.taketoday.web.reactive.function.client;

import org.reactivestreams.Publisher;

import java.net.URI;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import cn.taketoday.core.TypeReference;
import cn.taketoday.http.HttpHeaders;
import cn.taketoday.http.HttpMethod;
import cn.taketoday.http.HttpRequest;
import cn.taketoday.http.HttpStatusCode;
import cn.taketoday.http.MediaType;
import cn.taketoday.http.ResponseEntity;
import cn.taketoday.http.client.reactive.ClientHttpRequest;
import cn.taketoday.http.client.reactive.ClientHttpResponse;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.CollectionUtils;
import cn.taketoday.util.LinkedMultiValueMap;
import cn.taketoday.util.MultiValueMap;
import cn.taketoday.web.reactive.function.BodyExtractor;
import cn.taketoday.web.reactive.function.BodyInserter;
import cn.taketoday.web.reactive.function.BodyInserters;
import cn.taketoday.web.util.UriBuilder;
import cn.taketoday.web.util.UriBuilderFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Default implementation of {@link WebClient}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Sebastien Deleuze
 * @since 4.0
 */
class DefaultWebClient implements WebClient {

  private static final String URI_TEMPLATE_ATTRIBUTE = WebClient.class.getName() + ".uriTemplate";

  private static final Mono<ClientResponse> NO_HTTP_CLIENT_RESPONSE_ERROR = Mono.error(
          () -> new IllegalStateException("The underlying HTTP client completed without emitting a response."));

  private final ExchangeFunction exchangeFunction;

  private final UriBuilderFactory uriBuilderFactory;

  @Nullable
  private final HttpHeaders defaultHeaders;

  @Nullable
  private final MultiValueMap<String, String> defaultCookies;

  @Nullable
  private final Consumer<RequestHeadersSpec<?>> defaultRequest;

  private final DefaultWebClientBuilder builder;

  private final List<DefaultResponseSpec.StatusHandler> defaultStatusHandlers;

  DefaultWebClient(ExchangeFunction exchangeFunction, UriBuilderFactory uriBuilderFactory,
          @Nullable HttpHeaders defaultHeaders, @Nullable MultiValueMap<String, String> defaultCookies,
          @Nullable Consumer<RequestHeadersSpec<?>> defaultRequest,
          @Nullable Map<Predicate<HttpStatusCode>, Function<ClientResponse, Mono<? extends Throwable>>> statusHandlerMap,
          DefaultWebClientBuilder builder) {

    this.exchangeFunction = exchangeFunction;
    this.uriBuilderFactory = uriBuilderFactory;
    this.defaultHeaders = defaultHeaders;
    this.defaultCookies = defaultCookies;
    this.defaultRequest = defaultRequest;
    this.defaultStatusHandlers = initStatusHandlers(statusHandlerMap);
    this.builder = builder;
  }

  private static List<DefaultResponseSpec.StatusHandler> initStatusHandlers(
          @Nullable Map<Predicate<HttpStatusCode>, Function<ClientResponse, Mono<? extends Throwable>>> handlerMap) {

    return (CollectionUtils.isEmpty(handlerMap) ? Collections.emptyList() :
            handlerMap.entrySet().stream()
                    .map(entry -> new DefaultResponseSpec.StatusHandler(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList()));
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
  public RequestHeadersUriSpec<?> delete() {
    return methodInternal(HttpMethod.DELETE);
  }

  @Override
  public RequestHeadersUriSpec<?> options() {
    return methodInternal(HttpMethod.OPTIONS);
  }

  @Override
  public RequestBodyUriSpec method(HttpMethod httpMethod) {
    return methodInternal(httpMethod);
  }

  private RequestBodyUriSpec methodInternal(HttpMethod httpMethod) {
    return new DefaultRequestBodyUriSpec(httpMethod);
  }

  @Override
  public Builder mutate() {
    return new DefaultWebClientBuilder(this.builder);
  }

  private static Mono<Void> releaseIfNotConsumed(ClientResponse response) {
    return response.releaseBody().onErrorResume(ex2 -> Mono.empty());
  }

  private static <T> Mono<T> releaseIfNotConsumed(ClientResponse response, Throwable ex) {
    return response.releaseBody().onErrorResume(ex2 -> Mono.empty()).then(Mono.error(ex));
  }

  private class DefaultRequestBodyUriSpec implements RequestBodyUriSpec {

    private final HttpMethod httpMethod;

    @Nullable
    private URI uri;

    @Nullable
    private HttpHeaders headers;

    @Nullable
    private MultiValueMap<String, String> cookies;

    @Nullable
    private BodyInserter<?, ? super ClientHttpRequest> inserter;

    private final Map<String, Object> attributes = new LinkedHashMap<>(4);

    @Nullable
    private Function<Context, Context> contextModifier;

    @Nullable
    private Consumer<ClientHttpRequest> httpRequestConsumer;

    DefaultRequestBodyUriSpec(HttpMethod httpMethod) {
      this.httpMethod = httpMethod;
    }

    @Override
    public RequestBodySpec uri(String uriTemplate, Object... uriVariables) {
      attribute(URI_TEMPLATE_ATTRIBUTE, uriTemplate);
      return uri(uriBuilderFactory.expand(uriTemplate, uriVariables));
    }

    @Override
    public RequestBodySpec uri(String uriTemplate, Map<String, ?> uriVariables) {
      attribute(URI_TEMPLATE_ATTRIBUTE, uriTemplate);
      return uri(uriBuilderFactory.expand(uriTemplate, uriVariables));
    }

    @Override
    public RequestBodySpec uri(String uriTemplate, Function<UriBuilder, URI> uriFunction) {
      attribute(URI_TEMPLATE_ATTRIBUTE, uriTemplate);
      return uri(uriFunction.apply(uriBuilderFactory.uriString(uriTemplate)));
    }

    @Override
    public RequestBodySpec uri(Function<UriBuilder, URI> uriFunction) {
      return uri(uriFunction.apply(uriBuilderFactory.builder()));
    }

    @Override
    public RequestBodySpec uri(URI uri) {
      this.uri = uri;
      return this;
    }

    private HttpHeaders getHeaders() {
      if (this.headers == null) {
        this.headers = HttpHeaders.create();
      }
      return this.headers;
    }

    private MultiValueMap<String, String> getCookies() {
      if (this.cookies == null) {
        this.cookies = new LinkedMultiValueMap<>(3);
      }
      return this.cookies;
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
    public DefaultRequestBodyUriSpec cookie(String name, String value) {
      getCookies().add(name, value);
      return this;
    }

    @Override
    public DefaultRequestBodyUriSpec cookies(Consumer<MultiValueMap<String, String>> cookiesConsumer) {
      cookiesConsumer.accept(getCookies());
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
    public RequestBodySpec attribute(String name, Object value) {
      this.attributes.put(name, value);
      return this;
    }

    @Override
    public RequestBodySpec attributes(Consumer<Map<String, Object>> attributesConsumer) {
      attributesConsumer.accept(this.attributes);
      return this;
    }

    @Override
    public RequestBodySpec context(Function<Context, Context> contextModifier) {
      this.contextModifier = (this.contextModifier != null ?
                              this.contextModifier.andThen(contextModifier) : contextModifier);
      return this;
    }

    @Override
    public RequestBodySpec httpRequest(Consumer<ClientHttpRequest> requestConsumer) {
      this.httpRequestConsumer = (this.httpRequestConsumer != null ?
                                  this.httpRequestConsumer.andThen(requestConsumer) : requestConsumer);
      return this;
    }

    @Override
    public RequestHeadersSpec<?> bodyValue(Object body) {
      this.inserter = BodyInserters.fromValue(body);
      return this;
    }

    @Override
    public <T, P extends Publisher<T>> RequestHeadersSpec<?> body(
            P publisher, TypeReference<T> elementTypeRef) {
      this.inserter = BodyInserters.fromPublisher(publisher, elementTypeRef);
      return this;
    }

    @Override
    public <T, P extends Publisher<T>> RequestHeadersSpec<?> body(P publisher, Class<T> elementClass) {
      this.inserter = BodyInserters.fromPublisher(publisher, elementClass);
      return this;
    }

    @Override
    public RequestHeadersSpec<?> body(Object producer, Class<?> elementClass) {
      this.inserter = BodyInserters.fromProducer(producer, elementClass);
      return this;
    }

    @Override
    public RequestHeadersSpec<?> body(Object producer, TypeReference<?> elementTypeRef) {
      this.inserter = BodyInserters.fromProducer(producer, elementTypeRef);
      return this;
    }

    @Override
    public RequestHeadersSpec<?> body(BodyInserter<?, ? super ClientHttpRequest> inserter) {
      this.inserter = inserter;
      return this;
    }

    @Override
    public ResponseSpec retrieve() {
      return new DefaultResponseSpec(
              exchange(), this::createRequest, DefaultWebClient.this.defaultStatusHandlers);
    }

    private HttpRequest createRequest() {
      return new HttpRequest() {
        private final URI uri = initUri();

        @Override
        public HttpMethod getMethod() {
          return httpMethod;
        }

        @Override
        public String getMethodValue() {
          return httpMethod.name();
        }

        @Override
        public URI getURI() {
          return this.uri;
        }

        @Override
        public HttpHeaders getHeaders() {
          HttpHeaders headers = HttpHeaders.create();
          initHeaders(headers);
          return headers;
        }
      };
    }

    @Override
    public <V> Mono<V> exchangeToMono(Function<ClientResponse, ? extends Mono<V>> responseHandler) {
      return exchange().flatMap(response -> {
        try {
          return responseHandler.apply(response)
                  .flatMap(value -> releaseIfNotConsumed(response).thenReturn(value))
                  .switchIfEmpty(Mono.defer(() -> releaseIfNotConsumed(response).then(Mono.empty())))
                  .onErrorResume(ex -> releaseIfNotConsumed(response, ex));
        }
        catch (Throwable ex) {
          return releaseIfNotConsumed(response, ex);
        }
      });
    }

    @Override
    public <V> Flux<V> exchangeToFlux(Function<ClientResponse, ? extends Flux<V>> responseHandler) {
      return exchange().flatMapMany(response -> {
        try {
          return responseHandler.apply(response)
                  .concatWith(Flux.defer(() -> releaseIfNotConsumed(response).then(Mono.empty())))
                  .onErrorResume(ex -> releaseIfNotConsumed(response, ex));
        }
        catch (Throwable ex) {
          return releaseIfNotConsumed(response, ex);
        }
      });
    }

    @Override
    public Mono<ClientResponse> exchange() {
      ClientRequest.Builder requestBuilder = initRequestBuilder();
      if (inserter != null) {
        requestBuilder = requestBuilder.body(inserter);
      }

      ClientRequest request = requestBuilder.build();

      return Mono.defer(() -> {
        Mono<ClientResponse> responseMono = exchangeFunction.exchange(request)
                .checkpoint("Request to " + httpMethod.name() + " " + this.uri + " [DefaultWebClient]")
                .switchIfEmpty(NO_HTTP_CLIENT_RESPONSE_ERROR);
        if (this.contextModifier != null) {
          responseMono = responseMono.contextWrite(this.contextModifier);
        }
        return responseMono;
      });
    }

    private ClientRequest.Builder initRequestBuilder() {
      if (defaultRequest != null) {
        defaultRequest.accept(this);
      }
      ClientRequest.Builder builder = ClientRequest.create(this.httpMethod, initUri())
              .headers(this::initHeaders)
              .cookies(this::initCookies)
              .attributes(attributes -> attributes.putAll(this.attributes));
      if (this.httpRequestConsumer != null) {
        builder.httpRequest(this.httpRequestConsumer);
      }
      return builder;
    }

    private URI initUri() {
      return (this.uri != null ? this.uri : uriBuilderFactory.expand(""));
    }

    private HttpHeaders initHeaders() {
      if (CollectionUtils.isEmpty(this.headers)) {
        return (defaultHeaders != null ? defaultHeaders : HttpHeaders.create());
      }
      else if (CollectionUtils.isEmpty(defaultHeaders)) {
        return this.headers;
      }
      else {
        HttpHeaders result = HttpHeaders.create();
        result.putAll(defaultHeaders);
        result.putAll(this.headers);
        return result;
      }
    }

    private void initHeaders(HttpHeaders out) {
      if (!CollectionUtils.isEmpty(defaultHeaders)) {
        out.putAll(defaultHeaders);
      }
      if (!CollectionUtils.isEmpty(this.headers)) {
        out.putAll(this.headers);
      }
    }

    private void initCookies(MultiValueMap<String, String> out) {
      if (!CollectionUtils.isEmpty(defaultCookies)) {
        out.putAll(defaultCookies);
      }
      if (!CollectionUtils.isEmpty(this.cookies)) {
        out.putAll(this.cookies);
      }
    }

  }

  private static class DefaultResponseSpec implements ResponseSpec {

    private static final Predicate<HttpStatusCode> STATUS_CODE_ERROR = HttpStatusCode::isError;

    private static final StatusHandler DEFAULT_STATUS_HANDLER =
            new StatusHandler(STATUS_CODE_ERROR, ClientResponse::createException);

    private final Mono<ClientResponse> responseMono;

    private final Supplier<HttpRequest> requestSupplier;

    private final List<StatusHandler> statusHandlers = new ArrayList<>(1);

    private final int defaultStatusHandlerCount;

    DefaultResponseSpec(
            Mono<ClientResponse> responseMono, Supplier<HttpRequest> requestSupplier,
            List<StatusHandler> defaultStatusHandlers) {

      this.responseMono = responseMono;
      this.requestSupplier = requestSupplier;
      this.statusHandlers.addAll(defaultStatusHandlers);
      this.statusHandlers.add(DEFAULT_STATUS_HANDLER);
      this.defaultStatusHandlerCount = this.statusHandlers.size();
    }

    @Override
    public ResponseSpec onStatus(Predicate<HttpStatusCode> statusCodePredicate,
            Function<ClientResponse, Mono<? extends Throwable>> exceptionFunction) {
      Assert.notNull(exceptionFunction, "Function is required");
      Assert.notNull(statusCodePredicate, "StatusCodePredicate is required");

      int index = statusHandlers.size() - this.defaultStatusHandlerCount;  // Default handlers always last
      statusHandlers.add(index, new StatusHandler(statusCodePredicate, exceptionFunction));
      return this;
    }

    @Override
    public ResponseSpec onRawStatus(IntPredicate statusCodePredicate,
            Function<ClientResponse, Mono<? extends Throwable>> exceptionFunction) {

      return onStatus(toStatusCodePredicate(statusCodePredicate), exceptionFunction);
    }

    private static Predicate<HttpStatusCode> toStatusCodePredicate(IntPredicate predicate) {
      return value -> predicate.test(value.value());
    }

    @Override
    public <T> Mono<T> bodyToMono(Class<T> elementClass) {
      Assert.notNull(elementClass, "Class must not be null");
      return this.responseMono.flatMap(response ->
              handleBodyMono(response, response.bodyToMono(elementClass)));
    }

    @Override
    public <T> Mono<T> bodyToMono(TypeReference<T> elementTypeRef) {
      Assert.notNull(elementTypeRef, "TypeReference must not be null");
      return this.responseMono.flatMap(response ->
              handleBodyMono(response, response.bodyToMono(elementTypeRef)));
    }

    @Override
    public <T> Flux<T> bodyToFlux(Class<T> elementClass) {
      Assert.notNull(elementClass, "Class must not be null");
      return this.responseMono.flatMapMany(response ->
              handleBodyFlux(response, response.bodyToFlux(elementClass)));
    }

    @Override
    public <T> Flux<T> bodyToFlux(TypeReference<T> elementTypeRef) {
      Assert.notNull(elementTypeRef, "TypeReference must not be null");
      return this.responseMono.flatMapMany(response ->
              handleBodyFlux(response, response.bodyToFlux(elementTypeRef)));
    }

    @Override
    public <T> Mono<ResponseEntity<T>> toEntity(Class<T> bodyClass) {
      return this.responseMono.flatMap(response ->
              WebClientUtils.mapToEntity(response,
                      handleBodyMono(response, response.bodyToMono(bodyClass))));
    }

    @Override
    public <T> Mono<ResponseEntity<T>> toEntity(TypeReference<T> bodyTypeRef) {
      return this.responseMono.flatMap(response ->
              WebClientUtils.mapToEntity(response,
                      handleBodyMono(response, response.bodyToMono(bodyTypeRef))));
    }

    @Override
    public <T> Mono<ResponseEntity<List<T>>> toEntityList(Class<T> elementClass) {
      return this.responseMono.flatMap(response ->
              WebClientUtils.mapToEntityList(response,
                      handleBodyFlux(response, response.bodyToFlux(elementClass))));
    }

    @Override
    public <T> Mono<ResponseEntity<List<T>>> toEntityList(TypeReference<T> elementTypeRef) {
      return this.responseMono.flatMap(response ->
              WebClientUtils.mapToEntityList(response,
                      handleBodyFlux(response, response.bodyToFlux(elementTypeRef))));
    }

    @Override
    public <T> Mono<ResponseEntity<Flux<T>>> toEntityFlux(Class<T> elementType) {
      return this.responseMono.flatMap(response ->
              handlerEntityFlux(response, response.bodyToFlux(elementType)));
    }

    @Override
    public <T> Mono<ResponseEntity<Flux<T>>> toEntityFlux(TypeReference<T> elementTypeRef) {
      return this.responseMono.flatMap(response ->
              handlerEntityFlux(response, response.bodyToFlux(elementTypeRef)));
    }

    @Override
    public <T> Mono<ResponseEntity<Flux<T>>> toEntityFlux(BodyExtractor<Flux<T>, ? super ClientHttpResponse> bodyExtractor) {
      return this.responseMono.flatMap(response ->
              handlerEntityFlux(response, response.body(bodyExtractor)));
    }

    @Override
    public Mono<ResponseEntity<Void>> toBodilessEntity() {
      return this.responseMono.flatMap(response ->
              WebClientUtils.mapToEntity(response, handleBodyMono(response, Mono.<Void>empty()))
                      .flatMap(entity -> response.releaseBody()
                              .onErrorResume(WebClientUtils.WRAP_EXCEPTION_PREDICATE, exceptionWrappingFunction(response))
                              .thenReturn(entity))
      );
    }

    private <T> Mono<T> handleBodyMono(ClientResponse response, Mono<T> body) {
      body = body.onErrorResume(WebClientUtils.WRAP_EXCEPTION_PREDICATE, exceptionWrappingFunction(response));
      Mono<T> result = applyStatusHandlers(response);
      return (result != null ? result.switchIfEmpty(body) : body);
    }

    private <T> Publisher<T> handleBodyFlux(ClientResponse response, Flux<T> body) {
      body = body.onErrorResume(WebClientUtils.WRAP_EXCEPTION_PREDICATE, exceptionWrappingFunction(response));
      Mono<T> result = applyStatusHandlers(response);
      return (result != null ? result.flux().switchIfEmpty(body) : body);
    }

    private <T> Mono<? extends ResponseEntity<Flux<T>>> handlerEntityFlux(ClientResponse response, Flux<T> body) {
      ResponseEntity<Flux<T>> entity = new ResponseEntity<>(
              body.onErrorResume(WebClientUtils.WRAP_EXCEPTION_PREDICATE, exceptionWrappingFunction(response)),
              response.headers().asHttpHeaders(),
              response.statusCode());

      Mono<ResponseEntity<Flux<T>>> result = applyStatusHandlers(response);
      return (result != null ? result.defaultIfEmpty(entity) : Mono.just(entity));
    }

    private <T> Function<Throwable, Mono<? extends T>> exceptionWrappingFunction(ClientResponse response) {
      return t -> response.createException().flatMap(ex -> Mono.error(ex.initCause(t)));
    }

    @Nullable
    private <T> Mono<T> applyStatusHandlers(ClientResponse response) {
      HttpStatusCode statusCode = response.statusCode();
      for (StatusHandler handler : this.statusHandlers) {
        if (handler.test(statusCode)) {
          Mono<? extends Throwable> exMono;
          try {
            exMono = handler.apply(response);
            exMono = exMono.flatMap(ex -> releaseIfNotConsumed(response, ex));
            exMono = exMono.onErrorResume(ex -> releaseIfNotConsumed(response, ex));
          }
          catch (Throwable ex2) {
            exMono = releaseIfNotConsumed(response, ex2);
          }
          Mono<T> result = exMono.flatMap(Mono::error);
          HttpRequest request = this.requestSupplier.get();
          return insertCheckpoint(result, statusCode, request);
        }
      }
      return null;
    }

    private <T> Mono<T> insertCheckpoint(Mono<T> result, HttpStatusCode statusCode, HttpRequest request) {
      HttpMethod httpMethod = request.getMethod();
      URI uri = request.getURI();
      String description = statusCode + " from " + httpMethod + " " + uri + " [DefaultWebClient]";
      return result.checkpoint(description);
    }

    private record StatusHandler(
            Predicate<HttpStatusCode> predicate,
            Function<ClientResponse, Mono<? extends Throwable>> exceptionFunction) {

      public boolean test(HttpStatusCode status) {
        return this.predicate.test(status);
      }

      public Mono<? extends Throwable> apply(ClientResponse response) {
        return this.exceptionFunction.apply(response);
      }
    }
  }

}
