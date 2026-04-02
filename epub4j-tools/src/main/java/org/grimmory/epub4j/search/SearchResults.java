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
package org.grimmory.epub4j.search;

import java.util.ArrayList;
import java.util.List;
import org.grimmory.epub4j.domain.Book;

public class SearchResults {
  private String searchTerm;

  public String getSearchTerm() {
    return searchTerm;
  }

  public void setSearchTerm(String searchTerm) {
    this.searchTerm = searchTerm;
  }

  public Book getBook() {
    return book;
  }

  public void setBook(Book book) {
    this.book = book;
  }

  public List<SearchResult> getHits() {
    return hits;
  }

  public void setHits(List<SearchResult> hits) {
    this.hits = hits;
  }

  private Book book;
  private List<SearchResult> hits = new ArrayList<>();

  public boolean isEmpty() {
    return hits.isEmpty();
  }

  public int size() {
    return hits.size();
  }

  public void addAll(List<SearchResult> searchResults) {
    hits.addAll(searchResults);
  }
}
