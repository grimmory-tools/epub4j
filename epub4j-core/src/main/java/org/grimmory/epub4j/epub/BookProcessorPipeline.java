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
package org.grimmory.epub4j.epub;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.grimmory.epub4j.domain.Book;

/**
 * A book processor that combines several other bookprocessors
 *
 * <p>Fixes coverpage/coverimage. Cleans up the XHTML.
 *
 * @author paul.siegmann
 */
public class BookProcessorPipeline implements BookProcessor {

  private static final System.Logger log = System.getLogger(BookProcessorPipeline.class.getName());
  private List<BookProcessor> bookProcessors;

  public BookProcessorPipeline() {
    this(null);
  }

  public BookProcessorPipeline(List<BookProcessor> bookProcessingPipeline) {
    this.bookProcessors = bookProcessingPipeline;
  }

  @Override
  public Book processBook(Book book) {
    if (bookProcessors == null) {
      return book;
    }
    for (BookProcessor bookProcessor : bookProcessors) {
      try {
        book = bookProcessor.processBook(book);
      } catch (Exception e) {
        log.log(System.Logger.Level.ERROR, e.getMessage(), e);
      }
    }
    return book;
  }

  public void addBookProcessor(BookProcessor bookProcessor) {
    if (this.bookProcessors == null) {
      bookProcessors = new ArrayList<>();
    }
    this.bookProcessors.add(bookProcessor);
  }

  public void addBookProcessors(Collection<BookProcessor> bookProcessors) {
    if (this.bookProcessors == null) {
      this.bookProcessors = new ArrayList<>();
    }
    this.bookProcessors.addAll(bookProcessors);
  }

  public List<BookProcessor> getBookProcessors() {
    return bookProcessors;
  }

  public void setBookProcessingPipeline(List<BookProcessor> bookProcessingPipeline) {
    this.bookProcessors = bookProcessingPipeline;
  }
}
