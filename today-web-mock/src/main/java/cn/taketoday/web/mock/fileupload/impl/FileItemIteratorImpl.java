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
package cn.taketoday.web.mock.fileupload.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;

import cn.taketoday.web.mock.fileupload.FileItem;
import cn.taketoday.web.mock.fileupload.FileItemHeaders;
import cn.taketoday.web.mock.fileupload.FileItemIterator;
import cn.taketoday.web.mock.fileupload.FileItemStream;
import cn.taketoday.web.mock.fileupload.FileUploadBase;
import cn.taketoday.web.mock.fileupload.FileUploadException;
import cn.taketoday.web.mock.fileupload.IOUtils;
import cn.taketoday.web.mock.fileupload.MultipartStream;
import cn.taketoday.web.mock.fileupload.ProgressListener;
import cn.taketoday.web.mock.fileupload.RequestContext;
import cn.taketoday.web.mock.fileupload.UploadContext;
import cn.taketoday.web.mock.fileupload.util.LimitedInputStream;

/**
 * The iterator, which is returned by
 * {@link FileUploadBase#getItemIterator(RequestContext)}.
 */
public class FileItemIteratorImpl implements FileItemIterator {
  /**
   * The file uploads processing utility.
   *
   * @see FileUploadBase
   */
  private final FileUploadBase fileUploadBase;
  /**
   * The request context.
   *
   * @see RequestContext
   */
  private final RequestContext ctx;
  /**
   * The maximum allowed size of a complete request.
   */
  private long sizeMax;
  /**
   * The maximum allowed size of a single uploaded file.
   */
  private long fileSizeMax;

  @Override
  public long getSizeMax() {
    return sizeMax;
  }

  @Override
  public void setSizeMax(final long sizeMax) {
    this.sizeMax = sizeMax;
  }

  @Override
  public long getFileSizeMax() {
    return fileSizeMax;
  }

  @Override
  public void setFileSizeMax(final long fileSizeMax) {
    this.fileSizeMax = fileSizeMax;
  }

  /**
   * The multi part stream to process.
   */
  private MultipartStream multiPartStream;

  /**
   * The notifier, which used for triggering the
   * {@link ProgressListener}.
   */
  private MultipartStream.ProgressNotifier progressNotifier;

  /**
   * The boundary, which separates the various parts.
   */
  private byte[] multiPartBoundary;

  /**
   * The item, which we currently process.
   */
  private FileItemStreamImpl currentItem;

  /**
   * The current items field name.
   */
  private String currentFieldName;

  /**
   * Whether we are currently skipping the preamble.
   */
  private boolean skipPreamble;

  /**
   * Whether the current item may still be read.
   */
  private boolean itemValid;

  /**
   * Whether we have seen the end of the file.
   */
  private boolean eof;

  /**
   * Creates a new instance.
   *
   * @param fileUploadBase Main processor.
   * @param requestContext The request context.
   * @throws FileUploadException An error occurred while
   * parsing the request.
   * @throws IOException An I/O error occurred.
   */
  public FileItemIteratorImpl(final FileUploadBase fileUploadBase, final RequestContext requestContext)
          throws FileUploadException, IOException {
    this.fileUploadBase = fileUploadBase;
    sizeMax = fileUploadBase.getSizeMax();
    fileSizeMax = fileUploadBase.getFileSizeMax();
    ctx = Objects.requireNonNull(requestContext, "requestContext");
    skipPreamble = true;
    findNextItem();
  }

  protected void init(final FileUploadBase fileUploadBase, @SuppressWarnings("unused") final RequestContext pRequestContext)
          throws FileUploadException, IOException {
    final String contentType = ctx.getContentType();
    if ((null == contentType)
            || (!contentType.toLowerCase(Locale.ENGLISH).startsWith(FileUploadBase.MULTIPART))) {
      throw new InvalidContentTypeException(
              String.format("the request doesn't contain a %s or %s stream, content type header is %s",
                      FileUploadBase.MULTIPART_FORM_DATA, FileUploadBase.MULTIPART_MIXED, contentType));
    }

    final long requestSize = ((UploadContext) ctx).contentLength();

    final InputStream input; // N.B. this is eventually closed in MultipartStream processing
    if (sizeMax >= 0) {
      if (requestSize != -1 && requestSize > sizeMax) {
        throw new SizeLimitExceededException(String.format("the request was rejected because its size (%s) exceeds the configured maximum (%s)", requestSize, sizeMax),
                requestSize, sizeMax);
      }
      // N.B. this is eventually closed in MultipartStream processing
      input = new LimitedInputStream(ctx.getInputStream(), sizeMax) {
        @Override
        protected void raiseError(final long pSizeMax, final long pCount)
                throws IOException {
          final FileUploadException ex = new SizeLimitExceededException(
                  String.format("the request was rejected because its size (%s) exceeds the configured maximum (%s)",
                          pCount, pSizeMax),
                  pCount, pSizeMax);
          throw new FileUploadIOException(ex);
        }
      };
    }
    else {
      input = ctx.getInputStream();
    }

    String charEncoding = fileUploadBase.getHeaderEncoding();
    if (charEncoding == null) {
      charEncoding = ctx.getCharacterEncoding();
    }

    multiPartBoundary = fileUploadBase.getBoundary(contentType);
    if (multiPartBoundary == null) {
      IOUtils.closeQuietly(input); // avoid possible resource leak
      throw new FileUploadException("the request was rejected because no multipart boundary was found");
    }

    progressNotifier = new MultipartStream.ProgressNotifier(fileUploadBase.getProgressListener(), requestSize);
    try {
      multiPartStream = new MultipartStream(input, multiPartBoundary, progressNotifier);
    }
    catch (final IllegalArgumentException iae) {
      IOUtils.closeQuietly(input); // avoid possible resource leak
      throw new InvalidContentTypeException(
              String.format("The boundary specified in the %s header is too long", FileUploadBase.CONTENT_TYPE), iae);
    }
    multiPartStream.setHeaderEncoding(charEncoding);
  }

  public MultipartStream getMultiPartStream() throws FileUploadException, IOException {
    if (multiPartStream == null) {
      init(fileUploadBase, ctx);
    }
    return multiPartStream;
  }

  /**
   * Called for finding the next item, if any.
   *
   * @return True, if an next item was found, otherwise false.
   * @throws IOException An I/O error occurred.
   */
  private boolean findNextItem() throws FileUploadException, IOException {
    if (eof) {
      return false;
    }
    if (currentItem != null) {
      currentItem.close();
      currentItem = null;
    }
    final MultipartStream multi = getMultiPartStream();
    for (; ; ) {
      final boolean nextPart;
      if (skipPreamble) {
        nextPart = multi.skipPreamble();
      }
      else {
        nextPart = multi.readBoundary();
      }
      if (!nextPart) {
        if (currentFieldName == null) {
          // Outer multipart terminated -> No more data
          eof = true;
          return false;
        }
        // Inner multipart terminated -> Return to parsing the outer
        multi.setBoundary(multiPartBoundary);
        currentFieldName = null;
        continue;
      }
      final FileItemHeaders headers = fileUploadBase.getParsedHeaders(multi.readHeaders());
      if (currentFieldName == null) {
        // We're parsing the outer multipart
        final String fieldName = fileUploadBase.getFieldName(headers);
        if (fieldName != null) {
          final String subContentType = headers.getHeader(FileUploadBase.CONTENT_TYPE);
          if (subContentType != null
                  && subContentType.toLowerCase(Locale.ENGLISH)
                  .startsWith(FileUploadBase.MULTIPART_MIXED)) {
            currentFieldName = fieldName;
            // Multiple files associated with this field name
            final byte[] subBoundary = fileUploadBase.getBoundary(subContentType);
            multi.setBoundary(subBoundary);
            skipPreamble = true;
            continue;
          }
          final String fileName = fileUploadBase.getFileName(headers);
          currentItem = new FileItemStreamImpl(this, fileName,
                  fieldName, headers.getHeader(FileUploadBase.CONTENT_TYPE),
                  fileName == null, getContentLength(headers));
          currentItem.setHeaders(headers);
          progressNotifier.noteItem();
          itemValid = true;
          return true;
        }
      }
      else {
        final String fileName = fileUploadBase.getFileName(headers);
        if (fileName != null) {
          currentItem = new FileItemStreamImpl(this, fileName,
                  currentFieldName,
                  headers.getHeader(FileUploadBase.CONTENT_TYPE),
                  false, getContentLength(headers));
          currentItem.setHeaders(headers);
          progressNotifier.noteItem();
          itemValid = true;
          return true;
        }
      }
      multi.discardBodyData();
    }
  }

  private long getContentLength(final FileItemHeaders pHeaders) {
    try {
      return Long.parseLong(pHeaders.getHeader(FileUploadBase.CONTENT_LENGTH));
    }
    catch (final Exception e) {
      return -1;
    }
  }

  /**
   * Returns, whether another instance of {@link FileItemStream}
   * is available.
   *
   * @return True, if one or more additional file items
   * are available, otherwise false.
   * @throws FileUploadException Parsing or processing the
   * file item failed.
   * @throws IOException Reading the file item failed.
   */
  @Override
  public boolean hasNext() throws FileUploadException, IOException {
    if (eof) {
      return false;
    }
    if (itemValid) {
      return true;
    }
    try {
      return findNextItem();
    }
    catch (final FileUploadIOException e) {
      // unwrap encapsulated SizeException
      throw (FileUploadException) e.getCause();
    }
  }

  /**
   * Returns the next available {@link FileItemStream}.
   *
   * @return FileItemStream instance, which provides
   * access to the next file item.
   * @throws NoSuchElementException No more items are
   * available. Use {@link #hasNext()} to prevent this exception.
   * @throws FileUploadException Parsing or processing the
   * file item failed.
   * @throws IOException Reading the file item failed.
   */
  @Override
  public FileItemStream next() throws FileUploadException, IOException {
    if (eof || (!itemValid && !hasNext())) {
      throw new NoSuchElementException();
    }
    itemValid = false;
    return currentItem;
  }

  @Override
  public List<FileItem> getFileItems() throws FileUploadException, IOException {
    final List<FileItem> items = new ArrayList<>();
    while (hasNext()) {
      final FileItemStream fis = next();
      final FileItem fi = fileUploadBase.getFileItemFactory().createItem(fis.getFieldName(),
              fis.getContentType(), fis.isFormField(), fis.getName());
      items.add(fi);
    }
    return items;
  }

}
