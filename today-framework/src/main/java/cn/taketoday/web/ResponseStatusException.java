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

package cn.taketoday.web;

import java.io.Serial;

import cn.taketoday.http.HttpHeaders;
import cn.taketoday.http.HttpStatus;
import cn.taketoday.http.ProblemDetail;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.ExceptionUtils;

/**
 * Subclass of {@link ErrorResponseException} that accepts a "reason" and maps
 * it to the "detail" property of {@link ProblemDetail}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author TODAY 2021/5/6 19:16
 * @since 3.0.1
 */
public class ResponseStatusException extends ErrorResponseException {
  @Serial
  private static final long serialVersionUID = 1L;

  @Nullable
  private final String reason;

  /**
   * Constructor with a response status.
   *
   * @param status the HTTP status (required)
   */
  public ResponseStatusException(HttpStatus status) {
    this(status, null);
  }

  /**
   * Constructor with a response status and a reason to add to the exception
   * message as explanation.
   *
   * @param status the HTTP status (required)
   * @param reason the associated reason (optional)
   */
  public ResponseStatusException(HttpStatus status, @Nullable String reason) {
    this(status, reason, null);
  }

  /**
   * Constructor with a response status and a reason to add to the exception
   * message as explanation, as well as a nested exception.
   *
   * @param status the HTTP status (required)
   * @param reason the associated reason (optional)
   * @param cause a nested exception (optional)
   */
  public ResponseStatusException(HttpStatus status, @Nullable String reason, @Nullable Throwable cause) {
    this(status.value(), reason, cause);
  }

  /**
   * Constructor with a response status and a reason to add to the exception
   * message as explanation, as well as a nested exception.
   *
   * @param rawStatusCode the HTTP status code value
   * @param reason the associated reason (optional)
   * @param cause a nested exception (optional)
   */
  public ResponseStatusException(int rawStatusCode, @Nullable String reason, @Nullable Throwable cause) {
    super(rawStatusCode, cause);
    this.reason = reason;
  }

  /**
   * The reason explaining the exception (potentially {@code null} or empty).
   */
  @Nullable
  public String getReason() {
    return this.reason;
  }

  /**
   * Return headers to add to the error response, e.g. "Allow", "Accept", etc.
   * <p>By default, delegates to {@link HttpHeaders#EMPTY} for backwards
   * compatibility.
   */
  @Override
  public HttpHeaders getHeaders() {
    return HttpHeaders.EMPTY;
  }

  @Override
  public String getMessage() {
    HttpStatus code = HttpStatus.resolve(getRawStatusCode());
    String msg = (code != null ? code : getRawStatusCode()) + (this.reason != null ? " \"" + this.reason + "\"" : "");
    return ExceptionUtils.buildMessage(msg, getCause());
  }

}