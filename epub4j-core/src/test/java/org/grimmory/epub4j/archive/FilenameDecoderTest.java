package org.grimmory.epub4j.archive;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class FilenameDecoderTest {

  @Test
  void testUtf8FlaggedFilenameDecodedAsUtf8() {
    String original = "日本語ファイル.html";
    byte[] rawBytes = original.getBytes(StandardCharsets.UTF_8);
    int utf8Flag = 0x800; // bit 11 set

    String decoded = FilenameDecoder.decodeZipFilename(rawBytes, utf8Flag);
    assertEquals(original, decoded);
  }

  @Test
  void testNonUtf8FlagDecodedAsCp437() {
    // CP437 encoding: 'ü' is 0x81 in CP437
    byte[] rawBytes = {0x66, (byte) 0x81, (byte) 0x72, 0x2E, 0x74, 0x78, 0x74}; // für.txt
    int noUtf8Flag = 0x000;

    String decoded = FilenameDecoder.decodeZipFilename(rawBytes, noUtf8Flag);
    // CP437 0x81 = ü
    assertEquals("für.txt", decoded);
  }

  @Test
  void testAsciiFilenameWorksWithEitherFlag() {
    byte[] rawBytes = "chapter1.html".getBytes(StandardCharsets.US_ASCII);

    String withUtf8 = FilenameDecoder.decodeZipFilename(rawBytes, 0x800);
    String withoutUtf8 = FilenameDecoder.decodeZipFilename(rawBytes, 0x000);

    assertEquals("chapter1.html", withUtf8);
    assertEquals("chapter1.html", withoutUtf8);
  }

  @Test
  void testSniffXmlEncodingUtf8Bom() throws IOException {
    byte[] content = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '<', '?', 'x', 'm', 'l'};
    var in = new ByteArrayInputStream(content);
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("UTF-8", encoding);
  }

  @Test
  void testSniffXmlEncodingFromDeclaration() throws IOException {
    String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root/>";
    var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.US_ASCII));
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("ISO-8859-1", encoding);
  }

  @Test
  void testSniffXmlEncodingReturnsNullWhenUndetermined() throws IOException {
    String xml = "<?xml version=\"1.0\"?><root/>";
    var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.US_ASCII));
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertNull(encoding, "Should return null when no encoding specified");
  }

  @Test
  void testSniffXmlEncodingUtf16BeBom() throws IOException {
    byte[] content = {(byte) 0xFE, (byte) 0xFF, 0x00, 0x3C};
    var in = new ByteArrayInputStream(content);
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("UTF-16BE", encoding);
  }

  @Test
  void testSniffXmlEncodingUtf16LeBom() throws IOException {
    byte[] content = {(byte) 0xFF, (byte) 0xFE, 0x3C, 0x00};
    var in = new ByteArrayInputStream(content);
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("UTF-16LE", encoding);
  }

  @Test
  void testSniffXmlEncodingUtf16BeNullPattern() throws IOException {
    // No BOM, but null-interleaved pattern <?
    byte[] content = {0x00, 0x3C, 0x00, 0x3F};
    var in = new ByteArrayInputStream(content);
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("UTF-16BE", encoding);
  }

  @Test
  void testSniffXmlEncodingUtf16LeNullPattern() throws IOException {
    byte[] content = {0x3C, 0x00, 0x3F, 0x00};
    var in = new ByteArrayInputStream(content);
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("UTF-16LE", encoding);
  }

  @Test
  void testSniffXmlEncodingUcs4BeBom() throws IOException {
    byte[] content = {0x00, 0x00, (byte) 0xFE, (byte) 0xFF, 0x00, 0x00, 0x00, 0x3C};
    var in = new ByteArrayInputStream(content);
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("UCS-4BE", encoding);
  }

  @Test
  void testSniffXmlEncodingUcs4LeBom() throws IOException {
    byte[] content = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x00, 0x3C, 0x00, 0x00, 0x00};
    var in = new ByteArrayInputStream(content);
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("UCS-4LE", encoding);
  }

  @Test
  void testSniffXmlEncodingEbcdic() throws IOException {
    // EBCDIC: <?xm is 0x4C 0x6F 0xA7 0x94
    byte[] content = {0x4C, 0x6F, (byte) 0xA7, (byte) 0x94};
    var in = new ByteArrayInputStream(content);
    String encoding = FilenameDecoder.sniffXmlEncoding(in);
    assertEquals("EBCDIC", encoding);
  }

  @Test
  void testHasUtf8Bom() throws IOException {
    byte[] withBom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '<'};
    byte[] withoutBom = {'<', '?', 'x', 'm'};

    assertTrue(FilenameDecoder.hasUtf8Bom(new ByteArrayInputStream(withBom)));
    assertFalse(FilenameDecoder.hasUtf8Bom(new ByteArrayInputStream(withoutBom)));
  }

  @Test
  void testStreamResetAfterSniffing() throws IOException {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>";
    byte[] data = xml.getBytes(StandardCharsets.US_ASCII);
    var in = new ByteArrayInputStream(data);

    // Sniff should not consume the stream
    FilenameDecoder.sniffXmlEncoding(in);

    // Stream should be back at the start
    byte[] readBack = in.readAllBytes();
    assertArrayEquals(data, readBack);
  }
}
