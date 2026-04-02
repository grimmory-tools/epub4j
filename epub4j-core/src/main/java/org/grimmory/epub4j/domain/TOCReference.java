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
package org.grimmory.epub4j.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An item in the Table of Contents.
 *
 * @see TableOfContents
 * @author paul
 */
public class TOCReference extends TitledResourceReference implements Serializable {

  @Serial private static final long serialVersionUID = 5787958246077042456L;
  private List<TOCReference> children;
  private static final Comparator<TOCReference> COMPARATOR_BY_TITLE_IGNORE_CASE =
      (tocReference1, tocReference2) ->
          String.CASE_INSENSITIVE_ORDER.compare(tocReference1.getTitle(), tocReference2.getTitle());

  public TOCReference() {
    this(null, null, null);
  }

  public TOCReference(String name, Resource resource) {
    this(name, resource, null);
  }

  public TOCReference(String name, Resource resource, String fragmentId) {
    this(name, resource, fragmentId, new ArrayList<>());
  }

  public TOCReference(
      String title, Resource resource, String fragmentId, List<TOCReference> children) {
    super(resource, title, fragmentId);
    this.children = children;
  }

  public static Comparator<TOCReference> getComparatorByTitleIgnoreCase() {
    return COMPARATOR_BY_TITLE_IGNORE_CASE;
  }

  public List<TOCReference> getChildren() {
    return children;
  }

  public TOCReference addChildSection(TOCReference childSection) {
    this.children.add(childSection);
    return childSection;
  }

  public void setChildren(List<TOCReference> children) {
    this.children = children;
  }
}
