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
package org.grimmory.epub4j.util;

import java.io.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.EpubProcessorSupport;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Various resource utility methods
 *
 * @author paul
 */
public class ResourceUtil {

  public static Resource createResource(File file) throws IOException {
    if (file == null) {
      return null;
    }
    MediaType mediaType = MediaTypes.determineMediaType(file.getName());
    byte[] data;
    try (FileInputStream inputStream = new FileInputStream(file)) {
      data = IOUtil.toByteArray(inputStream);
    }
    return new Resource(data, mediaType);
  }

  /**
   * Creates a resource with as contents a html page with the given title.
   *
   * @param title
   * @param href
   * @return a resource with as contents a html page with the given title.
   */
  public static Resource createResource(String title, String href) {
    String content =
        "<html><head><title>" + title + "</title></head><body><h1>" + title + "</h1></body></html>";
    return new Resource(
        null, content.getBytes(), href, MediaTypes.XHTML, Constants.CHARACTER_ENCODING);
  }

  /**
   * Creates a resource from an entry name and input stream.
   *
   * @param entryName the archive entry name (used as href and to determine media type)
   * @param inputStream the stream containing the entry data
   * @return a resource created from the entry
   * @throws IOException if reading the stream fails
   */
  public static Resource createResource(String entryName, InputStream inputStream)
      throws IOException {
    return new Resource(inputStream, entryName);
  }

  /**
   * Converts a given string from given input character encoding to the requested output character
   * encoding.
   *
   * @param inputEncoding
   * @param outputEncoding
   * @param input
   * @return the string from given input character encoding converted to the requested output
   *     character encoding.
   * @throws UnsupportedEncodingException
   */
  public static byte[] recode(String inputEncoding, String outputEncoding, byte[] input)
      throws UnsupportedEncodingException {
    return new String(input, inputEncoding).getBytes(outputEncoding);
  }

  /** Gets the contents of the Resource as an InputSource in a null-safe manner. */
  public static InputSource getInputSource(Resource resource) throws IOException {
    if (resource == null) {
      return null;
    }
    Reader reader = resource.getReader();
    if (reader == null) {
      return null;
    }
    return new InputSource(reader);
  }

  /** Reads parses the xml therein and returns the result as a Document */
  public static Document getAsDocument(Resource resource)
      throws SAXException, IOException, ParserConfigurationException {
    return getAsDocument(resource, EpubProcessorSupport.createDocumentBuilder());
  }

  /**
   * Reads the given resources inputstream, parses the xml therein and returns the result as a
   * Document
   *
   * @param resource
   * @param documentBuilder
   * @return the document created from the given resource
   * @throws UnsupportedEncodingException
   * @throws SAXException
   * @throws IOException
   * @throws ParserConfigurationException
   */
  public static Document getAsDocument(Resource resource, DocumentBuilder documentBuilder)
      throws UnsupportedEncodingException, SAXException, IOException, ParserConfigurationException {
    InputSource inputSource = getInputSource(resource);
    if (inputSource == null) {
      return null;
    }
    return documentBuilder.parse(inputSource);
  }
}
