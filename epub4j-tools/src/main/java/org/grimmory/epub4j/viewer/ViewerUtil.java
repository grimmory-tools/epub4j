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

import java.awt.Image;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;

public class ViewerUtil {

  private static final System.Logger log = System.getLogger(ViewerUtil.class.getName());

  /**
   * Creates a button with the given icon. The icon will be loaded from the classpath. If loading
   * the icon is unsuccessful it will use the defaultLabel.
   *
   * @param iconName
   * @param backupLabel
   * @return a button with the given icon.
   */
  // package
  static JButton createButton(String iconName, String backupLabel) {
    ImageIcon icon = createImageIcon(iconName);
    if (icon == null) {
      return new JButton(backupLabel);
    }
    return new JButton(icon);
  }

  static ImageIcon createImageIcon(String iconName) {
    ImageIcon result = null;
    String fullIconPath = "/viewer/icons/" + iconName + ".png";
    try {
      Image image = ImageIO.read(ViewerUtil.class.getResourceAsStream(fullIconPath));
      result = new ImageIcon(image);
    } catch (Exception e) {
      log.log(System.Logger.Level.ERROR, "Icon '" + fullIconPath + "' not found");
    }
    return result;
  }
}
