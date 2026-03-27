package org.grimmory.epub4j.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.grimmory.epub4j.util.StringUtil;

/**
 * A Book's identifier.
 *
 * <p>Defaults to a random UUID and scheme "UUID"
 *
 * @author paul
 */
public class Identifier implements Serializable {

  @Serial private static final long serialVersionUID = 955949951416391810L;
  private static final Pattern ISBN10_PATTERN = Pattern.compile("\\d{9}[\\dXx]");
  private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[- ]");
  private static final Pattern ISBN13_PATTERN = Pattern.compile("\\d{13}");

  public interface Scheme {

    String UUID = "UUID";
    String ISBN = "ISBN";
    String ISBN10 = "ISBN10";
    String ISBN13 = "ISBN13";
    String URL = "URL";
    String URI = "URI";
    String ASIN = "ASIN";
    String GOODREADS = "GOODREADS";
    String GOOGLE = "GOOGLE";
    String HARDCOVER = "HARDCOVER";
    String COMICVINE = "COMICVINE";
    String LUBIMYCZYTAC = "LUBIMYCZYTAC";
    String RANOBEDB = "RANOBEDB";
  }

  private boolean bookId = false;
  private String scheme;
  private String value;

  /** Creates an Identifier with as value a random UUID and scheme "UUID" */
  public Identifier() {
    this(Scheme.UUID, UUID.randomUUID().toString());
  }

  public Identifier(String scheme, String value) {
    this.scheme = scheme;
    this.value = value;
  }

  /**
   * Creates an Identifier by parsing URN-format values. Handles formats like
   * "urn:isbn:9780316769488" and "isbn:9780316769488". If an explicit opf:scheme is provided, it
   * takes precedence over the URN-derived scheme.
   *
   * @param scheme the opf:scheme attribute value (may be blank)
   * @param rawValue the raw identifier text content
   * @return a new Identifier with the resolved scheme and value
   */
  public static Identifier fromUrn(String scheme, String rawValue) {
    if (rawValue == null) {
      return new Identifier(scheme, null);
    }
    String effectiveScheme = scheme;
    String value = rawValue;

    String lower = rawValue.toLowerCase();
    if (lower.startsWith("urn:")) {
      String[] parts = rawValue.split(":", 3);
      if (parts.length >= 3) {
        if (StringUtil.isBlank(effectiveScheme)) {
          effectiveScheme = parts[1].toUpperCase();
        }
        value = parts[2];
      }
    } else if (lower.startsWith("isbn:")) {
      if (StringUtil.isBlank(effectiveScheme)) {
        effectiveScheme = Scheme.ISBN;
      }
      value = rawValue.substring(5);
    }

    return new Identifier(effectiveScheme, value);
  }

  /** Returns true if this identifier is an ISBN-13 (13 digits after removing separators). */
  public boolean isIsbn13() {
    if (!(Scheme.ISBN.equalsIgnoreCase(scheme) || Scheme.ISBN13.equalsIgnoreCase(scheme))
        || value == null) {
      return false;
    }
    String normalized = SEPARATOR_PATTERN.matcher(value).replaceAll("");
    return ISBN13_PATTERN.matcher(normalized).matches();
  }

  /** Returns true if this identifier is an ISBN-10 (10 characters after removing separators). */
  public boolean isIsbn10() {
    if (!(Scheme.ISBN.equalsIgnoreCase(scheme) || Scheme.ISBN10.equalsIgnoreCase(scheme))
        || value == null) {
      return false;
    }
    String normalized = SEPARATOR_PATTERN.matcher(value).replaceAll("");
    return ISBN10_PATTERN.matcher(normalized).matches();
  }

  /**
   * The first identifier for which the bookId is true is made the bookId identifier.
   *
   * <p>If no identifier has bookId == true then the first bookId identifier is written as the
   * primary.
   *
   * @param identifiers
   * @return The first identifier for which the bookId is true is made the bookId identifier.
   */
  public static Identifier getBookIdIdentifier(List<Identifier> identifiers) {
    if (identifiers == null || identifiers.isEmpty()) {
      return null;
    }

    Identifier result = null;
    for (Identifier identifier : identifiers) {
      if (identifier.bookId) {
        result = identifier;
        break;
      }
    }

    if (result == null) {
      result = identifiers.getFirst();
    }

    return result;
  }

  public String getScheme() {
    return scheme;
  }

  public void setScheme(String scheme) {
    this.scheme = scheme;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public void setBookId(boolean bookId) {
    this.bookId = bookId;
  }

  /**
   * This bookId property allows the book creator to add multiple ids and tell the epubwriter which
   * one to write out as the bookId.
   *
   * <p>The Dublin Core metadata spec allows multiple identifiers for a Book. The epub spec requires
   * exactly one identifier to be marked as the book id.
   *
   * @return whether this is the unique book id.
   */
  public boolean isBookId() {
    return bookId;
  }

  public int hashCode() {
    return StringUtil.defaultIfNull(scheme).hashCode() ^ StringUtil.defaultIfNull(value).hashCode();
  }

  public boolean equals(Object otherIdentifier) {
    if (!(otherIdentifier instanceof Identifier other)) {
      return false;
    }
    return StringUtil.equals(scheme, other.scheme) && StringUtil.equals(value, other.value);
  }

  public String toString() {
    if (StringUtil.isBlank(scheme)) {
      return value;
    }
    return scheme + ":" + value;
  }
}
