package org.grimmory.epub4j.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Unified metadata model for all book formats (EPUB, PDF, CBZ, etc.). This interface provides a
 * common abstraction that format-specific metadata implementations can implement, allowing Grimmory
 * to handle all book types uniformly.
 *
 * <p>Implementations should be immutable records or classes that extract data from format-specific
 * metadata (EPUB OPF, PDF XMP, ComicInfo.xml, etc.)
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // EPUB
 * BookMetadata epubMeta = EpubBookMetadata.from(epubBook);
 *
 * // PDF
 * BookMetadata pdfMeta = PdfBookMetadata.from(pdfDocument);
 *
 * // Uniform access
 * System.out.println(epubMeta.title());
 * System.out.println(pdfMeta.authors().get(0));
 * }</pre>
 *
 * @author Grimmory
 */
public interface BookMetadata {

  /**
   * The book's primary title.
   *
   * @return the title, or empty if not available
   */
  Optional<String> title();

  /**
   * All authors/creators of the book.
   *
   * @return list of authors (may be empty)
   */
  List<String> authors();

  /**
   * The series name this book belongs to, if any.
   *
   * @return series name, or empty
   */
  Optional<String> series();

  /**
   * The volume/sequence number within the series.
   *
   * @return series number, or empty
   */
  Optional<Float> seriesNumber();

  /**
   * The ISBN identifier, if available.
   *
   * @return ISBN, or empty
   */
  Optional<String> isbn();

  /**
   * The language code (ISO 639-1 two-letter code preferred).
   *
   * @return language code, or empty
   */
  Optional<String> language();

  /**
   * The publication date.
   *
   * @return publication date, or empty
   */
  Optional<LocalDate> publishedDate();

  /**
   * All subjects/genres/tags.
   *
   * @return list of subjects (may be empty)
   */
  List<String> subjects();

  /**
   * A description/abstract of the book.
   *
   * @return description, or empty
   */
  Optional<String> description();

  /**
   * The publisher name.
   *
   * @return publisher, or empty
   */
  Optional<String> publisher();

  /**
   * Format-specific raw metadata object. Cast to the appropriate type for format-specific access.
   *
   * @return the raw metadata object (e.g., {@link Metadata} for EPUB)
   */
  Object rawMetadata();

  /**
   * The book format this metadata represents.
   *
   * @return the book format enum value
   */
  BookFormat format();

  /**
   * Additional custom metadata fields not covered by standard fields. Implementations may return an
   * empty map if no custom fields exist.
   *
   * @return map of custom field name → value
   */
  default Map<String, String> customFields() {
    return Collections.emptyMap();
  }

  /** Whether this metadata has a title. */
  default boolean hasTitle() {
    return title().isPresent();
  }

  /** Whether this metadata has any authors. */
  default boolean hasAuthors() {
    return !authors().isEmpty();
  }

  /** Whether this metadata has a series. */
  default boolean hasSeries() {
    return series().isPresent();
  }

  /** Whether this metadata has an ISBN. */
  default boolean hasIsbn() {
    return isbn().isPresent();
  }

  /**
   * Get the first author, if any.
   *
   * @return the first author, or empty
   */
  default Optional<String> firstAuthor() {
    return authors().isEmpty() ? Optional.empty() : Optional.of(authors().getFirst());
  }

  /**
   * Get the first subject/genre, if any.
   *
   * @return the first subject, or empty
   */
  default Optional<String> firstSubject() {
    return subjects().isEmpty() ? Optional.empty() : Optional.of(subjects().getFirst());
  }

  /** Book format enumeration. */
  enum BookFormat {
    EPUB("EPUB"),
    PDF("PDF"),
    CBZ("CBZ"),
    CBR("CBR"),
    MOBI("MOBI"),
    AZW3("AZW3"),
    FB2("FB2"),
    DJVU("DjVu"),
    UNKNOWN("Unknown");

    private final String displayName;

    BookFormat(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }
}
