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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.QName;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Date;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.util.StringUtil;
import org.xmlpull.v1.XmlSerializer;

public class PackageDocumentMetadataWriter extends PackageDocumentBase {

  /**
   * Writes the book's metadata in EPUB3 format.
   *
   * <p>EPUB3 differences from EPUB2:
   *
   * <ul>
   *   <li>No opf:scheme on dc:identifier (OPF-049)
   *   <li>No opf:role, opf:file-as on dc:creator/dc:contributor (OPF-049)
   *   <li>No opf:event on dc:date (OPF-049)
   *   <li>dcterms:modified expressed as {@code <meta property="dcterms:modified">} (OPF-053)
   *   <li>Cover image identified via properties="cover-image" on manifest item, not meta
   *       name="cover"
   * </ul>
   */
  public static void writeMetaData(Book book, XmlSerializer serializer)
      throws IllegalArgumentException, IllegalStateException, IOException {
    serializer.startTag(NAMESPACE_OPF, OPFTags.metadata);
    // Only declare DC namespace prefix on metadata element
    serializer.setPrefix(PREFIX_DUBLIN_CORE, NAMESPACE_DUBLIN_CORE);

    writeIdentifiers(book.getMetadata().getIdentifiers(), serializer);
    writeSimpleMetdataElements(DCTags.title, book.getMetadata().getTitles(), serializer);
    writeSimpleMetdataElements(DCTags.subject, book.getMetadata().getSubjects(), serializer);
    writeSimpleMetdataElements(
        DCTags.description, book.getMetadata().getDescriptions(), serializer);
    writeSimpleMetdataElements(DCTags.publisher, book.getMetadata().getPublishers(), serializer);
    writeSimpleMetdataElements(DCTags.type, book.getMetadata().getTypes(), serializer);
    writeSimpleMetdataElements(DCTags.rights, book.getMetadata().getRights(), serializer);

    // EPUB3: write authors without opf:role and opf:file-as attributes
    for (Author author : book.getMetadata().getAuthors()) {
      serializer.startTag(NAMESPACE_DUBLIN_CORE, DCTags.creator);
      serializer.text(buildDisplayName(author));
      serializer.endTag(NAMESPACE_DUBLIN_CORE, DCTags.creator);
    }

    // EPUB3: write contributors without opf:role and opf:file-as attributes
    for (Author author : book.getMetadata().getContributors()) {
      serializer.startTag(NAMESPACE_DUBLIN_CORE, DCTags.contributor);
      serializer.text(buildDisplayName(author));
      serializer.endTag(NAMESPACE_DUBLIN_CORE, DCTags.contributor);
    }

    // EPUB3: write dates without opf:event attribute
    for (Date date : book.getMetadata().getDates()) {
      serializer.startTag(NAMESPACE_DUBLIN_CORE, DCTags.date);
      serializer.text(date.getValue());
      serializer.endTag(NAMESPACE_DUBLIN_CORE, DCTags.date);
    }

    // OPF_053: dcterms:modified as <meta property="dcterms:modified"> for EPUB3
    writeDctermsModified(serializer);

    // write language
    if (StringUtil.isNotBlank(book.getMetadata().getLanguage())) {
      serializer.startTag(NAMESPACE_DUBLIN_CORE, DCTags.language);
      serializer.text(book.getMetadata().getLanguage());
      serializer.endTag(NAMESPACE_DUBLIN_CORE, DCTags.language);
    }

    // Write subtitle as dc:title with EPUB3 title-type refinement
    if (StringUtil.isNotBlank(book.getMetadata().getSubtitle())) {
      serializer.startTag(NAMESPACE_DUBLIN_CORE, DCTags.title);
      serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, "id", "subtitle");
      serializer.text(book.getMetadata().getSubtitle());
      serializer.endTag(NAMESPACE_DUBLIN_CORE, DCTags.title);

      serializer.startTag(NAMESPACE_OPF, OPFTags.meta);
      serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.refines, "#subtitle");
      serializer.attribute(
          EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.property, MetaProperties.TITLE_TYPE);
      serializer.text("subtitle");
      serializer.endTag(NAMESPACE_OPF, OPFTags.meta);
    }

    // Write series as belongs-to-collection + group-position (EPUB3 standard)
    if (StringUtil.isNotBlank(book.getMetadata().getSeriesName())) {
      serializer.startTag(NAMESPACE_OPF, OPFTags.meta);
      serializer.attribute(
          EpubWriter.EMPTY_NAMESPACE_PREFIX,
          OPFAttributes.property,
          MetaProperties.BELONGS_TO_COLLECTION);
      serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, "id", "series");
      serializer.text(book.getMetadata().getSeriesName());
      serializer.endTag(NAMESPACE_OPF, OPFTags.meta);

      serializer.startTag(NAMESPACE_OPF, OPFTags.meta);
      serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.refines, "#series");
      serializer.attribute(
          EpubWriter.EMPTY_NAMESPACE_PREFIX,
          OPFAttributes.property,
          MetaProperties.COLLECTION_TYPE);
      serializer.text("series");
      serializer.endTag(NAMESPACE_OPF, OPFTags.meta);

      if (book.getMetadata().getSeriesNumber() != null) {
        serializer.startTag(NAMESPACE_OPF, OPFTags.meta);
        serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.refines, "#series");
        serializer.attribute(
            EpubWriter.EMPTY_NAMESPACE_PREFIX,
            OPFAttributes.property,
            MetaProperties.GROUP_POSITION);
        float num = book.getMetadata().getSeriesNumber();
        serializer.text(num == Math.floor(num) ? String.valueOf((int) num) : String.valueOf(num));
        serializer.endTag(NAMESPACE_OPF, OPFTags.meta);
      }
    }

    // Write page count
    if (book.getMetadata().getPageCount() != null) {
      serializer.startTag(NAMESPACE_OPF, OPFTags.meta);
      serializer.attribute(
          EpubWriter.EMPTY_NAMESPACE_PREFIX,
          OPFAttributes.property,
          MetaProperties.SCHEMA_PAGE_COUNT);
      serializer.text(String.valueOf(book.getMetadata().getPageCount()));
      serializer.endTag(NAMESPACE_OPF, OPFTags.meta);
    }

    // write other properties using EPUB3 <meta property="..."> format
    // Skip properties that are now written structurally above
    Set<String> structuredProperties =
        Set.of(
            MetaProperties.BELONGS_TO_COLLECTION, MetaProperties.GROUP_POSITION,
            MetaProperties.COLLECTION_TYPE, MetaProperties.SCHEMA_PAGE_COUNT,
            MetaProperties.MEDIA_PAGE_COUNT, MetaProperties.TITLE_TYPE);

    if (book.getMetadata().getOtherProperties() != null) {
      for (Map.Entry<QName, String> mapEntry : book.getMetadata().getOtherProperties().entrySet()) {
        if (structuredProperties.contains(mapEntry.getKey().getLocalPart())) {
          continue;
        }
        serializer.startTag(NAMESPACE_OPF, OPFTags.meta);
        serializer.attribute(
            EpubWriter.EMPTY_NAMESPACE_PREFIX,
            OPFAttributes.property,
            mapEntry.getKey().getLocalPart());
        serializer.text(mapEntry.getValue());
        serializer.endTag(NAMESPACE_OPF, OPFTags.meta);
      }
    }

    // EPUB3: Cover image is identified via properties="cover-image" on the
    // manifest <item>, not via <meta name="cover" content="..."/>.
    // We omit the legacy EPUB2 cover meta entirely.

    serializer.endTag(NAMESPACE_OPF, OPFTags.metadata);
  }

  /**
   * Writes the dcterms:modified meta element required for EPUB3 (OPF_053).
   *
   * <p>In EPUB3, this MUST be expressed as: {@code <meta
   * property="dcterms:modified">2024-01-01T00:00:00Z</meta>} rather than {@code
   * <dcterms:modified>...</dcterms:modified>}.
   */
  private static void writeDctermsModified(XmlSerializer serializer) throws IOException {
    serializer.startTag(NAMESPACE_OPF, OPFTags.meta);
    serializer.attribute(
        EpubWriter.EMPTY_NAMESPACE_PREFIX, OPFAttributes.property, "dcterms:modified");
    String modified = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
    // Truncate to seconds as required by EPUB3 spec
    if (modified.endsWith("Z")) {
      serializer.text(modified.substring(0, 19) + "Z");
    } else {
      serializer.text(modified.substring(0, 19) + "+00:00");
    }
    serializer.endTag(NAMESPACE_OPF, OPFTags.meta);
  }

  private static String buildDisplayName(Author author) {
    String first = author.getFirstname();
    String last = author.getLastname();
    boolean hasFirst = first != null && !first.trim().isEmpty();
    boolean hasLast = last != null && !last.trim().isEmpty();
    if (hasFirst && hasLast) {
      return first + " " + last;
    } else if (hasFirst) {
      return first;
    } else if (hasLast) {
      return last;
    }
    return "";
  }

  private static void writeSimpleMetdataElements(
      String tagName, List<String> values, XmlSerializer serializer)
      throws IllegalArgumentException, IllegalStateException, IOException {
    for (String value : values) {
      if (StringUtil.isBlank(value)) {
        continue;
      }
      serializer.startTag(NAMESPACE_DUBLIN_CORE, tagName);
      serializer.text(value);
      serializer.endTag(NAMESPACE_DUBLIN_CORE, tagName);
    }
  }

  /**
   * Writes out the complete list of Identifiers to the package document.
   *
   * <p>EPUB3: dc:identifier does NOT use opf:scheme attribute. OPF_048/OPF_030: The primary
   * dc:identifier must have an id attribute matching the package unique-identifier attribute.
   */
  private static void writeIdentifiers(List<Identifier> identifiers, XmlSerializer serializer)
      throws IllegalArgumentException, IllegalStateException, IOException {
    Identifier bookIdIdentifier = Identifier.getBookIdIdentifier(identifiers);
    if (bookIdIdentifier == null) {
      return;
    }

    // OPF_048/OPF_030: The dc:identifier must have id="BookId" to match
    // the package unique-identifier attribute
    serializer.startTag(NAMESPACE_DUBLIN_CORE, DCTags.identifier);
    serializer.attribute(EpubWriter.EMPTY_NAMESPACE_PREFIX, DCAttributes.id, BOOK_ID_ID);
    // EPUB3: No opf:scheme attribute
    serializer.text(bookIdIdentifier.getValue());
    serializer.endTag(NAMESPACE_DUBLIN_CORE, DCTags.identifier);

    for (Identifier identifier : identifiers) {
      if (identifier == bookIdIdentifier) {
        continue;
      }
      serializer.startTag(NAMESPACE_DUBLIN_CORE, DCTags.identifier);
      // EPUB3: No opf:scheme attribute
      serializer.text(identifier.getValue());
      serializer.endTag(NAMESPACE_DUBLIN_CORE, DCTags.identifier);
    }
  }
}
