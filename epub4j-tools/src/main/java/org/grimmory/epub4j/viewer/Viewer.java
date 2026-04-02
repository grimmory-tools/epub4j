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
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.lang3.StringUtils;
import org.grimmory.epub4j.browsersupport.Navigator;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.epub.EpubReader;
import org.grimmory.epub4j.epub.EpubWriter;

public class Viewer {

  static final System.Logger log = System.getLogger(Viewer.class.getName());
  private final JFrame mainWindow;
  private JSplitPane mainSplitPane;
  private JSplitPane rightSplitPane;
  private final Navigator navigator = new Navigator();

  public Viewer(InputStream bookStream) {
    mainWindow = createMainWindow();
    if (bookStream == null) {
      log.log(System.Logger.Level.ERROR, "No input stream available for opening the book");
      return;
    }
    Book book;
    try (InputStream inputStream = bookStream) {
      book = (new EpubReader()).readEpub(inputStream);
      gotoBook(book);
    } catch (IOException e) {
      log.log(System.Logger.Level.ERROR, e.getMessage(), e);
    }
  }

  public Viewer(Book book) {
    mainWindow = createMainWindow();
    gotoBook(book);
  }

  private JFrame createMainWindow() {
    JFrame result = new JFrame();
    result.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    result.setJMenuBar(createMenuBar());

    JPanel mainPanel = new JPanel(new BorderLayout());

    JSplitPane leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    leftSplitPane.setTopComponent(new TableOfContentsPane(navigator));
    leftSplitPane.setBottomComponent(new GuidePane(navigator));
    leftSplitPane.setOneTouchExpandable(true);
    leftSplitPane.setContinuousLayout(true);
    leftSplitPane.setResizeWeight(0.8);

    rightSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    rightSplitPane.setOneTouchExpandable(true);
    rightSplitPane.setContinuousLayout(true);
    rightSplitPane.setResizeWeight(1.0);
    ContentPane htmlPane = new ContentPane(navigator);
    JPanel contentPanel = new JPanel(new BorderLayout());
    contentPanel.add(htmlPane, BorderLayout.CENTER);
    BrowseBar browseBar = new BrowseBar(navigator, htmlPane);
    contentPanel.add(browseBar, BorderLayout.SOUTH);
    rightSplitPane.setLeftComponent(contentPanel);
    rightSplitPane.setRightComponent(new MetadataPane(navigator));

    mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    mainSplitPane.setLeftComponent(leftSplitPane);
    mainSplitPane.setRightComponent(rightSplitPane);
    mainSplitPane.setOneTouchExpandable(true);
    mainSplitPane.setContinuousLayout(true);
    mainSplitPane.setResizeWeight(0.0);

    mainPanel.add(mainSplitPane, BorderLayout.CENTER);
    mainPanel.setPreferredSize(new Dimension(1000, 750));
    mainPanel.add(new NavigationBar(navigator), BorderLayout.NORTH);

    result.add(mainPanel);
    result.pack();
    setLayout(Layout.TocContentMeta);
    result.setVisible(true);
    return result;
  }

  private void gotoBook(Book book) {
    mainWindow.setTitle(book.getTitle());
    navigator.gotoBook(book, this);
  }

  private static String getText(String text) {
    return text;
  }

  private static JFileChooser createFileChooser(File startDir) {
    if (startDir == null) {
      startDir = new File(System.getProperty("user.home"));
      if (!startDir.exists()) {
        startDir = null;
      }
    }
    JFileChooser fileChooser = new JFileChooser(startDir);
    fileChooser.setAcceptAllFileFilterUsed(true);
    fileChooser.setFileFilter(new FileNameExtensionFilter("EPub files", "epub"));

    return fileChooser;
  }

  private JMenuBar createMenuBar() {
    final JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu = new JMenu(getText("File"));
    menuBar.add(fileMenu);
    JMenuItem openFileMenuItem = new JMenuItem(getText("Open"));
    openFileMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    openFileMenuItem.addActionListener(
        new ActionListener() {

          private File previousDir;

          public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = createFileChooser(previousDir);
            int returnVal = fileChooser.showOpenDialog(mainWindow);
            if (returnVal != JFileChooser.APPROVE_OPTION) {
              return;
            }
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile == null) {
              return;
            }
            if (!selectedFile.isDirectory()) {
              previousDir = selectedFile.getParentFile();
            }
            try (FileInputStream inputStream = new FileInputStream(selectedFile)) {
              Book book = (new EpubReader()).readEpub(inputStream);
              gotoBook(book);
            } catch (IOException e1) {
              log.log(System.Logger.Level.ERROR, e1.getMessage(), e1);
            }
          }
        });
    fileMenu.add(openFileMenuItem);

    JMenuItem saveFileMenuItem = new JMenuItem(getText("Save as ..."));
    saveFileMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    saveFileMenuItem.addActionListener(
        new ActionListener() {

          private File previousDir;

          public void actionPerformed(ActionEvent e) {
            if (navigator.getBook() == null) {
              return;
            }
            JFileChooser fileChooser = createFileChooser(previousDir);
            int returnVal = fileChooser.showOpenDialog(mainWindow);
            if (returnVal != JFileChooser.APPROVE_OPTION) {
              return;
            }
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile == null) {
              return;
            }
            if (!selectedFile.isDirectory()) {
              previousDir = selectedFile.getParentFile();
            }
            try (FileOutputStream outputStream = new FileOutputStream(selectedFile)) {
              (new EpubWriter()).write(navigator.getBook(), outputStream);
            } catch (IOException e1) {
              log.log(System.Logger.Level.ERROR, e1.getMessage(), e1);
            }
          }
        });
    fileMenu.add(saveFileMenuItem);

    JMenuItem reloadMenuItem = new JMenuItem(getText("Reload"));
    reloadMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
    reloadMenuItem.addActionListener(e -> gotoBook(navigator.getBook()));
    fileMenu.add(reloadMenuItem);

    JMenuItem exitMenuItem = new JMenuItem(getText("Exit"));
    exitMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
    exitMenuItem.addActionListener(e -> System.exit(0));
    fileMenu.add(exitMenuItem);

    JMenu viewMenu = new JMenu(getText("View"));
    menuBar.add(viewMenu);

    JMenuItem viewTocContentMenuItem =
        new JMenuItem(getText("TOCContent"), ViewerUtil.createImageIcon("layout-toc-content"));
    viewTocContentMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK));
    viewTocContentMenuItem.addActionListener(e -> setLayout(Layout.TocContent));
    viewMenu.add(viewTocContentMenuItem);

    JMenuItem viewContentMenuItem =
        new JMenuItem(getText("Content"), ViewerUtil.createImageIcon("layout-content"));
    viewContentMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
    viewContentMenuItem.addActionListener(e -> setLayout(Layout.Content));
    viewMenu.add(viewContentMenuItem);

    JMenuItem viewTocContentMetaMenuItem =
        new JMenuItem(
            getText("TocContentMeta"), ViewerUtil.createImageIcon("layout-toc-content-meta"));
    viewTocContentMetaMenuItem.setAccelerator(
        KeyStroke.getKeyStroke(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK));
    viewTocContentMetaMenuItem.addActionListener(e -> setLayout(Layout.TocContentMeta));
    viewMenu.add(viewTocContentMetaMenuItem);

    JMenu helpMenu = new JMenu(getText("Help"));
    menuBar.add(helpMenu);
    JMenuItem aboutMenuItem = new JMenuItem(getText("About"));
    aboutMenuItem.addActionListener(e -> new AboutDialog(Viewer.this.mainWindow));
    helpMenu.add(aboutMenuItem);

    return menuBar;
  }

  private enum Layout {
    TocContentMeta,
    TocContent,
    Content
  }

  private void setLayout(Layout layout) {
    switch (layout) {
      case Content -> {
        mainSplitPane.setDividerLocation(0.0d);
        rightSplitPane.setDividerLocation(1.0d);
      }
      case TocContent -> {
        mainSplitPane.setDividerLocation(0.2d);
        rightSplitPane.setDividerLocation(1.0d);
      }
      case TocContentMeta -> {
        mainSplitPane.setDividerLocation(0.2d);
        rightSplitPane.setDividerLocation(0.6d);
      }
    }
  }

  private static InputStream getBookInputStream(String[] args) {
    String bookFile = null;
    if (args.length > 0) {
      bookFile = args[0];
    }
    InputStream result = null;
    if (!StringUtils.isBlank(bookFile)) {
      try {
        result = new FileInputStream(bookFile);
      } catch (Exception e) {
        log.log(System.Logger.Level.ERROR, "Unable to open " + bookFile, e);
      }
    }
    if (result == null) {
      result = Viewer.class.getResourceAsStream("/viewer/epub4jviewer-help.epub");
    }
    return result;
  }

  static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      log.log(System.Logger.Level.ERROR, "Unable to set native look and feel", e);
    }

    final InputStream bookStream = getBookInputStream(args);
    //		final Book book = readBook(args);

    // Schedule a job for the event dispatch thread:
    // creating and showing this application's GUI.
    SwingUtilities.invokeLater(() -> new Viewer(bookStream));
  }
}
