/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Originally from epub4j (https://github.com/documentnode/epub4j)
 * Copyright (C) Paul Siegmund and epub4j contributors
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
package org.grimmory.epub4j.bookprocessor;

import java.io.IOException;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Helper class for BookProcessors that only manipulate html type resources.
 *
 * @author paul
 */
public abstract class HtmlBookProcessor implements BookProcessor {

  private static final System.Logger log = System.getLogger(HtmlBookProcessor.class.getName());
  public static final String OUTPUT_ENCODING = "UTF-8";

  @Override
  public Book processBook(Book book) {
    for (Resource resource : book.getResources().getAll()) {
      try {
        cleanupResource(resource, book);
      } catch (IOException e) {
        log.log(System.Logger.Level.ERROR, e.getMessage(), e);
      }
    }
    return book;
  }

  private void cleanupResource(Resource resource, Book book) throws IOException {
    if (resource.getMediaType() == MediaTypes.XHTML) {
      byte[] cleanedHtml = processHtml(resource, book);
      resource.setData(cleanedHtml);
      resource.setInputEncoding(Constants.CHARACTER_ENCODING);
    }
  }

  protected abstract byte[] processHtml(Resource resource, Book book) throws IOException;
}
