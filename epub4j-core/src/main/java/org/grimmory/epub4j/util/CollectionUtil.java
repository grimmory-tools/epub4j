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
package org.grimmory.epub4j.util;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

public class CollectionUtil {

  /**
   * Wraps an Enumeration around an Iterator
   *
   * @param <T>
   * @author paul.siegmann
   */
  private record IteratorEnumerationAdapter<T>(Iterator<T> iterator) implements Enumeration<T> {

    @Override
    public boolean hasMoreElements() {
      return iterator.hasNext();
    }

    @Override
    public T nextElement() {
      return iterator.next();
    }
  }

  /**
   * Creates an Enumeration out of the given Iterator.
   *
   * @param <T>
   * @param it
   * @return an Enumeration created out of the given Iterator.
   */
  public static <T> Enumeration<T> createEnumerationFromIterator(Iterator<T> it) {
    return new IteratorEnumerationAdapter<>(it);
  }

  /**
   * Returns the first element of the list, null if the list is null or empty.
   *
   * @param <T>
   * @param list
   * @return the first element of the list, null if the list is null or empty.
   */
  public static <T> T first(List<T> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    return list.getFirst();
  }

  /**
   * Whether the given collection is null or has no elements.
   *
   * @param collection
   * @return Whether the given collection is null or has no elements.
   */
  public static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }
}
