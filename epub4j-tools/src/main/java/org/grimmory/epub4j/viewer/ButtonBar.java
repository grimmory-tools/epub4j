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

import java.awt.GridLayout;
import java.io.Serial;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.grimmory.epub4j.browsersupport.Navigator;

/** Creates a panel with the first,previous,next and last buttons. */
class ButtonBar extends JPanel {
  @Serial private static final long serialVersionUID = 6431437924245035812L;

  private final JButton startButton = ViewerUtil.createButton("chapter-first", "|<");
  private final JButton previousChapterButton = ViewerUtil.createButton("chapter-previous", "<<");
  private final JButton previousPageButton = ViewerUtil.createButton("page-previous", "<");
  private final JButton nextPageButton = ViewerUtil.createButton("page-next", ">");
  private final JButton nextChapterButton = ViewerUtil.createButton("chapter-next", ">>");
  private final JButton endButton = ViewerUtil.createButton("chapter-last", ">|");
  private final ContentPane chapterPane;
  private final ValueHolder<Navigator> navigatorHolder = new ValueHolder<>();

  public ButtonBar(Navigator navigator, ContentPane chapterPane) {
    super(new GridLayout(0, 4));
    this.chapterPane = chapterPane;

    JPanel bigPrevious = new JPanel(new GridLayout(0, 2));
    bigPrevious.add(startButton);
    bigPrevious.add(previousChapterButton);
    add(bigPrevious);

    add(previousPageButton);
    add(nextPageButton);

    JPanel bigNext = new JPanel(new GridLayout(0, 2));
    bigNext.add(nextChapterButton);
    bigNext.add(endButton);
    add(bigNext);

    setSectionWalker(navigator);
  }

  public void setSectionWalker(Navigator navigator) {
    navigatorHolder.setValue(navigator);

    startButton.addActionListener(
        e -> navigatorHolder.getValue().gotoFirstSpineSection(ButtonBar.this));
    previousChapterButton.addActionListener(
        e -> navigatorHolder.getValue().gotoPreviousSpineSection(ButtonBar.this));
    previousPageButton.addActionListener(e -> chapterPane.gotoPreviousPage());

    nextPageButton.addActionListener(e -> chapterPane.gotoNextPage());
    nextChapterButton.addActionListener(
        e -> navigatorHolder.getValue().gotoNextSpineSection(ButtonBar.this));

    endButton.addActionListener(
        e -> navigatorHolder.getValue().gotoLastSpineSection(ButtonBar.this));
  }
}
