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

import java.awt.BorderLayout;
import java.io.Serial;
import javax.swing.JPanel;
import org.grimmory.epub4j.browsersupport.Navigator;

public class BrowseBar extends JPanel {

  @Serial private static final long serialVersionUID = -5745389338067538254L;

  public BrowseBar(Navigator navigator, ContentPane chapterPane) {
    super(new BorderLayout());
    add(new ButtonBar(navigator, chapterPane), BorderLayout.CENTER);
    add(new SpineSlider(navigator), BorderLayout.NORTH);
  }
}
