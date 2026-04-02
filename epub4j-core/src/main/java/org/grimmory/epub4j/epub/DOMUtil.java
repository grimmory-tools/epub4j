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
package org.grimmory.epub4j.epub;

import java.util.ArrayList;
import java.util.List;
import org.grimmory.epub4j.util.StringUtil;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Utility methods for working with the DOM.
 *
 * @author paul
 */
// package
class DOMUtil {

  /**
   * First tries to get the attribute value by doing an getAttributeNS on the element, if that gets
   * an empty element it does a getAttribute without namespace.
   *
   * @param element
   * @param namespace
   * @param attribute
   * @return
   */
  public static String getAttribute(Element element, String namespace, String attribute) {
    String result = element.getAttributeNS(namespace, attribute);
    if (StringUtil.isEmpty(result)) {
      result = element.getAttribute(attribute);
    }
    return result;
  }

  /**
   * Gets all descendant elements of the given parentElement with the given namespace and tagname
   * and returns their text child as a list of String.
   *
   * @param parentElement
   * @param namespace
   * @param tagname
   * @return
   */
  public static List<String> getElementsTextChild(
      Element parentElement, String namespace, String tagname) {
    NodeList elements = parentElement.getElementsByTagNameNS(namespace, tagname);
    int elementCount = elements.getLength();
    List<String> result = new ArrayList<>(elementCount);
    for (int i = 0; i < elementCount; i++) {
      result.add(getTextChildrenContent((Element) elements.item(i)));
    }
    return result;
  }

  /**
   * Finds in the current document the first element with the given namespace and elementName and
   * with the given findAttributeName and findAttributeValue. It then returns the value of the given
   * resultAttributeName.
   *
   * @param document
   * @param namespace
   * @param elementName
   * @param findAttributeName
   * @param findAttributeValue
   * @param resultAttributeName
   * @return
   */
  public static String getFindAttributeValue(
      Document document,
      String namespace,
      String elementName,
      String findAttributeName,
      String findAttributeValue,
      String resultAttributeName) {
    NodeList metaTags = document.getElementsByTagNameNS(namespace, elementName);
    int tagCount = metaTags.getLength();
    for (int i = 0; i < tagCount; i++) {
      Element metaElement = (Element) metaTags.item(i);
      if (findAttributeValue.equalsIgnoreCase(metaElement.getAttribute(findAttributeName))
          && StringUtil.isNotBlank(metaElement.getAttribute(resultAttributeName))) {
        return metaElement.getAttribute(resultAttributeName);
      }
    }
    return null;
  }

  /**
   * Gets the first element that is a child of the parentElement and has the given namespace and
   * tagName
   *
   * @param parentElement
   * @param namespace
   * @param tagName
   * @return
   */
  public static Element getFirstElementByTagNameNS(
      Element parentElement, String namespace, String tagName) {
    NodeList nodes = parentElement.getElementsByTagNameNS(namespace, tagName);
    if (nodes.getLength() == 0) {
      return null;
    }
    return (Element) nodes.item(0);
  }

  /**
   * The contents of all Text nodes that are children of the given parentElement. The result is
   * trim()-ed.
   *
   * <p>The reason for this more complicated procedure instead of just returning the data of the
   * firstChild is that when the text is Chinese characters then on Android each Characater is
   * represented in the DOM as an individual Text node.
   *
   * @param parentElement
   * @return
   */
  public static String getTextChildrenContent(Element parentElement) {
    if (parentElement == null) {
      return null;
    }
    StringBuilder result = new StringBuilder();
    NodeList childNodes = parentElement.getChildNodes();
    int childCount = childNodes.getLength();
    for (int i = 0; i < childCount; i++) {
      Node node = childNodes.item(i);
      if ((node == null) || (node.getNodeType() != Node.TEXT_NODE)) {
        continue;
      }
      result.append(((CharacterData) node).getData());
    }
    return result.toString().trim();
  }
}
