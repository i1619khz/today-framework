/*
 * Copyright 2017 - 2023 the original author or authors.
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

package cn.taketoday.http.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import cn.taketoday.http.HttpHeaders;
import cn.taketoday.http.HttpMethod;
import cn.taketoday.http.StreamingHttpOutputMessage;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.StreamUtils;

/**
 * Simple implementation of {@link ClientHttpRequest} that wraps another request.
 *
 * @author Arjen Poutsma
 * @author <a href="https://github.com/TAKETODAY">Harry Yang</a>
 * @since 4.0
 */
final class BufferingClientHttpRequestWrapper extends AbstractBufferingClientHttpRequest {

  private final ClientHttpRequest request;

  BufferingClientHttpRequestWrapper(ClientHttpRequest request) {
    this.request = request;
  }

  @Override
  @Nullable
  public HttpMethod getMethod() {
    return this.request.getMethod();
  }

  @Override
  public String getMethodValue() {
    return this.request.getMethodValue();
  }

  @Override
  public URI getURI() {
    return this.request.getURI();
  }

  @Override
  protected ClientHttpResponse executeInternal(HttpHeaders headers, byte[] bufferedOutput) throws IOException {
    request.getHeaders().putAll(headers);

    if (request instanceof StreamingHttpOutputMessage streaming) {
      streaming.setBody(new StreamingHttpOutputMessage.Body() {

        @Override
        public void writeTo(OutputStream outputStream) throws IOException {
          StreamUtils.copy(bufferedOutput, outputStream);
        }

        @Override
        public boolean repeatable() {
          return true;
        }
      });
    }
    else {
      StreamUtils.copy(bufferedOutput, request.getBody());
    }

    ClientHttpResponse response = request.execute();
    return new BufferingClientHttpResponseWrapper(response);
  }

}
