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

package cn.taketoday.web.reactive.function.client;

import java.io.Serial;
import java.nio.charset.Charset;

import cn.taketoday.http.HttpHeaders;
import cn.taketoday.http.HttpRequest;
import cn.taketoday.http.HttpStatusCode;
import cn.taketoday.lang.Nullable;

/**
 * Exception thrown when an unknown (or custom) HTTP status code is received.
 *
 * @author Brian Clozel
 * @since 4.0
 */
public class UnknownHttpStatusCodeException extends WebClientResponseException {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Create a new instance of the {@code UnknownHttpStatusCodeException} with the given
   * parameters.
   */
  public UnknownHttpStatusCodeException(
          int statusCode, HttpHeaders headers, byte[] responseBody, Charset responseCharset) {

    super("Unknown status code [" + statusCode + "]", statusCode, "",
            headers, responseBody, responseCharset);
  }

  /**
   * Create a new instance of the {@code UnknownHttpStatusCodeException} with the given
   * parameters.
   */
  public UnknownHttpStatusCodeException(
          int statusCode, HttpHeaders headers, byte[] responseBody, @Nullable Charset responseCharset,
          @Nullable HttpRequest request) {

    super("Unknown status code [" + statusCode + "]", statusCode, "",
            headers, responseBody, responseCharset, request);
  }

  /**
   * Create a new instance of the {@code UnknownHttpStatusCodeException} with the given
   * parameters.
   */
  public UnknownHttpStatusCodeException(
          HttpStatusCode statusCode, HttpHeaders headers, byte[] responseBody, @Nullable Charset responseCharset,
          @Nullable HttpRequest request) {

    super("Unknown status code [" + statusCode + "]", statusCode, "",
            headers, responseBody, responseCharset, request);
  }

}
