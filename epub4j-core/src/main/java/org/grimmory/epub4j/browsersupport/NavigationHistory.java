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

import java.util.ArrayList;
import java.util.List;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;

/**
 * A history of the user's locations with the epub.
 *
 * @author paul.siegmann
 */
public class NavigationHistory implements NavigationEventListener {

  public static final int DEFAULT_MAX_HISTORY_SIZE = 1000;
  private static final long DEFAULT_HISTORY_WAIT_TIME = 1000;

  private static class Location {

    private String href;

    public Location(String href) {
      super();
      this.href = href;
    }

    public void setHref(String href) {
      this.href = href;
    }

    public String getHref() {
      return href;
    }
  }

  private long lastUpdateTime = 0;
  private List<Location> locations = new ArrayList<>();
  private final Navigator navigator;
  private int currentPos = -1;
  private int currentSize = 0;
  private int maxHistorySize = DEFAULT_MAX_HISTORY_SIZE;
  private long historyWaitTime = DEFAULT_HISTORY_WAIT_TIME;

  public NavigationHistory(Navigator navigator) {
    this.navigator = navigator;
    navigator.addNavigationEventListener(this);
    initBook(navigator.getBook());
  }

  public int getCurrentPos() {
    return currentPos;
  }

  public int getCurrentSize() {
    return currentSize;
  }

  public void initBook(Book book) {
    if (book == null) {
      return;
    }
    locations = new ArrayList<>();
    currentPos = -1;
    currentSize = 0;
    if (navigator.getCurrentResource() != null) {
      addLocation(navigator.getCurrentResource().getHref());
    }
  }

  /**
   * If the time between a navigation event is less than the historyWaitTime then the new location
   * is not added to the history.
   *
   * <p>When a user is rapidly viewing many pages using the slider we do not want all of them to be
   * added to the history.
   *
   * @return the time we wait before adding the page to the history
   */
  public long getHistoryWaitTime() {
    return historyWaitTime;
  }

  public void setHistoryWaitTime(long historyWaitTime) {
    this.historyWaitTime = historyWaitTime;
  }

  public void addLocation(Resource resource) {
    if (resource == null) {
      return;
    }
    addLocation(resource.getHref());
  }

  /**
   * Adds the location after the current position. If the currentposition is not the end of the list
   * then the elements between the current element and the end of the list will be discarded.
   *
   * <p>Does nothing if the new location matches the current location. <br>
   * If this nr of locations becomes larger then the historySize then the first item(s) will be
   * removed.
   *
   * @param location
   */
  public void addLocation(Location location) {
    // do nothing if the new location matches the current location
    if (!(locations.isEmpty()) && location.getHref().equals(locations.get(currentPos).getHref())) {
      return;
    }
    currentPos++;
    if (currentPos != currentSize) {
      locations.set(currentPos, location);
    } else {
      locations.add(location);
      checkHistorySize();
    }
    currentSize = currentPos + 1;
  }

  /** Removes all elements that are too much for the maxHistorySize out of the history. */
  private void checkHistorySize() {
    while (locations.size() > maxHistorySize) {
      locations.removeFirst();
      currentSize--;
      currentPos--;
    }
  }

  public void addLocation(String href) {
    addLocation(new Location(href));
  }

  private String getLocationHref(int pos) {
    if (pos < 0 || pos >= locations.size()) {
      return null;
    }
    return locations.get(currentPos).getHref();
  }

  /**
   * Moves the current positions delta positions.
   *
   * <p>move(-1) to go one position back in history.<br>
   * move(1) to go one position forward.<br>
   *
   * @param delta
   */
  public void move(int delta) {
    if (((currentPos + delta) < 0) || ((currentPos + delta) >= currentSize)) {
      return;
    }
    currentPos += delta;
    navigator.gotoResource(getLocationHref(currentPos), this);
  }

  /**
   * If this is not the source of the navigationEvent then the addLocation will be called with the
   * href of the currentResource in the navigationEvent.
   */
  @Override
  public void navigationPerformed(NavigationEvent navigationEvent) {
    if (this == navigationEvent.getSource()) {
      return;
    }
    if (navigationEvent.getCurrentResource() == null) {
      return;
    }

    if ((System.currentTimeMillis() - this.lastUpdateTime) > historyWaitTime) {
      // if the user scrolled rapidly through the pages then the last page
      // will not be added to the history. We fix that here:
      addLocation(navigationEvent.getOldResource());

      addLocation(navigationEvent.getCurrentResource().getHref());
    }
    lastUpdateTime = System.currentTimeMillis();
  }

  public String getCurrentHref() {
    if (currentPos < 0 || currentPos >= locations.size()) {
      return null;
    }
    return locations.get(currentPos).getHref();
  }

  public void setMaxHistorySize(int maxHistorySize) {
    this.maxHistorySize = maxHistorySize;
  }

  public int getMaxHistorySize() {
    return maxHistorySize;
  }
}
