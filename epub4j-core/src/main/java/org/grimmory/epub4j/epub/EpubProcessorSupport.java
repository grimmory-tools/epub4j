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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.grimmory.epub4j.Constants;
import org.kxml2.io.KXmlSerializer;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlSerializer;

/**
 * Various low-level support methods for reading/writing epubs.
 *
 * @author paul.siegmann
 */
public class EpubProcessorSupport {

  private static final System.Logger log = System.getLogger(EpubProcessorSupport.class.getName());

  protected static DocumentBuilderFactory documentBuilderFactory;

  static {
    init();
  }

  static class EntityResolverImpl implements EntityResolver {

    private String previousLocation;

    @Override
    public InputSource resolveEntity(String publicId, String systemId)
        throws SAXException, IOException {
      String resourcePath;
      if (systemId.startsWith("http:")) {
        URL url = URI.create(systemId).toURL();
        resourcePath = "dtd/" + url.getHost() + url.getPath();
        previousLocation = resourcePath.substring(0, resourcePath.lastIndexOf('/'));
      } else {
        resourcePath = previousLocation + systemId.substring(systemId.lastIndexOf('/'));
      }

      if (this.getClass().getClassLoader().getResource(resourcePath) == null) {
        throw new RuntimeException(
            "remote resource is not cached : [" + systemId + "] cannot continue");
      }

      InputStream in =
          EpubProcessorSupport.class.getClassLoader().getResourceAsStream(resourcePath);
      return new InputSource(in);
    }
  }

  private static void init() {
    EpubProcessorSupport.documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    documentBuilderFactory.setValidating(false);
    // Each feature is set independently  -  some parser implementations don't support all of them
    trySetFeature(documentBuilderFactory, javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
    // DOCTYPEs are legitimate in XHTML/EPUB content  -  don't disallow them
    trySetFeature(
        documentBuilderFactory, "http://xml.org/sax/features/external-general-entities", false);
    trySetFeature(
        documentBuilderFactory, "http://xml.org/sax/features/external-parameter-entities", false);
    trySetFeature(
        documentBuilderFactory,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        false);
    try {
      documentBuilderFactory.setXIncludeAware(false);
    } catch (UnsupportedOperationException ignored) {
      // XInclude not supported by this parser
    }
    documentBuilderFactory.setExpandEntityReferences(false);
    // Some parser implementations (e.g. Xerces) don't support these attributes
    trySetAttribute(documentBuilderFactory, javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
    trySetAttribute(documentBuilderFactory, javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
  }

  private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
    try {
      factory.setFeature(feature, value);
    } catch (javax.xml.parsers.ParserConfigurationException ignored) {
      // Not all XML parser implementations support this feature
    }
  }

  private static void trySetAttribute(DocumentBuilderFactory factory, String name, String value) {
    try {
      factory.setAttribute(name, value);
    } catch (IllegalArgumentException ignored) {
      // Not all XML parser implementations support this attribute (e.g. Xerces)
    }
  }

  public static XmlSerializer createXmlSerializer(OutputStream out)
      throws UnsupportedEncodingException {
    return createXmlSerializer(new OutputStreamWriter(out, Constants.CHARACTER_ENCODING));
  }

  public static XmlSerializer createXmlSerializer(Writer out) {
    XmlSerializer result = null;
    try {
      /*
       * Disable XmlPullParserFactory here before it doesn't work when
       * building native image using GraalVM
       */
      // XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
      // factory.setValidating(true);
      // result = factory.newSerializer();

      result = new KXmlSerializer();
      result.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
      result.setOutput(out);
    } catch (Exception e) {
      log.log(
          System.Logger.Level.ERROR,
          "When creating XmlSerializer: " + e.getClass().getName() + ": " + e.getMessage());
    }
    return result;
  }

  /**
   * Gets an EntityResolver that loads dtd's and such from the epub4j classpath. In order to enable
   * the loading of relative urls the given EntityResolver contains the previousLocation. Because of
   * a new EntityResolver is created every time this method is called. Fortunately the
   * EntityResolver created uses up very little memory per instance.
   *
   * @return an EntityResolver that loads dtd's and such from the epub4j classpath.
   */
  public static EntityResolver getEntityResolver() {
    return new EntityResolverImpl();
  }

  public static DocumentBuilderFactory getDocumentBuilderFactory() {
    return documentBuilderFactory;
  }

  /**
   * Creates a DocumentBuilder that looks up dtd's and schema's from epub4j's classpath.
   *
   * @return a DocumentBuilder that looks up dtd's and schema's from epub4j's classpath.
   */
  public static DocumentBuilder createDocumentBuilder() {
    DocumentBuilder result = null;
    try {
      result = documentBuilderFactory.newDocumentBuilder();
      result.setEntityResolver(getEntityResolver());
    } catch (ParserConfigurationException e) {
      log.log(System.Logger.Level.ERROR, e.getMessage());
    }
    return result;
  }
}
