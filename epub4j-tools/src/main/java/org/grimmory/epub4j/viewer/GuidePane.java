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
import java.util.ArrayList;
import java.util.List;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.grimmory.epub4j.browsersupport.NavigationEvent;
import org.grimmory.epub4j.browsersupport.NavigationEventListener;
import org.grimmory.epub4j.browsersupport.Navigator;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Guide;
import org.grimmory.epub4j.domain.GuideReference;

/**
 * Creates a Panel for navigating a Book via its Guide
 *
 * @author paul
 */
public class GuidePane extends JScrollPane implements NavigationEventListener {

  @Serial private static final long serialVersionUID = -8988054938907109295L;
  private final Navigator navigator;

  public GuidePane(Navigator navigator) {
    this.navigator = navigator;
    navigator.addNavigationEventListener(this);
    initBook(navigator.getBook());
  }

  private void initBook(Book book) {
    if (book == null) {
      return;
    }
    getViewport().removeAll();
    JTable table =
        new JTable(createTableData(navigator.getBook().getGuide()), new String[] {"", ""});
    //		table.setEnabled(false);
    table.setFillsViewportHeight(true);
    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (navigator.getBook() == null) {
                return;
              }
              int guideIndex = e.getFirstIndex();
              GuideReference guideReference =
                  navigator.getBook().getGuide().getReferences().get(guideIndex);
              navigator.gotoResource(guideReference.getResource(), GuidePane.this);
            });
    getViewport().add(table);
  }

  private static Object[][] createTableData(Guide guide) {
    List<String[]> result = new ArrayList<>();
    for (GuideReference guideReference : guide.getReferences()) {
      result.add(new String[] {guideReference.getType(), guideReference.getTitle()});
    }
    return result.toArray(new Object[result.size()][2]);
  }

  @Override
  public void navigationPerformed(NavigationEvent navigationEvent) {
    if (navigationEvent.isBookChanged()) {
      initBook(navigationEvent.getCurrentBook());
    }
  }
}
