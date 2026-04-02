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
package org.grimmory.epub4j.utilities;

import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

/**
 * Splits up a xhtml document into pieces that are all valid xhtml documents.
 *
 * @author paul
 */
public class HtmlSplitter {

  private final XMLEventFactory xmlEventFactory = XMLEventFactory.newInstance();
  private final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
  private List<XMLEvent> headerElements = new ArrayList<>();
  private List<XMLEvent> footerElements = new ArrayList<>();
  private int footerCloseTagLength;
  private final List<XMLEvent> elementStack = new ArrayList<>();
  private StringWriter currentDoc = new StringWriter();
  private List<XMLEvent> currentXmlEvents = new ArrayList<>();
  private XMLEventWriter out;
  private int maxLength = 300000; // 300K, the max length of a chapter of an epub document
  private final List<List<XMLEvent>> result = new ArrayList<>();

  public List<List<XMLEvent>> splitHtml(Reader reader, int maxLength) throws XMLStreamException {
    XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(reader);
    return splitHtml(xmlEventReader, maxLength);
  }

  private static int calculateTotalTagStringLength(List<XMLEvent> xmlEvents) {
    int result = 0;
    for (XMLEvent xmlEvent : xmlEvents) {
      result += xmlEvent.toString().length();
    }
    return result;
  }

  public List<List<XMLEvent>> splitHtml(XMLEventReader reader, int maxLength)
      throws XMLStreamException {
    this.headerElements = getHeaderElements(reader);
    this.footerElements = getFooterElements();
    footerCloseTagLength = calculateTotalTagStringLength(footerElements);
    this.maxLength = (int) ((float) maxLength * 0.9);
    currentXmlEvents = new ArrayList<>();
    currentXmlEvents.addAll(headerElements);
    currentXmlEvents.addAll(elementStack);
    out = xmlOutputFactory.createXMLEventWriter(currentDoc);
    for (XMLEvent headerXmlEvent : headerElements) {
      out.add(headerXmlEvent);
    }
    XMLEvent xmlEvent = reader.nextEvent();
    while (!isBodyEndElement(xmlEvent)) {
      processXmlEvent(xmlEvent);
      xmlEvent = reader.nextEvent();
    }
    result.add(currentXmlEvents);
    return result;
  }

  private void closeCurrentDocument() throws XMLStreamException {
    closeAllTags(currentXmlEvents);
    currentXmlEvents.addAll(footerElements);
    result.add(currentXmlEvents);
  }

  private void startNewDocument() throws XMLStreamException {
    currentDoc = new StringWriter();
    out = xmlOutputFactory.createXMLEventWriter(currentDoc);
    for (XMLEvent headerXmlEvent : headerElements) {
      out.add(headerXmlEvent);
    }
    for (XMLEvent stackXmlEvent : elementStack) {
      out.add(stackXmlEvent);
    }

    currentXmlEvents = new ArrayList<>();
    currentXmlEvents.addAll(headerElements);
    currentXmlEvents.addAll(elementStack);
  }

  private void processXmlEvent(XMLEvent xmlEvent) throws XMLStreamException {
    out.flush();
    String currentSerializerDoc = currentDoc.toString();
    if ((currentSerializerDoc.length() + xmlEvent.toString().length() + footerCloseTagLength)
        >= maxLength) {
      closeCurrentDocument();
      startNewDocument();
    }
    updateStack(xmlEvent);
    out.add(xmlEvent);
    currentXmlEvents.add(xmlEvent);
  }

  private void closeAllTags(List<XMLEvent> xmlEvents) throws XMLStreamException {
    for (int i = elementStack.size() - 1; i >= 0; i--) {
      XMLEvent xmlEvent = elementStack.get(i);
      XMLEvent xmlEndElementEvent =
          xmlEventFactory.createEndElement(xmlEvent.asStartElement().getName(), null);
      xmlEvents.add(xmlEndElementEvent);
    }
  }

  private void updateStack(XMLEvent xmlEvent) {
    if (xmlEvent.isStartElement()) {
      elementStack.add(xmlEvent);
    } else if (xmlEvent.isEndElement()) {
      XMLEvent lastEvent = elementStack.getLast();
      if (lastEvent.isStartElement()
          && xmlEvent.asEndElement().getName().equals(lastEvent.asStartElement().getName())) {
        elementStack.removeLast();
      }
    }
  }

  private static List<XMLEvent> getHeaderElements(XMLEventReader reader) throws XMLStreamException {
    List<XMLEvent> result = new ArrayList<>();
    XMLEvent event = reader.nextEvent();
    while (event != null && (!isBodyStartElement(event))) {
      result.add(event);
      event = reader.nextEvent();
    }

    // add the body start tag to the result
    if (event != null) {
      result.add(event);
    }
    return result;
  }

  private List<XMLEvent> getFooterElements() throws XMLStreamException {
    List<XMLEvent> result = new ArrayList<>();
    result.add(xmlEventFactory.createEndElement("", null, "body"));
    result.add(xmlEventFactory.createEndElement("", null, "html"));
    result.add(xmlEventFactory.createEndDocument());
    return result;
  }

  private static boolean isBodyStartElement(XMLEvent xmlEvent) {
    return xmlEvent.isStartElement()
        && "body".equals(xmlEvent.asStartElement().getName().getLocalPart());
  }

  private static boolean isBodyEndElement(XMLEvent xmlEvent) {
    return xmlEvent.isEndElement()
        && "body".equals(xmlEvent.asEndElement().getName().getLocalPart());
  }
}
