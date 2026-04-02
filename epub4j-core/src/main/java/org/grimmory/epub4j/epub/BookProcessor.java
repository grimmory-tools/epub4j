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

import org.grimmory.epub4j.domain.Book;

/**
 * Post-processes a book.
 *
 * <p>Can be used to clean up a book after reading or before writing.
 *
 * @author paul
 */
public interface BookProcessor {

  /** A BookProcessor that returns the input book unchanged. */
  BookProcessor IDENTITY_BOOKPROCESSOR = book -> book;

  Book processBook(Book book);
}
