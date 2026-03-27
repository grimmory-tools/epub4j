package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.domain.Metadata;
import org.grimmory.epub4j.domain.Resource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class PackageDocumentMetadataReaderTest {

  @Test
  public void test1() {
    try {
      Document document =
          EpubProcessorSupport.createDocumentBuilder()
              .parse(PackageDocumentMetadataReader.class.getResourceAsStream("/opf/test2.opf"));
      Metadata metadata = PackageDocumentMetadataReader.readMetadata(document);
      assertEquals(1, metadata.getAuthors().size());
    } catch (Exception e) {
      fail("Unexpected exception: " + e.getMessage(), e);
    }
  }

  @Test
  public void testReadsLanguage() {
    Metadata metadata = getMetadata("/opf/test_language.opf");
    assertEquals("fi", metadata.getLanguage());
  }

  @Test
  public void testDefaultsToEnglish() {
    Metadata metadata = getMetadata("/opf/test_default_language.opf");
    assertEquals("en", metadata.getLanguage());
  }

  private static Metadata getMetadata(String file) {
    try (var in = PackageDocumentMetadataReader.class.getResourceAsStream(file)) {
      assertNotNull(in, "Missing test fixture: " + file);
      Document document = EpubProcessorSupport.createDocumentBuilder().parse(in);

      return PackageDocumentMetadataReader.readMetadata(document);
    } catch (Exception e) {
      fail("Unexpected exception: " + e.getMessage(), e);
      return null;
    }
  }

  @Test
  public void test2() throws SAXException, IOException {
    // given
    String input =
        """
    	        <package version="2.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId">\
    	        <metadata xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">\
    	        <dc:title>Three Men in a Boat</dc:title>\
    	        <dc:creator opf:role="aut" opf:file-as="Jerome, Jerome K.">Jerome K. Jerome</dc:creator>\
    	        <dc:creator opf:role="ill" opf:file-as="Frederics, A.">A. Frederics</dc:creator>\
    	        <dc:language>en</dc:language>\
    	        <dc:date opf:event="publication">1889</dc:date>\
    	        <dc:date opf:event="creation">2009-05-17</dc:date>\
    	        <dc:identifier opf:scheme="URI" id="BookId">zelda@mobileread.com:2010040720</dc:identifier>\
    	        <dc:contributor opf:role="bkp">zelda pinwheel</dc:contributor>\
    	        <dc:publisher>zelda pinwheel</dc:publisher>\
    	        <dc:rights>Public Domain</dc:rights>\
    	        <dc:type>Text</dc:type>\
    	        <dc:type>Image</dc:type>\
    	        <dc:subject>Travel</dc:subject>\
    	        <dc:subject>Humour</dc:subject>\
    	        <dc:description>Three Men in a Boat (To Say Nothing of the Dog), published in 1889, is a humorous account by Jerome K. Jerome of a boating holiday on the Thames between Kingston and Oxford. The book was initially intended to be a serious travel guide, with accounts of local history along the route, but the humorous elements took over to the point where the serious and somewhat sentimental passages seem a distraction to the comic novel. One of the most praised things about Three Men in a Boat is how undated it appears to modern readers, the jokes seem fresh and witty even today.</dc:description>\
    	        <meta name="cover" content="cover_pic" />\
    	        <meta name="calibre:rating" content="8"/>\
    	        </metadata>\
    	        </package>""";

    // when
    Document metadataDocument =
        EpubProcessorSupport.createDocumentBuilder()
            .parse(new InputSource(new StringReader(input)));
    Metadata metadata = PackageDocumentMetadataReader.readMetadata(metadataDocument);

    // then
    assertEquals("Three Men in a Boat", metadata.getFirstTitle());

    // test identifier
    assertNotNull(metadata.getIdentifiers());
    assertEquals(1, metadata.getIdentifiers().size());
    Identifier identifier = metadata.getIdentifiers().getFirst();
    assertEquals("URI", identifier.getScheme());
    assertEquals("zelda@mobileread.com:2010040720", identifier.getValue());

    assertEquals("8", metadata.getMetaAttribute("calibre:rating"));
    assertEquals("cover_pic", metadata.getMetaAttribute("cover"));
  }

  @Test
  public void testEpub3SubtitleViaRefines() {
    Metadata metadata = getMetadata("/opf/test_epub3_metadata.opf");
    assertNotNull(metadata);
    assertEquals("The Main Title", metadata.getFirstTitle());
    assertEquals("A Wonderful Subtitle", metadata.getSubtitle());
  }

  @Test
  public void testEpub3SeriesViaBelongsToCollection() {
    Metadata metadata = getMetadata("/opf/test_epub3_metadata.opf");
    assertNotNull(metadata);
    assertEquals("The Expanse", metadata.getSeriesName());
    assertEquals(3.0f, metadata.getSeriesNumber());
  }

  @Test
  public void testEpub3PageCountFromSchemaPagecount() {
    Metadata metadata = getMetadata("/opf/test_epub3_metadata.opf");
    assertNotNull(metadata);
    assertEquals(456, metadata.getPageCount());
  }

  @Test
  public void testEpub3CreatorRolesViaRefines() {
    Metadata metadata = getMetadata("/opf/test_epub3_metadata.opf");
    assertNotNull(metadata);
    assertEquals(2, metadata.getAuthors().size());
    // setRole converts code to Relator enum
    assertNotNull(metadata.getAuthors().get(0).getRelator());
    assertNotNull(metadata.getAuthors().get(1).getRelator());
  }

  @Test
  public void testUrnIdentifierParsing() {
    Metadata metadata = getMetadata("/opf/test_epub3_metadata.opf");
    assertNotNull(metadata);

    // Find the ISBN identifier (from urn:isbn:9780316769488)
    Identifier isbn =
        metadata.getIdentifiers().stream()
            .filter(id -> "ISBN".equals(id.getScheme()))
            .findFirst()
            .orElse(null);
    assertNotNull(isbn, "Should parse urn:isbn: format");
    assertEquals("9780316769488", isbn.getValue());
    assertTrue(isbn.isIsbn13());
    assertFalse(isbn.isIsbn10());

    // UUID identifier (from urn:uuid:...)
    Identifier uuid =
        metadata.getIdentifiers().stream()
            .filter(id -> "UUID".equals(id.getScheme()))
            .findFirst()
            .orElse(null);
    assertNotNull(uuid, "Should parse urn:uuid: format");
    assertEquals("12345678-1234-1234-1234-123456789012", uuid.getValue());
  }

  @Test
  public void testLegacySeriesFallback() {
    Metadata metadata = getMetadata("/opf/test_legacy_series_metadata.opf");
    assertNotNull(metadata);
    assertEquals("Wheel of Time", metadata.getSeriesName());
    assertEquals(5.5f, metadata.getSeriesNumber());
  }

  @Test
  public void testLegacyPagesFallback() {
    Metadata metadata = getMetadata("/opf/test_legacy_series_metadata.opf");
    assertNotNull(metadata);
    assertEquals(832, metadata.getPageCount());
  }

  @Test
  public void testMediaPagecountVariant() {
    Metadata metadata = getMetadata("/opf/test_pagecount_variants.opf");
    assertNotNull(metadata);
    assertEquals(320, metadata.getPageCount());
  }

  @Test
  public void testNoSubtitleWhenNotPresent() {
    Metadata metadata = getMetadata("/opf/test_legacy_series_metadata.opf");
    assertNotNull(metadata);
    assertNull(metadata.getSubtitle());
  }

  @Test
  public void testNoSeriesWhenNotPresent() {
    Metadata metadata = getMetadata("/opf/test_language.opf");
    assertNotNull(metadata);
    assertNull(metadata.getSeriesName());
    assertNull(metadata.getSeriesNumber());
  }

  @Test
  public void testIdentifierIsbn10Detection() {
    Identifier isbn10 = new Identifier(Identifier.Scheme.ISBN, "0-316-76948-7");
    assertTrue(isbn10.isIsbn10());
    assertFalse(isbn10.isIsbn13());
  }

  @Test
  public void testIdentifierFromUrnFactory() {
    // URN format
    Identifier urn = Identifier.fromUrn("", "urn:isbn:9780316769488");
    assertEquals("ISBN", urn.getScheme());
    assertEquals("9780316769488", urn.getValue());

    // isbn: prefix
    Identifier isbn = Identifier.fromUrn("", "isbn:9780316769488");
    assertEquals("ISBN", isbn.getScheme());
    assertEquals("9780316769488", isbn.getValue());

    // Explicit scheme takes precedence
    Identifier explicit = Identifier.fromUrn("CUSTOM", "urn:isbn:123");
    assertEquals("CUSTOM", explicit.getScheme());
    assertEquals("123", explicit.getValue());

    // Plain value without URN
    Identifier plain = Identifier.fromUrn("UUID", "12345");
    assertEquals("UUID", plain.getScheme());
    assertEquals("12345", plain.getValue());
  }

  @Test
  public void testSubtitleSeriesPageCountRoundTrip() throws IOException {
    Book book = new Book();
    book.getMetadata().addTitle("Round Trip Test");
    book.getMetadata().addAuthor(new Author("Test", "Author"));
    book.getMetadata()
        .addIdentifier(new Identifier(Identifier.Scheme.UUID, "urn:uuid:roundtrip-test-uuid"));
    book.getMetadata().setSubtitle("The Subtitle");
    book.getMetadata().setSeriesName("Test Series");
    book.getMetadata().setSeriesNumber(7.0f);
    book.getMetadata().setPageCount(350);

    String xhtml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 1</title></head>
                <body><p>Content.</p></body>
                </html>""";
    book.addSection(
        "Chapter 1", new Resource(xhtml.getBytes(StandardCharsets.UTF_8), "chapter1.xhtml"));

    // Write
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new EpubWriter().write(book, out);

    // Read back
    Book readBook = new EpubReader().readEpub(new ByteArrayInputStream(out.toByteArray()));

    assertEquals("Round Trip Test", readBook.getMetadata().getFirstTitle());
    assertEquals("The Subtitle", readBook.getMetadata().getSubtitle());
    assertEquals("Test Series", readBook.getMetadata().getSeriesName());
    assertEquals(7.0f, readBook.getMetadata().getSeriesNumber());
    assertEquals(350, readBook.getMetadata().getPageCount());
  }
}
