package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class MetadataNormalizerTest {

  @Test
  void toFirstLastConvertsCommaFormat() {
    assertEquals("Brandon Sanderson", MetadataNormalizer.toFirstLast("Sanderson, Brandon"));
  }

  @Test
  void toFirstLastPreservesFirstLastFormat() {
    assertEquals("Brandon Sanderson", MetadataNormalizer.toFirstLast("Brandon Sanderson"));
  }

  @Test
  void toLastFirstConvertsSingleSpace() {
    assertEquals("Sanderson, Brandon", MetadataNormalizer.toLastFirst("Brandon Sanderson"));
  }

  @Test
  void toLastFirstPreservesCommaFormat() {
    assertEquals("Sanderson, Brandon", MetadataNormalizer.toLastFirst("Sanderson, Brandon"));
  }

  @Test
  void normalizeAuthorsTrimsWhitespace() {
    Metadata meta = new Metadata();
    Author author = new Author("  Brandon  ", "  Sanderson  ");
    meta.addAuthor(author);
    int changes = MetadataNormalizer.normalizeAuthors(meta);
    assertTrue(changes > 0);
    assertEquals("Brandon", author.getFirstname());
    assertEquals("Sanderson", author.getLastname());
  }

  @Test
  void normalizeAuthorsSplitsLastFirst() {
    Metadata meta = new Metadata();
    Author author = new Author("Sanderson, Brandon");
    meta.addAuthor(author);
    MetadataNormalizer.normalizeAuthors(meta);
    assertEquals("Brandon", author.getFirstname());
    assertEquals("Sanderson", author.getLastname());
  }

  @Test
  void cleanIsbnRemovesDashes() {
    // ISBN-13 for "The Hobbit"
    assertEquals("9780547928227", MetadataNormalizer.cleanIsbn("978-0-547-92822-7"));
  }

  @Test
  void cleanIsbnRemovesSpaces() {
    assertEquals("9780547928227", MetadataNormalizer.cleanIsbn("978 0 547 92822 7"));
  }

  @Test
  void cleanIsbnHandlesIsbn10WithX() {
    // ISBN-10 for "The Practice of Programming"
    assertEquals("020161622X", MetadataNormalizer.cleanIsbn("0-201-61622-x"));
  }

  @Test
  void cleanIsbnReturnsNullForInvalid() {
    assertNull(MetadataNormalizer.cleanIsbn("not-an-isbn"));
    assertNull(MetadataNormalizer.cleanIsbn(null));
  }

  @Test
  void isbn10to13Converts() {
    // "The C Programming Language" ISBN-10: 0131103628
    String isbn13 = MetadataNormalizer.isbn10to13("0131103628");
    assertNotNull(isbn13);
    assertEquals(13, isbn13.length());
    assertTrue(isbn13.startsWith("978"));
  }

  @Test
  void isbn13to10Converts978Prefix() {
    // ISBN-13: 9780131103627 → ISBN-10: 0131103628
    String isbn10 = MetadataNormalizer.isbn13to10("9780131103627");
    assertNotNull(isbn10);
    assertEquals(10, isbn10.length());
  }

  @Test
  void isbn13to10ReturnsNullForNon978() {
    // Valid ISBN-13 with 979 prefix cannot be converted to ISBN-10
    assertNull(MetadataNormalizer.isbn13to10("9791032305690"));
  }

  @Test
  void isValidIsbn13ChecksDigit() {
    assertTrue(MetadataNormalizer.isValidIsbn13("978-0-547-92822-7"));
    assertFalse(MetadataNormalizer.isValidIsbn13("978-0-547-92822-0"));
  }

  @Test
  void isValidIsbn10ChecksDigit() {
    assertTrue(MetadataNormalizer.isValidIsbn10("0-131-10362-8"));
  }

  @Test
  void normalizeLanguageCodeHandleTwoLetter() {
    assertEquals("en", MetadataNormalizer.normalizeLanguageCode("en"));
    assertEquals("fr", MetadataNormalizer.normalizeLanguageCode("fr"));
  }

  @Test
  void normalizeLanguageCodeHandlesFullName() {
    assertEquals("en", MetadataNormalizer.normalizeLanguageCode("English"));
    assertEquals("de", MetadataNormalizer.normalizeLanguageCode("German"));
    assertEquals("hu", MetadataNormalizer.normalizeLanguageCode("Hungarian"));
  }

  @Test
  void normalizeLanguageCodeHandlesISO6393() {
    assertEquals("en", MetadataNormalizer.normalizeLanguageCode("eng"));
    assertEquals("fr", MetadataNormalizer.normalizeLanguageCode("fre"));
    assertEquals("de", MetadataNormalizer.normalizeLanguageCode("ger"));
  }

  @Test
  void normalizeLanguageCodeStripsLocale() {
    assertEquals("en", MetadataNormalizer.normalizeLanguageCode("en-US"));
    assertEquals("en", MetadataNormalizer.normalizeLanguageCode("en_GB"));
  }

  @Test
  void normalizeLanguageCodeReturnsInputIfUnknown() {
    assertEquals("xx", MetadataNormalizer.normalizeLanguageCode("xx"));
  }

  @Test
  void parseDateIso() {
    assertEquals("2024-03-15", MetadataNormalizer.parseDate("2024-03-15"));
  }

  @Test
  void parseDateSlashFormat() {
    assertEquals("2024-03-15", MetadataNormalizer.parseDate("2024/03/15"));
  }

  @Test
  void parseDateYearOnly() {
    assertEquals("1951-01-01", MetadataNormalizer.parseDate("1951"));
  }

  @Test
  void parseDateEnglishFormat() {
    assertEquals("2024-03-15", MetadataNormalizer.parseDate("March 15, 2024"));
  }

  @Test
  void parseDateReturnsNullForGarbage() {
    assertNull(MetadataNormalizer.parseDate("not a date"));
    assertNull(MetadataNormalizer.parseDate(null));
  }

  @Test
  void normalizeForSearchStripsDiacriticals() {
    assertEquals("cafe", MetadataNormalizer.normalizeForSearch("Café"));
  }

  @Test
  void normalizeForSearchHandlesSpecialChars() {
    assertEquals("strasse", MetadataNormalizer.normalizeForSearch("Straße"));
    assertEquals("hojde", MetadataNormalizer.normalizeForSearch("Højde"));
  }

  @Test
  void normalizeForSearchLowercases() {
    assertEquals("hello world", MetadataNormalizer.normalizeForSearch("Hello World"));
  }

  @Test
  void normalizeAllModifiesBook() {
    Book book = new Book();
    book.getMetadata().addAuthor(new Author("  Tolkien,  J.R.R.  "));
    book.getMetadata().setLanguage("English");
    int changes = MetadataNormalizer.normalizeAll(book);
    assertTrue(changes > 0);
    assertEquals("en", book.getMetadata().getLanguage());
  }
}
