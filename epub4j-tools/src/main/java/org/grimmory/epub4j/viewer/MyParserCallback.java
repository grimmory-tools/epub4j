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

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLEditorKit.ParserCallback;

class MyParserCallback extends ParserCallback {
  private final ParserCallback parserCallback;
  private List<String> stylesheetHrefs = new ArrayList<>();

  public MyParserCallback(ParserCallback parserCallback) {
    this.parserCallback = parserCallback;
  }

  public List<String> getStylesheetHrefs() {
    return stylesheetHrefs;
  }

  public void setStylesheetHrefs(List<String> stylesheetHrefs) {
    this.stylesheetHrefs = stylesheetHrefs;
  }

  private static boolean isStylesheetLink(Tag tag, MutableAttributeSet attributes) {
    return ((tag == Tag.LINK)
        && (attributes.containsAttribute(HTML.Attribute.REL, "stylesheet"))
        && (attributes.containsAttribute(HTML.Attribute.TYPE, "text/css")));
  }

  private void handleStylesheet(Tag tag, MutableAttributeSet attributes) {
    if (isStylesheetLink(tag, attributes)) {
      stylesheetHrefs.add(attributes.getAttribute(HTML.Attribute.HREF).toString());
    }
  }

  public int hashCode() {
    return parserCallback.hashCode();
  }

  public boolean equals(Object obj) {
    return parserCallback.equals(obj);
  }

  public String toString() {
    return parserCallback.toString();
  }

  public void flush() throws BadLocationException {
    parserCallback.flush();
  }

  public void handleText(char[] data, int pos) {
    parserCallback.handleText(data, pos);
  }

  public void handleComment(char[] data, int pos) {
    parserCallback.handleComment(data, pos);
  }

  public void handleStartTag(Tag t, MutableAttributeSet a, int pos) {
    handleStylesheet(t, a);
    parserCallback.handleStartTag(t, a, pos);
  }

  public void handleEndTag(Tag t, int pos) {
    parserCallback.handleEndTag(t, pos);
  }

  public void handleSimpleTag(Tag t, MutableAttributeSet a, int pos) {
    handleStylesheet(t, a);
    parserCallback.handleSimpleTag(t, a, pos);
  }

  public void handleError(String errorMsg, int pos) {
    parserCallback.handleError(errorMsg, pos);
  }

  public void handleEndOfLineString(String eol) {
    parserCallback.handleEndOfLineString(eol);
  }
}
