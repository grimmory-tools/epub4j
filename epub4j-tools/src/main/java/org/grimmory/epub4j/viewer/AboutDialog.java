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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serial;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
 * First stab at an about dialog.
 *
 * @author paul.siegmann
 */
public class AboutDialog extends JDialog {

  @Serial private static final long serialVersionUID = -1766802200843275782L;

  public AboutDialog(JFrame parent) {
    super(parent, true);

    super.setResizable(false);
    super.getContentPane().setLayout(new GridLayout(3, 1));
    super.setSize(400, 150);
    super.setTitle("About epub4j");
    super.setLocationRelativeTo(parent);

    JButton close = new JButton("Close");
    close.addActionListener(e -> AboutDialog.this.dispose());
    super.getRootPane().setDefaultButton(close);
    add(new JLabel("epub4j viewer"));
    add(new JLabel("https://github.com/documentnode/epub4j"));
    add(close);
    super.addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            AboutDialog.this.dispose();
          }
        });
    pack();
    setVisible(true);
  }
}
