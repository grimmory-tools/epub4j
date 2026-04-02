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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Spine;
import org.grimmory.epub4j.domain.SpineReference;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Removes Sections from the page flow that differ only from the previous section's href by the '#'
 * in the url.
 *
 * @author paul
 */
public class SectionHrefSanityCheckBookProcessor implements BookProcessor {

  @Override
  public Book processBook(Book book) {
    book.getSpine().setSpineReferences(checkSpineReferences(book.getSpine()));
    return book;
  }

  private static List<SpineReference> checkSpineReferences(Spine spine) {
    List<SpineReference> result = new ArrayList<>(spine.size());
    Resource previousResource = null;
    for (SpineReference spineReference : spine.getSpineReferences()) {
      if (spineReference.getResource() == null
          || StringUtils.isBlank(spineReference.getResource().getHref())) {
        continue;
      }
      if (previousResource == null
          || spineReference.getResource() == null
          || (!(spineReference.getResource().getHref().equals(previousResource.getHref())))) {
        result.add(spineReference);
      }
      previousResource = spineReference.getResource();
    }
    return result;
  }
}
