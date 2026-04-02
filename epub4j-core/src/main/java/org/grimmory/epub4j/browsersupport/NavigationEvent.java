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
package org.grimmory.epub4j.browsersupport;

import java.io.Serial;
import java.util.EventObject;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.util.StringUtil;

/**
 * Used to tell NavigationEventListener just what kind of navigation action the user just did.
 *
 * @author paul
 */
public class NavigationEvent extends EventObject {

  @Serial private static final long serialVersionUID = -6346750144308952762L;

  private Resource oldResource;
  private int oldSpinePos;
  private Navigator navigator;
  private Book oldBook;
  private int oldSectionPos;
  private String oldFragmentId;

  public NavigationEvent(Object source) {
    super(source);
  }

  public NavigationEvent(Object source, Navigator navigator) {
    super(source);
    this.navigator = navigator;
    this.oldBook = navigator.getBook();
    this.oldFragmentId = navigator.getCurrentFragmentId();
    this.oldSectionPos = navigator.getCurrentSectionPos();
    this.oldResource = navigator.getCurrentResource();
    this.oldSpinePos = navigator.getCurrentSpinePos();
  }

  /**
   * The previous position within the section.
   *
   * @return The previous position within the section.
   */
  public int getOldSectionPos() {
    return oldSectionPos;
  }

  public Navigator getNavigator() {
    return navigator;
  }

  public String getOldFragmentId() {
    return oldFragmentId;
  }

  // package
  void setOldFragmentId(String oldFragmentId) {
    this.oldFragmentId = oldFragmentId;
  }

  public Book getOldBook() {
    return oldBook;
  }

  // package
  void setOldPagePos(int oldPagePos) {
    this.oldSectionPos = oldPagePos;
  }

  public int getCurrentSectionPos() {
    return navigator.getCurrentSectionPos();
  }

  public int getOldSpinePos() {
    return oldSpinePos;
  }

  public int getCurrentSpinePos() {
    return navigator.getCurrentSpinePos();
  }

  public String getCurrentFragmentId() {
    return navigator.getCurrentFragmentId();
  }

  public boolean isBookChanged() {
    if (oldBook == null) {
      return true;
    }
    return oldBook != navigator.getBook();
  }

  public boolean isSpinePosChanged() {
    return oldSpinePos != getCurrentSpinePos();
  }

  public boolean isFragmentChanged() {
    return !StringUtil.equals(oldFragmentId, getCurrentFragmentId());
  }

  public Resource getOldResource() {
    return oldResource;
  }

  public Resource getCurrentResource() {
    return navigator.getCurrentResource();
  }

  public void setOldResource(Resource oldResource) {
    this.oldResource = oldResource;
  }

  public void setOldSpinePos(int oldSpinePos) {
    this.oldSpinePos = oldSpinePos;
  }

  public void setNavigator(Navigator navigator) {
    this.navigator = navigator;
  }

  public void setOldBook(Book oldBook) {
    this.oldBook = oldBook;
  }

  public Book getCurrentBook() {
    return navigator.getBook();
  }

  public boolean isResourceChanged() {
    return oldResource != getCurrentResource();
  }

  public String toString() {
    return StringUtil.toString(
        "oldSectionPos",
        oldSectionPos,
        "oldResource",
        oldResource,
        "oldBook",
        oldBook,
        "oldFragmentId",
        oldFragmentId,
        "oldSpinePos",
        oldSpinePos,
        "currentPagePos",
        getCurrentSectionPos(),
        "currentResource",
        getCurrentResource(),
        "currentBook",
        getCurrentBook(),
        "currentFragmentId",
        getCurrentFragmentId(),
        "currentSpinePos",
        getCurrentSpinePos());
  }

  public boolean isSectionPosChanged() {
    return oldSectionPos != getCurrentSectionPos();
  }
}
