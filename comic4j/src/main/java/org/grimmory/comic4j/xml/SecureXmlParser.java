/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Provides XXE-hardened XML parsing facilities. All XML parsing in comic4j must go through this
 * class.
 */
public final class SecureXmlParser {

  private SecureXmlParser() {}

  /** Creates a DocumentBuilder with comprehensive XXE protection. */
  public static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    // Secure processing mode
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

    // Disable DTDs entirely
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

    // Disable external entities
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

    // Disable external DTD/Schema loading
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

    // Disable XInclude processing
    factory.setXIncludeAware(false);

    // Disable entity reference expansion
    factory.setExpandEntityReferences(false);

    // Namespace-unaware: ComicInfo.xml does not use namespaces
    factory.setNamespaceAware(false);

    return factory.newDocumentBuilder();
  }
}
