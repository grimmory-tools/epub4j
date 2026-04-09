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

import java.awt.Cursor;
import java.io.*;
import javax.accessibility.AccessibleContext;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLEditorKit.Parser;
import javax.swing.text.html.StyleSheet;

/**
 * Wraps a HTMLEditorKit so we can make getParser() public.
 *
 * @author paul.siegmann
 */
class MyHtmlEditorKit extends HTMLEditorKit {
  @Serial private static final long serialVersionUID = 1L;
  private final HTMLEditorKit htmlEditorKit;

  public MyHtmlEditorKit(HTMLEditorKit htmlEditorKit) {
    this.htmlEditorKit = htmlEditorKit;
  }

  public Parser getParser() {
    return super.getParser();
  }

  public int hashCode() {
    return htmlEditorKit.hashCode();
  }

  public Element getCharacterAttributeRun() {
    return htmlEditorKit.getCharacterAttributeRun();
  }

  public Caret createCaret() {
    return htmlEditorKit.createCaret();
  }

  public void read(InputStream in, Document doc, int pos) throws IOException, BadLocationException {
    htmlEditorKit.read(in, doc, pos);
  }

  public boolean equals(Object obj) {
    return htmlEditorKit.equals(obj);
  }

  public void write(OutputStream out, Document doc, int pos, int len)
      throws IOException, BadLocationException {
    htmlEditorKit.write(out, doc, pos, len);
  }

  public String getContentType() {
    return htmlEditorKit.getContentType();
  }

  public ViewFactory getViewFactory() {
    return htmlEditorKit.getViewFactory();
  }

  public Document createDefaultDocument() {
    return htmlEditorKit.createDefaultDocument();
  }

  public void read(Reader in, Document doc, int pos) throws IOException, BadLocationException {
    htmlEditorKit.read(in, doc, pos);
  }

  public void insertHTML(
      HTMLDocument doc, int offset, String html, int popDepth, int pushDepth, Tag insertTag)
      throws BadLocationException, IOException {
    htmlEditorKit.insertHTML(doc, offset, html, popDepth, pushDepth, insertTag);
  }

  public String toString() {
    return htmlEditorKit.toString();
  }

  public void write(Writer out, Document doc, int pos, int len)
      throws IOException, BadLocationException {
    htmlEditorKit.write(out, doc, pos, len);
  }

  public void install(JEditorPane c) {
    htmlEditorKit.install(c);
  }

  public void deinstall(JEditorPane c) {
    htmlEditorKit.deinstall(c);
  }

  public void setStyleSheet(StyleSheet s) {
    htmlEditorKit.setStyleSheet(s);
  }

  public StyleSheet getStyleSheet() {
    return htmlEditorKit.getStyleSheet();
  }

  public Action[] getActions() {
    return htmlEditorKit.getActions();
  }

  public MutableAttributeSet getInputAttributes() {
    return htmlEditorKit.getInputAttributes();
  }

  public void setDefaultCursor(Cursor cursor) {
    htmlEditorKit.setDefaultCursor(cursor);
  }

  public Cursor getDefaultCursor() {
    return htmlEditorKit.getDefaultCursor();
  }

  public void setLinkCursor(Cursor cursor) {
    htmlEditorKit.setLinkCursor(cursor);
  }

  public Cursor getLinkCursor() {
    return htmlEditorKit.getLinkCursor();
  }

  public boolean isAutoFormSubmission() {
    return htmlEditorKit.isAutoFormSubmission();
  }

  public void setAutoFormSubmission(boolean isAuto) {
    htmlEditorKit.setAutoFormSubmission(isAuto);
  }

  public MyHtmlEditorKit clone() {
    super.clone();
    return (MyHtmlEditorKit) htmlEditorKit.clone();
  }

  public AccessibleContext getAccessibleContext() {
    return htmlEditorKit.getAccessibleContext();
  }
}
