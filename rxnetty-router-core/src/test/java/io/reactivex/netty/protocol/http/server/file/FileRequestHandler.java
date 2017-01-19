/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.netty.protocol.http.server.file;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

/**
 * Base implementation for serving local files.  Resolving the request URI to
 * a local file URI is deferred to the subclass.
 *
 * @author elandau
 *
 */
public abstract class FileRequestHandler extends AbstractFileRequestHandler {
  private static final Logger logger = LoggerFactory.getLogger(FileRequestHandler.class);

  private static final int CHUNK_SIZE = 8192;

  @Override
  public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
    // We don't support GET.
    if (!request.getHttpMethod().equals(GET)) {
      response.setStatus(METHOD_NOT_ALLOWED);
      return response.sendHeaders();
    }

    RandomAccessFile raf = null;

    String sanitizedUri = sanitizeUri(request.getUri());
    if (sanitizedUri == null) {
      response.setStatus(FORBIDDEN);
      return response.sendHeaders();
    }

    URI uri = resolveUri(sanitizedUri);
    if (uri == null) {
      response.setStatus(NOT_FOUND);
      return response.sendHeaders();
    }

    File file = new File(uri);
    if (file.isHidden() || !file.exists()) {
      response.setStatus(NOT_FOUND);
      return response.sendHeaders();
    }

    if (file.isDirectory()) {
      response.setStatus(FORBIDDEN);
      return response.sendHeaders();
    }

    if (!file.isFile()) {
      response.setStatus(FORBIDDEN);
      return response.sendHeaders();
    }

    long fileLength;
    try {
      raf = new RandomAccessFile(file, "r");
      fileLength = raf.length();
    }
    catch (Exception e) {
      logger.warn("Error accessing file {}", uri, e);
      if (raf != null) {
        try {
          raf.close();
        } catch (IOException e1) {
          logger.warn("Error closing file {}", uri, e1);
        }
      }
      return Observable.error(e);
    }

    // Cache Validation
    String ifModifiedSince = request.getHeader(IF_MODIFIED_SINCE);
    if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
      SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
      Date ifModifiedSinceDate = null;
      try {
        ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);
      } catch (ParseException e) {
        logger.warn("Failed to parse {} header", IF_MODIFIED_SINCE);
      }

      if (ifModifiedSinceDate != null) {
        // Only compare up to the second because the datetime format we send to the client
        // does not have milliseconds
        long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
        long fileLastModifiedSeconds = file.lastModified() / 1000;
        if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
          response.setStatus(NOT_MODIFIED);
          setDateHeader(response, dateFormatter);
          return response.sendHeaders();
        }
      }
    }

    response.setStatus(OK);
    response.setHeader("Content-Length", fileLength);
    setContentTypeHeader(response, file);
    setDateAndCacheHeaders(response, file);

    if (request.isKeepAlive()) {
      response.setHeader(CONNECTION, KEEP_ALIVE);
    }

    try {
      return response.write(Observable.just(Unpooled.copiedBuffer(IOUtils.toByteArray(new FileInputStream(file)))));
    } catch (IOException e) {
      logger.warn("Failed to write chunked file {}", e);
      return Observable.error(e);
    }
  }

  protected abstract URI resolveUri(String path);
}
