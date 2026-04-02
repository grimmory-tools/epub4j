/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.domain;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.namespace.QName;
import org.grimmory.epub4j.epub.MetadataNormalizer;

/**
 * EPUB implementation of {@link BookMetadata}. Wraps the EPUB {@link Metadata} class and provides
 * unified access to standard metadata fields.
 *
 * @param title the book's primary title
 * @param authors list of all authors/creators
 * @param series the series name (from belongs-to-collection or legacy meta elements)
 * @param seriesNumber the volume number within the series
 * @param isbn the ISBN identifier
 * @param language the language code
 * @param publishedDate the publication date
 * @param subjects list of subjects/genres
 * @param description the book description/abstract
 * @param publisher the publisher name
 * @param rawMetadata the underlying EPUB Metadata object
 * @param customFields additional custom metadata fields
 */
public record EpubBookMetadata(
    Optional<String> title,
    List<String> authors,
    Optional<String> series,
    Optional<Float> seriesNumber,
    Optional<String> isbn,
    Optional<String> language,
    Optional<LocalDate> publishedDate,
    List<String> subjects,
    Optional<String> description,
    Optional<String> publisher,
    Metadata rawMetadata,
    Map<String, String> customFields)
    implements BookMetadata {

  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
  private static final System.Logger log = System.getLogger(EpubBookMetadata.class.getName());

  public EpubBookMetadata {
    authors = List.copyOf(authors);
    subjects = List.copyOf(subjects);
    customFields = Map.copyOf(customFields);
  }

  @Override
  public BookFormat format() {
    return BookFormat.EPUB;
  }

  /**
   * Create BookMetadata from an EPUB Book.
   *
   * @param book the EPUB book
   * @return unified metadata
   */
  public static EpubBookMetadata from(Book book) {
    if (book == null) {
      return empty();
    }

    Metadata meta = book.getMetadata();
    if (meta == null) {
      return empty();
    }

    // Extract title
    Optional<String> title = Optional.ofNullable(meta.getFirstTitle()).filter(s -> !s.isBlank());

    // Extract authors
    List<String> authors =
        meta.getAuthors().stream()
            .map(
                author -> {
                  String first = author.getFirstname();
                  String last = author.getLastname();
                  if (first == null || first.isBlank()) return last;
                  if (last == null || last.isBlank()) return first;
                  return first + " " + last;
                })
            .filter(s -> s != null && !s.isBlank())
            .toList();

    // Extract series from belongs-to-collection or legacy meta elements
    Optional<String> series = extractSeries(meta);
    Optional<Float> seriesNumber = extractSeriesNumber(meta);

    // Extract ISBN
    Optional<String> isbn = extractIsbn(meta);

    // Extract language
    Optional<String> language =
        Optional.ofNullable(meta.getLanguage())
            .filter(s -> !s.isBlank())
            .map(MetadataNormalizer::normalizeLanguageCode);

    // Extract publication date
    Optional<LocalDate> publishedDate = extractPublishedDate(meta);

    // Extract subjects
    List<String> subjects =
        meta.getSubjects().stream().filter(s -> s != null && !s.isBlank()).toList();

    // Extract description
    Optional<String> description =
        meta.getDescriptions().stream().filter(s -> s != null && !s.isBlank()).findFirst();

    // Extract publisher
    Optional<String> publisher =
        meta.getPublishers().stream().filter(s -> s != null && !s.isBlank()).findFirst();

    // Extract custom fields
    Map<String, String> customFields = extractCustomFields(meta);

    return new EpubBookMetadata(
        title,
        authors,
        series,
        seriesNumber,
        isbn,
        language,
        publishedDate,
        subjects,
        description,
        publisher,
        meta,
        customFields);
  }

  /** Create an empty EpubBookMetadata. */
  public static EpubBookMetadata empty() {
    return new EpubBookMetadata(
        Optional.empty(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        null,
        Map.of());
  }

  private static Optional<String> extractSeries(Metadata meta) {
    // Try belongs-to-collection first
    if (meta.getSeriesName() != null && !meta.getSeriesName().isBlank()) {
      return Optional.of(meta.getSeriesName().trim());
    }

    return Optional.empty();
  }

  private static Optional<Float> extractSeriesNumber(Metadata meta) {
    if (meta.getSeriesNumber() != null) {
      return Optional.of(meta.getSeriesNumber());
    }
    return Optional.empty();
  }

  private static Optional<String> extractIsbn(Metadata meta) {
    // Check identifiers
    for (Identifier id : meta.getIdentifiers()) {
      String scheme = id.getScheme();
      if ("isbn".equalsIgnoreCase(scheme)) {
        String value = id.getValue();
        if (value != null && !value.isBlank()) {
          return Optional.of(MetadataNormalizer.cleanIsbn(value));
        }
      }
    }

    return Optional.empty();
  }

  private static Optional<LocalDate> extractPublishedDate(Metadata meta) {
    String dateStr =
        meta.getDates().stream()
            .filter(d -> d.getEvent() == org.grimmory.epub4j.domain.Date.Event.PUBLICATION)
            .map(org.grimmory.epub4j.domain.Date::getValue)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    if (dateStr != null && !dateStr.isBlank()) {
      String normalized = MetadataNormalizer.parseDate(dateStr);
      if (normalized != null) {
        try {
          return Optional.of(LocalDate.parse(normalized));
        } catch (Exception e) {
          log.log(
              System.Logger.Level.DEBUG,
              "Skipping unparsable publication date '" + normalized + "': " + e.getMessage());
        }
      }
    }

    return Optional.empty();
  }

  private static Map<String, String> extractCustomFields(Metadata meta) {
    Map<String, String> custom = new LinkedHashMap<>();

    for (Map.Entry<QName, String> entry : meta.getOtherProperties().entrySet()) {
      String key = entry.getKey().toString();
      String value = entry.getValue();
      if (value != null && !value.isBlank()) {
        custom.put(key, value.trim());
      }
    }

    return Map.copyOf(custom);
  }

  /** Convert this unified metadata back to EPUB Metadata. Useful for round-trip operations. */
  public Metadata toEpubMetadata() {
    Metadata meta = new Metadata();

    title().ifPresent(meta::addTitle);

    for (String author : authors()) {
      String[] parts = WHITESPACE_PATTERN.split(author, 2);
      Author epubAuthor =
          parts.length == 2 ? new Author(parts[0], parts[1]) : new Author("", parts[0]);
      meta.addAuthor(epubAuthor);
    }

    series().ifPresent(meta::setSeriesName);
    seriesNumber().ifPresent(meta::setSeriesNumber);
    isbn().ifPresent(i -> meta.addIdentifier(new Identifier("isbn", i)));
    language().ifPresent(meta::setLanguage);
    publishedDate()
        .ifPresent(
            d ->
                meta.addDate(
                    new org.grimmory.epub4j.domain.Date(
                        d.toString(), org.grimmory.epub4j.domain.Date.Event.PUBLICATION)));

    for (String subject : subjects()) {
      meta.getSubjects().add(subject);
    }

    description().ifPresent(meta::addDescription);
    publisher().ifPresent(meta::addPublisher);

    if (!customFields().isEmpty()) {
      Map<QName, String> props = new HashMap<>(meta.getOtherProperties());
      for (Map.Entry<String, String> entry : customFields().entrySet()) {
        props.put(QName.valueOf(entry.getKey()), entry.getValue());
      }
      meta.setOtherProperties(props);
    }

    return meta;
  }
}
