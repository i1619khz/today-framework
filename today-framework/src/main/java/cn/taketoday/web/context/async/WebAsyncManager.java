/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.taketoday.web.context.async;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import cn.taketoday.core.task.AsyncTaskExecutor;
import cn.taketoday.core.task.SimpleAsyncTaskExecutor;
import cn.taketoday.core.task.SyncTaskExecutor;
import cn.taketoday.lang.Assert;
import cn.taketoday.lang.Nullable;
import cn.taketoday.logging.Logger;
import cn.taketoday.logging.LoggerFactory;
import cn.taketoday.web.RequestContext;
import cn.taketoday.web.context.async.DeferredResult.DeferredResultHandler;
import cn.taketoday.web.servlet.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The central class for managing asynchronous request processing, mainly intended
 * as an SPI and not typically used directly by application classes.
 *
 * <p>An async scenario starts with request processing as usual in a thread (T1).
 * Concurrent request handling can be initiated by calling
 * {@link #startCallableProcessing(Callable, Object...) startCallableProcessing} or
 * {@link #startDeferredResultProcessing(DeferredResult, Object...) startDeferredResultProcessing},
 * both of which produce a result in a separate thread (T2). The result is saved
 * and the request dispatched to the container, to resume processing with the saved
 * result in a third thread (T3). Within the dispatched thread (T3), the saved
 * result can be accessed via {@link #getConcurrentResult()} or its presence
 * detected via {@link #hasConcurrentResult()}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @see cn.taketoday.web.servlet.filter.OncePerRequestFilter#shouldNotFilterAsyncDispatch
 * @see cn.taketoday.web.servlet.filter.OncePerRequestFilter#isAsyncDispatch
 * @since 4.0
 */
public final class WebAsyncManager {

  private static final Object RESULT_NONE = new Object();

  private static final AsyncTaskExecutor DEFAULT_TASK_EXECUTOR =
          new SimpleAsyncTaskExecutor(WebAsyncManager.class.getSimpleName());

  private static final Logger logger = LoggerFactory.getLogger(WebAsyncManager.class);

  private static final CallableProcessingInterceptor timeoutCallableInterceptor =
          new TimeoutCallableProcessingInterceptor();

  private static final DeferredResultProcessingInterceptor timeoutDeferredResultInterceptor =
          new TimeoutDeferredResultProcessingInterceptor();

  private static Boolean taskExecutorWarning = true;

  private AsyncWebRequest asyncWebRequest;

  private AsyncTaskExecutor taskExecutor = DEFAULT_TASK_EXECUTOR;

  private volatile Object concurrentResult = RESULT_NONE;

  private volatile Object[] concurrentResultContext;

  /*
   * Whether the concurrentResult is an error. If such errors remain unhandled, some
   * Servlet containers will call AsyncListener#onError at the end, after the ASYNC
   * and/or the ERROR dispatch (Boot's case), and we need to ignore those.
   */
  private volatile boolean errorHandlingInProgress;

  private final Map<Object, CallableProcessingInterceptor> callableInterceptors = new LinkedHashMap<>();

  private final Map<Object, DeferredResultProcessingInterceptor> deferredResultInterceptors = new LinkedHashMap<>();

  private final RequestContext requestContext;

  /**
   * Package-private constructor.
   *
   * @see WebAsyncUtils#getAsyncManager(cn.taketoday.web.RequestContext)
   */
  WebAsyncManager(RequestContext requestContext) {
    this.requestContext = requestContext;
  }

  /**
   * Configure the {@link AsyncWebRequest} to use. This property may be set
   * more than once during a single request to accurately reflect the current
   * state of the request (e.g. following a forward, request/response
   * wrapping, etc). However, it should not be set while concurrent handling
   * is in progress, i.e. while {@link #isConcurrentHandlingStarted()} is
   * {@code true}.
   *
   * @param asyncWebRequest the web request to use
   */
  public void setAsyncWebRequest(AsyncWebRequest asyncWebRequest) {
    Assert.notNull(asyncWebRequest, "AsyncWebRequest must not be null");
    this.asyncWebRequest = asyncWebRequest;
    this.asyncWebRequest.addCompletionHandler(
            () -> requestContext.removeAttribute(WebAsyncUtils.WEB_ASYNC_MANAGER_ATTRIBUTE));
  }

  /**
   * Configure an AsyncTaskExecutor for use with concurrent processing via
   * {@link #startCallableProcessing(Callable, Object...)}.
   * <p>By default a {@link SimpleAsyncTaskExecutor} instance is used.
   */
  public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
    this.taskExecutor = taskExecutor;
  }

  /**
   * Whether the selected handler for the current request chose to handle the
   * request asynchronously. A return value of "true" indicates concurrent
   * handling is under way and the response will remain open. A return value
   * of "false" means concurrent handling was either not started or possibly
   * that it has completed and the request was dispatched for further
   * processing of the concurrent result.
   */
  public boolean isConcurrentHandlingStarted() {
    return (asyncWebRequest != null && asyncWebRequest.isAsyncStarted());
  }

  /**
   * Whether a result value exists as a result of concurrent handling.
   */
  public boolean hasConcurrentResult() {
    return (concurrentResult != RESULT_NONE);
  }

  /**
   * Provides access to the result from concurrent handling.
   *
   * @return an Object, possibly an {@code Exception} or {@code Throwable} if
   * concurrent handling raised one.
   * @see #clearConcurrentResult()
   */
  public Object getConcurrentResult() {
    return concurrentResult;
  }

  /**
   * Provides access to additional processing context saved at the start of
   * concurrent handling.
   *
   * @see #clearConcurrentResult()
   */
  public Object[] getConcurrentResultContext() {
    return concurrentResultContext;
  }

  /**
   * Get the {@link CallableProcessingInterceptor} registered under the given key.
   *
   * @param key the key
   * @return the interceptor registered under that key, or {@code null} if none
   */
  @Nullable
  public CallableProcessingInterceptor getCallableInterceptor(Object key) {
    return callableInterceptors.get(key);
  }

  /**
   * Get the {@link DeferredResultProcessingInterceptor} registered under the given key.
   *
   * @param key the key
   * @return the interceptor registered under that key, or {@code null} if none
   */
  @Nullable
  public DeferredResultProcessingInterceptor getDeferredResultInterceptor(Object key) {
    return deferredResultInterceptors.get(key);
  }

  /**
   * Register a {@link CallableProcessingInterceptor} under the given key.
   *
   * @param key the key
   * @param interceptor the interceptor to register
   */
  public void registerCallableInterceptor(Object key, CallableProcessingInterceptor interceptor) {
    Assert.notNull(key, "Key is required");
    Assert.notNull(interceptor, "CallableProcessingInterceptor  is required");
    callableInterceptors.put(key, interceptor);
  }

  /**
   * Register a {@link CallableProcessingInterceptor} without a key.
   * The key is derived from the class name and hashcode.
   *
   * @param interceptors one or more interceptors to register
   */
  public void registerCallableInterceptors(CallableProcessingInterceptor... interceptors) {
    Assert.notNull(interceptors, "A CallableProcessingInterceptor is required");
    for (CallableProcessingInterceptor interceptor : interceptors) {
      String key = interceptor.getClass().getName() + ":" + interceptor.hashCode();
      callableInterceptors.put(key, interceptor);
    }
  }

  /**
   * Register a {@link DeferredResultProcessingInterceptor} under the given key.
   *
   * @param key the key
   * @param interceptor the interceptor to register
   */
  public void registerDeferredResultInterceptor(Object key, DeferredResultProcessingInterceptor interceptor) {
    Assert.notNull(key, "Key is required");
    Assert.notNull(interceptor, "DeferredResultProcessingInterceptor is required");
    deferredResultInterceptors.put(key, interceptor);
  }

  /**
   * Register one or more {@link DeferredResultProcessingInterceptor DeferredResultProcessingInterceptors} without a specified key.
   * The default key is derived from the interceptor class name and hash code.
   *
   * @param interceptors one or more interceptors to register
   */
  public void registerDeferredResultInterceptors(DeferredResultProcessingInterceptor... interceptors) {
    Assert.notNull(interceptors, "A DeferredResultProcessingInterceptor is required");
    for (DeferredResultProcessingInterceptor interceptor : interceptors) {
      String key = interceptor.getClass().getName() + ":" + interceptor.hashCode();
      deferredResultInterceptors.put(key, interceptor);
    }
  }

  /**
   * Clear {@linkplain #getConcurrentResult() concurrentResult} and
   * {@linkplain #getConcurrentResultContext() concurrentResultContext}.
   */
  public void clearConcurrentResult() {
    synchronized(WebAsyncManager.this) {
      this.concurrentResult = RESULT_NONE;
      this.concurrentResultContext = null;
    }
  }

  /**
   * Start concurrent request processing and execute the given task with an
   * {@link #setTaskExecutor(AsyncTaskExecutor) AsyncTaskExecutor}. The result
   * from the task execution is saved and the request dispatched in order to
   * resume processing of that result. If the task raises an Exception then
   * the saved result will be the raised Exception.
   *
   * @param callable a unit of work to be executed asynchronously
   * @param processingContext additional context to save that can be accessed
   * via {@link #getConcurrentResultContext()}
   * @throws Exception if concurrent processing failed to start
   * @see #getConcurrentResult()
   * @see #getConcurrentResultContext()
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void startCallableProcessing(Callable<?> callable, Object... processingContext) throws Exception {
    Assert.notNull(callable, "Callable must not be null");
    startCallableProcessing(new WebAsyncTask(callable), processingContext);
  }

  /**
   * Use the given {@link WebAsyncTask} to configure the task executor as well as
   * the timeout value of the {@code AsyncWebRequest} before delegating to
   * {@link #startCallableProcessing(Callable, Object...)}.
   *
   * @param webAsyncTask a WebAsyncTask containing the target {@code Callable}
   * @param processingContext additional context to save that can be accessed
   * via {@link #getConcurrentResultContext()}
   * @throws Exception if concurrent processing failed to start
   */
  public void startCallableProcessing(final WebAsyncTask<?> webAsyncTask, Object... processingContext)
          throws Exception {

    Assert.notNull(webAsyncTask, "WebAsyncTask must not be null");
    Assert.state(this.asyncWebRequest != null, "AsyncWebRequest must not be null");

    Long timeout = webAsyncTask.getTimeout();
    if (timeout != null) {
      this.asyncWebRequest.setTimeout(timeout);
    }

    AsyncTaskExecutor executor = webAsyncTask.getExecutor();
    if (executor != null) {
      this.taskExecutor = executor;
    }
    else {
      logExecutorWarning();
    }

    ArrayList<CallableProcessingInterceptor> interceptors = new ArrayList<>();
    interceptors.add(webAsyncTask.getInterceptor());
    interceptors.addAll(this.callableInterceptors.values());
    interceptors.add(timeoutCallableInterceptor);

    final Callable<?> callable = webAsyncTask.getCallable();
    final CallableInterceptorChain interceptorChain = new CallableInterceptorChain(interceptors);

    asyncWebRequest.addTimeoutHandler(() -> {
      if (logger.isDebugEnabled()) {
        logger.debug("Async request timeout for " + formatRequestUri());
      }
      Object result = interceptorChain.triggerAfterTimeout(requestContext, callable);
      if (result != CallableProcessingInterceptor.RESULT_NONE) {
        setConcurrentResultAndDispatch(result);
      }
    });

    asyncWebRequest.addErrorHandler(ex -> {
      if (!errorHandlingInProgress) {
        if (logger.isDebugEnabled()) {
          logger.debug("Async request error for " + formatRequestUri() + ": " + ex);
        }
        Object result = interceptorChain.triggerAfterError(requestContext, callable, ex);
        result = (result != CallableProcessingInterceptor.RESULT_NONE ? result : ex);
        setConcurrentResultAndDispatch(result);
      }
    });

    asyncWebRequest.addCompletionHandler(
            () -> interceptorChain.triggerAfterCompletion(requestContext, callable));

    interceptorChain.applyBeforeConcurrentHandling(requestContext, callable);
    startAsyncProcessing(processingContext);
    try {
      Future<?> future = taskExecutor.submit(() -> {
        Object result = null;
        try {
          interceptorChain.applyPreProcess(requestContext, callable);
          result = callable.call();
        }
        catch (Throwable ex) {
          result = ex;
        }
        finally {
          result = interceptorChain.applyPostProcess(requestContext, callable, result);
        }
        setConcurrentResultAndDispatch(result);
      });
      interceptorChain.setTaskFuture(future);
    }
    catch (RejectedExecutionException ex) {
      Object result = interceptorChain.applyPostProcess(requestContext, callable, ex);
      setConcurrentResultAndDispatch(result);
      throw ex;
    }
  }

  private void logExecutorWarning() {
    if (taskExecutorWarning && logger.isWarnEnabled()) {
      synchronized(DEFAULT_TASK_EXECUTOR) {
        AsyncTaskExecutor executor = this.taskExecutor;
        if (taskExecutorWarning &&
                (executor instanceof SimpleAsyncTaskExecutor || executor instanceof SyncTaskExecutor)) {
          String executorTypeName = executor.getClass().getSimpleName();
          logger.warn("\n!!!\n" +
                  "An Executor is required to handle java.util.concurrent.Callable return values.\n" +
                  "Please, configure a TaskExecutor in the MVC config under \"async support\".\n" +
                  "The " + executorTypeName + " currently in use is not suitable under load.\n" +
                  "-------------------------------\n" +
                  "Request URI: '" + formatRequestUri() + "'\n" +
                  "!!!");
          taskExecutorWarning = false;
        }
      }
    }
  }

  private String formatRequestUri() {
    HttpServletRequest request = ServletUtils.getServletRequest(requestContext);
    return request != null ? request.getRequestURI() : "servlet container";
  }

  private void setConcurrentResultAndDispatch(Object result) {
    synchronized(WebAsyncManager.this) {
      if (concurrentResult != RESULT_NONE) {
        return;
      }
      this.concurrentResult = result;
      this.errorHandlingInProgress = (result instanceof Throwable);
    }

    if (asyncWebRequest.isAsyncComplete()) {
      if (logger.isDebugEnabled()) {
        logger.debug("Async result set but request already complete: " + formatRequestUri());
      }
      return;
    }

    if (logger.isDebugEnabled()) {
      boolean isError = result instanceof Throwable;
      logger.debug("Async " + (isError ? "error" : "result set") + ", dispatch to " + formatRequestUri());
    }
    asyncWebRequest.dispatch();
  }

  /**
   * Start concurrent request processing and initialize the given
   * {@link DeferredResult} with a {@link DeferredResultHandler} that saves
   * the result and dispatches the request to resume processing of that
   * result. The {@code AsyncWebRequest} is also updated with a completion
   * handler that expires the {@code DeferredResult} and a timeout handler
   * assuming the {@code DeferredResult} has a default timeout result.
   *
   * @param deferredResult the DeferredResult instance to initialize
   * @param processingContext additional context to save that can be accessed
   * via {@link #getConcurrentResultContext()}
   * @throws Exception if concurrent processing failed to start
   * @see #getConcurrentResult()
   * @see #getConcurrentResultContext()
   */
  public void startDeferredResultProcessing(
          final DeferredResult<?> deferredResult, Object... processingContext) throws Exception {

    Assert.notNull(deferredResult, "DeferredResult must not be null");
    Assert.state(asyncWebRequest != null, "AsyncWebRequest must not be null");

    Long timeout = deferredResult.getTimeoutValue();
    if (timeout != null) {
      asyncWebRequest.setTimeout(timeout);
    }

    List<DeferredResultProcessingInterceptor> interceptors = new ArrayList<>();
    interceptors.add(deferredResult.getInterceptor());
    interceptors.addAll(deferredResultInterceptors.values());
    interceptors.add(timeoutDeferredResultInterceptor);

    final DeferredResultInterceptorChain interceptorChain = new DeferredResultInterceptorChain(interceptors);

    asyncWebRequest.addTimeoutHandler(() -> {
      try {
        interceptorChain.triggerAfterTimeout(requestContext, deferredResult);
      }
      catch (Throwable ex) {
        setConcurrentResultAndDispatch(ex);
      }
    });

    asyncWebRequest.addErrorHandler(ex -> {
      if (!errorHandlingInProgress) {
        try {
          if (!interceptorChain.triggerAfterError(requestContext, deferredResult, ex)) {
            return;
          }
          deferredResult.setErrorResult(ex);
        }
        catch (Throwable interceptorEx) {
          setConcurrentResultAndDispatch(interceptorEx);
        }
      }
    });

    asyncWebRequest.addCompletionHandler(()
            -> interceptorChain.triggerAfterCompletion(requestContext, deferredResult));

    interceptorChain.applyBeforeConcurrentHandling(requestContext, deferredResult);
    startAsyncProcessing(processingContext);

    try {
      interceptorChain.applyPreProcess(requestContext, deferredResult);
      deferredResult.setResultHandler(result -> {
        result = interceptorChain.applyPostProcess(requestContext, deferredResult, result);
        setConcurrentResultAndDispatch(result);
      });
    }
    catch (Throwable ex) {
      setConcurrentResultAndDispatch(ex);
    }
  }

  private void startAsyncProcessing(Object[] processingContext) {
    synchronized(WebAsyncManager.this) {
      this.concurrentResult = RESULT_NONE;
      this.concurrentResultContext = processingContext;
      this.errorHandlingInProgress = false;
    }
    asyncWebRequest.startAsync();

    if (logger.isDebugEnabled()) {
      logger.debug("Started async request");
    }
  }

}