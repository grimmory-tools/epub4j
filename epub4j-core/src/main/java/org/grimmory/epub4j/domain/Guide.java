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
import java.util.List;

/**
 * The guide is a selection of special pages of the book. Examples of these are the cover, list of
 * illustrations, etc.
 *
 * <p>It is an optional part of an epub, and support for the various types of references varies by
 * reader.
 *
 * <p>The only part of this that is heavily used is the cover page.
 *
 * @author paul
 */
public class Guide implements Serializable {

  /** */
  @Serial private static final long serialVersionUID = -6256645339915751189L;

  public static final String DEFAULT_COVER_TITLE = GuideReference.COVER;

  private List<GuideReference> references = new ArrayList<>();
  private static final int COVERPAGE_NOT_FOUND = -1;
  private static final int COVERPAGE_UNITIALIZED = -2;

  private int coverPageIndex = -1;

  public List<GuideReference> getReferences() {
    return references;
  }

  public void setReferences(List<GuideReference> references) {
    this.references = references;
    uncheckCoverPage();
  }

  private void uncheckCoverPage() {
    coverPageIndex = COVERPAGE_UNITIALIZED;
  }

  public GuideReference getCoverReference() {
    checkCoverPage();
    if (coverPageIndex >= 0) {
      return references.get(coverPageIndex);
    }
    return null;
  }

  public void setCoverReference(GuideReference guideReference) {
    if (coverPageIndex >= 0) {
      references.set(coverPageIndex, guideReference);
    } else {
      references.addFirst(guideReference);
      coverPageIndex = 0;
    }
  }

  private void checkCoverPage() {
    if (coverPageIndex == COVERPAGE_UNITIALIZED) {
      initCoverPage();
    }
  }

  private void initCoverPage() {
    int result = COVERPAGE_NOT_FOUND;
    for (int i = 0; i < references.size(); i++) {
      GuideReference guideReference = references.get(i);
      if (guideReference.getType().equals(GuideReference.COVER)) {
        result = i;
        break;
      }
    }
    coverPageIndex = result;
  }

  /**
   * The coverpage of the book.
   *
   * @return The coverpage of the book.
   */
  public Resource getCoverPage() {
    GuideReference guideReference = getCoverReference();
    if (guideReference == null) {
      return null;
    }
    return guideReference.getResource();
  }

  public void setCoverPage(Resource coverPage) {
    GuideReference coverpageGuideReference =
        new GuideReference(coverPage, GuideReference.COVER, DEFAULT_COVER_TITLE);
    setCoverReference(coverpageGuideReference);
  }

  public void addReference(GuideReference reference) {
    this.references.add(reference);
    uncheckCoverPage();
  }

  /**
   * A list of all GuideReferences that have the given referenceTypeName (ignoring case).
   *
   * @param referenceTypeName
   * @return A list of all GuideReferences that have the given referenceTypeName (ignoring case).
   */
  public List<GuideReference> getGuideReferencesByType(String referenceTypeName) {
    List<GuideReference> result = new ArrayList<>();
    for (GuideReference guideReference : references) {
      if (referenceTypeName.equalsIgnoreCase(guideReference.getType())) {
        result.add(guideReference);
      }
    }
    return result;
  }
}
