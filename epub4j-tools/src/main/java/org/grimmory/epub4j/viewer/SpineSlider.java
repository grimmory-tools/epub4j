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
package org.grimmory.epub4j.viewer;

import java.io.Serial;
import javax.swing.JSlider;
import org.grimmory.epub4j.browsersupport.NavigationEvent;
import org.grimmory.epub4j.browsersupport.NavigationEventListener;
import org.grimmory.epub4j.browsersupport.Navigator;
import org.grimmory.epub4j.domain.Book;

// package
class SpineSlider extends JSlider implements NavigationEventListener {

  /** */
  @Serial private static final long serialVersionUID = 8436441824668551056L;

  private final Navigator navigator;

  public SpineSlider(Navigator navigator) {
    super(JSlider.HORIZONTAL);
    this.navigator = navigator;
    navigator.addNavigationEventListener(this);
    setPaintLabels(false);
    addChangeListener(
        evt -> {
          JSlider slider = (JSlider) evt.getSource();
          int value = slider.getValue();
          SpineSlider.this.navigator.gotoSpineSection(value, SpineSlider.this);
        });
    initBook(navigator.getBook());
  }

  private void initBook(Book book) {
    if (book == null) {
      return;
    }
    super.setMinimum(0);
    super.setMaximum(book.getSpine().size() - 1);
    super.setValue(0);
    //			setPaintTicks(true);
    updateToolTip();
  }

  private void updateToolTip() {
    String tooltip = "";
    if (navigator.getCurrentSpinePos() >= 0 && navigator.getBook() != null) {
      tooltip = navigator.getCurrentSpinePos() + 1 + " / " + navigator.getBook().getSpine().size();
    }
    setToolTipText(tooltip);
  }

  @Override
  public void navigationPerformed(NavigationEvent navigationEvent) {
    updateToolTip();
    if (this == navigationEvent.getSource()) {
      return;
    }

    if (navigationEvent.isBookChanged()) {
      initBook(navigationEvent.getCurrentBook());
    } else if (navigationEvent.isResourceChanged()) {
      setValue(navigationEvent.getCurrentSpinePos());
    }
  }
}
