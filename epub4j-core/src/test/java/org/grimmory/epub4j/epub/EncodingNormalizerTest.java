package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.junit.jupiter.api.Test;

/** Tests for encoding normalization with pathological test cases. */
public class EncodingNormalizerTest {

  /** Test detection of UTF-8 BOM. */
  @Test
  public void testDetectUtf8Bom() {
    byte[] withBom = {
      (byte) 0xEF,
      (byte) 0xBB,
      (byte) 0xBF, // UTF-8 BOM
      'H',
      'e',
      'l',
      'l',
      'o'
    };

    var result = EncodingNormalizer.detectBom(withBom);
    assertTrue(result.isPresent());
    assertEquals("UTF-8", result.get().getCharset());
  }

  /** Test detection of UTF-16LE BOM. */
  @Test
  public void testDetectUtf16LeBom() {
    byte[] withBom = {
      (byte) 0xFF,
      (byte) 0xFE, // UTF-16LE BOM
      'H',
      0,
      'e',
      0,
      'l',
      0,
      'l',
      0,
      'o',
      0
    };

    var result = EncodingNormalizer.detectBom(withBom);
    assertTrue(result.isPresent());
    assertEquals("UTF-16LE", result.get().getCharset());
  }

  /** Test BOM stripping. */
  @Test
  public void testStripBom() {
    byte[] withBom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'e', 'l', 'l', 'o'};

    byte[] stripped = EncodingNormalizer.stripBom(withBom);
    assertEquals("Hello", new String(stripped, StandardCharsets.UTF_8));
  }

  /** Test XML declaration encoding extraction. */
  @Test
  public void testExtractXmlDeclarationEncoding() {
    String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root/>";
    byte[] data = xml.getBytes(StandardCharsets.UTF_8);

    var result = EncodingNormalizer.extractXmlDeclarationEncoding(data);
    assertTrue(result.isPresent());
    assertEquals("ISO-8859-1", result.get());
  }

  /** Test HTML meta charset extraction. */
  @Test
  public void testExtractHtmlMetaCharset() {
    String html = "<html><head><meta charset=\"UTF-8\"></head><body/></html>";
    byte[] data = html.getBytes(StandardCharsets.UTF_8);

    var result = EncodingNormalizer.extractHtmlMetaEncoding(data);
    assertTrue(result.isPresent());
    assertEquals("UTF-8", result.get());
  }

  /** Test normalization of resource with UTF-8 BOM. */
  @Test
  public void testNormalizeResourceWithBom() throws IOException {
    byte[] withBom = {
      (byte) 0xEF,
      (byte) 0xBB,
      (byte) 0xBF,
      '<',
      '?',
      'x',
      'm',
      'l',
      ' ',
      'v',
      'e',
      'r',
      's',
      'i',
      'o',
      'n',
      '=',
      '"',
      '1',
      '.',
      '0',
      '"',
      '?',
      '>',
      '<',
      'h',
      't',
      'm',
      'l',
      '>',
      '<',
      '/',
      'h',
      't',
      'm',
      'l',
      '>'
    };

    Resource resource = new Resource("test.xhtml");
    resource.setData(withBom);
    resource.setMediaType(org.grimmory.epub4j.domain.MediaTypes.XHTML);

    boolean modified = EncodingNormalizer.normalizeToUtf8(resource);
    assertTrue(modified, "Resource should be modified");

    byte[] normalized = resource.getData();
    assertFalse(normalized[0] == (byte) 0xEF, "BOM should be removed");
    assertEquals("UTF-8", resource.getInputEncoding());
  }

  /** Test encoding analysis. */
  @Test
  public void testEncodingAnalysis() {
    // UTF-8 without BOM - no normalization needed
    byte[] cleanUtf8 = "Hello".getBytes(StandardCharsets.UTF_8);
    var analysis = EncodingNormalizer.analyze(cleanUtf8);
    assertFalse(analysis.needsNormalization());

    // UTF-8 with BOM - normalization needed
    byte[] withBom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'e', 'l', 'l', 'o'};
    analysis = EncodingNormalizer.analyze(withBom);
    assertTrue(analysis.needsNormalization());
    assertTrue(analysis.hasBom());
  }

  /** Test encoding issue detection in book. */
  @Test
  public void testDetectEncodingIssues() {
    Book book = new Book();

    // Add resource with BOM
    byte[] withBom = {
      (byte) 0xEF,
      (byte) 0xBB,
      (byte) 0xBF,
      '<',
      'h',
      't',
      'm',
      'l',
      '>',
      '<',
      '/',
      'h',
      't',
      'm',
      'l',
      '>'
    };
    Resource resourceWithBom = new Resource("chapter1.xhtml");
    resourceWithBom.setData(withBom);
    resourceWithBom.setMediaType(org.grimmory.epub4j.domain.MediaTypes.XHTML);
    book.getResources().add(resourceWithBom);

    // Add clean resource
    Resource cleanResource = new Resource("chapter2.xhtml");
    cleanResource.setData("<html></html>".getBytes(StandardCharsets.UTF_8));
    cleanResource.setMediaType(org.grimmory.epub4j.domain.MediaTypes.XHTML);
    book.getResources().add(cleanResource);

    List<Resource> issues = EncodingNormalizer.detectEncodingIssues(book);
    assertEquals(1, issues.size());
    assertEquals("chapter1.xhtml", issues.getFirst().getHref());
  }

  /** Test encoding name normalization. */
  @Test
  public void testNormalizeEncodingName() {
    assertEquals("UTF-8", EncodingNormalizer.normalizeEncodingName("utf-8"));
    assertEquals("UTF-8", EncodingNormalizer.normalizeEncodingName("UTF8"));
    assertEquals("UTF-8", EncodingNormalizer.normalizeEncodingName("utf8"));
    assertEquals("ISO-8859-1", EncodingNormalizer.normalizeEncodingName("latin1"));
    assertEquals("ISO-8859-1", EncodingNormalizer.normalizeEncodingName("latin-1"));
    assertEquals("Shift_JIS", EncodingNormalizer.normalizeEncodingName("shift_jis"));
    assertEquals("windows-1252", EncodingNormalizer.normalizeEncodingName("cp1252"));
  }
}
