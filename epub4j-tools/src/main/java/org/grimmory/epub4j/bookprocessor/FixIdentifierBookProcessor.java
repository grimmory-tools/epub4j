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

import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * If the book has no identifier it adds a generated UUID as identifier.
 *
 * @author paul
 */
public class FixIdentifierBookProcessor implements BookProcessor {

  @Override
  public Book processBook(Book book) {
    if (book.getMetadata().getIdentifiers().isEmpty()) {
      book.getMetadata().addIdentifier(new Identifier());
    }
    return book;
  }
}
