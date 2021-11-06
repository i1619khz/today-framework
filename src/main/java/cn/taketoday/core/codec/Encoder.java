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

package cn.taketoday.core.codec;

import org.reactivestreams.Publisher;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import cn.taketoday.core.ResolvableType;
import cn.taketoday.core.io.buffer.DataBuffer;
import cn.taketoday.core.io.buffer.DataBufferFactory;
import cn.taketoday.lang.Nullable;
import cn.taketoday.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Strategy to encode a stream of Objects of type {@code <T>} into an output
 * stream of bytes.
 *
 * @param <T> the type of elements in the input stream
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 4.0
 */
public interface Encoder<T> {

  /**
   * Whether the encoder supports the given source element type and the MIME
   * type for the output stream.
   *
   * @param elementType the type of elements in the source stream
   * @param mimeType the MIME type for the output stream
   * (can be {@code null} if not specified)
   * @return {@code true} if supported, {@code false} otherwise
   */
  boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType);

  /**
   * Encode a stream of Objects of type {@code T} into a {@link DataBuffer}
   * output stream.
   *
   * @param inputStream the input stream of Objects to encode. If the input should be
   * encoded as a single value rather than as a stream of elements, an instance of
   * {@link Mono} should be used.
   * @param bufferFactory for creating output stream {@code DataBuffer}'s
   * @param elementType the expected type of elements in the input stream;
   * this type must have been previously passed to the {@link #canEncode}
   * method and it must have returned {@code true}.
   * @param mimeType the MIME type for the output content (optional)
   * @param hints additional information about how to encode
   * @return the output stream
   */
  Flux<DataBuffer> encode(
          Publisher<? extends T> inputStream, DataBufferFactory bufferFactory,
          ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints);

  /**
   * Encode an Object of type T to a data buffer. This is useful for scenarios,
   * that distinct messages (or events) are encoded and handled individually,
   * in fully aggregated form.
   * <p>By default this method raises {@link UnsupportedOperationException}
   * and it is expected that some encoders cannot produce a single buffer or
   * cannot do so synchronously (e.g. encoding a {@code Resource}).
   *
   * @param value the value to be encoded
   * @param bufferFactory for creating the output {@code DataBuffer}
   * @param valueType the type for the value being encoded
   * @param mimeType the MIME type for the output content (optional)
   * @param hints additional information about how to encode
   * @return the encoded content
   */
  default DataBuffer encodeValue(
          T value, DataBufferFactory bufferFactory,
          ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
    // It may not be possible to produce a single DataBuffer synchronously
    throw new UnsupportedOperationException();
  }

  /**
   * Return the list of MIME types supported by this Encoder. The list may not
   * apply to every possible target element type and calls to this method should
   * typically be guarded via {@link #canEncode(ResolvableType, MimeType)
   * canEncode(elementType, null)}. The list may also exclude MIME types
   * supported only for a specific element type. Alternatively, use
   * {@link #getEncodableMimeTypes(ResolvableType)} for a more precise list.
   *
   * @return the list of supported MIME types
   */
  List<MimeType> getEncodableMimeTypes();

  /**
   * Return the list of MIME types supported by this Encoder for the given type
   * of element. This list may differ from the {@link #getEncodableMimeTypes()}
   * if the Encoder doesn't support the element type or if it supports it only
   * for a subset of MIME types.
   *
   * @param elementType the type of element to check for encoding
   * @return the list of MIME types supported for the given element type
   */
  default List<MimeType> getEncodableMimeTypes(ResolvableType elementType) {
    return canEncode(elementType, null) ? getEncodableMimeTypes() : Collections.emptyList();
  }

}
