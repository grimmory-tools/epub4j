package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpubReaderTocTest {

  @TempDir Path tempDir;

  @Test
  void validNcxRetainsPublisherTocWithoutFallback() throws IOException {
    var result = new EpubReader().readEpubWithReport(createEpub(false, false, false));

    assertToc(result, "Publisher NCX label");
    assertNotNull(result.book().getNcxResource());
    assertFalse(result.report().hasCorrections());
  }

  @Test
  void strictModeAcceptsValidNcx() throws IOException {
    var reader = new EpubReader(null, EpubProcessingPolicy.strictPolicy());
    var result = reader.readEpubWithReport(createEpub(false, false, false));

    assertToc(result, "Publisher NCX label");
  }

  @Test
  void navRemainsPreferredWhenNcxHasDifferentLabels() throws IOException {
    var result = new EpubReader().readEpubWithReport(createEpub(true, false, false));

    assertToc(result, "Preferred NAV label");
    assertNotNull(result.book().getNcxResource());
  }

  @Test
  void unusableNavFallsBackToValidNcx() throws IOException {
    var result = new EpubReader().readEpubWithReport(createEpub(true, false, true));

    assertToc(result, "Publisher NCX label");
  }

  @Test
  void malformedNcxStillWarnsAndSynthesizesToc() throws IOException {
    var result = new EpubReader().readEpubWithReport(createEpub(false, true, false));

    assertEquals(1, result.book().getTableOfContents().size());
    assertTrue(
        result.report().warnings().stream()
            .anyMatch(w -> w.code() == EpubReader.IngestionCode.TOC_INVALID));
    assertTrue(result.report().hasCorrections());
  }

  private static void assertToc(EpubReader.ReadResult result, String expectedLabel) {
    assertEquals(1, result.book().getTableOfContents().size());
    assertEquals(
        expectedLabel, result.book().getTableOfContents().getTocReferences().getFirst().getTitle());
    assertFalse(
        result.report().warnings().stream()
            .anyMatch(w -> w.code() == EpubReader.IngestionCode.TOC_INVALID));
  }

  private Path createEpub(boolean includeNav, boolean malformedNcx, boolean unusableNav)
      throws IOException {
    Path path = Files.createTempFile(tempDir, "toc-", ".epub");
    String navItem =
        includeNav
            ? "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
            : "";
    try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
      addEntry(zip, "mimetype", "application/epub+zip");
      addEntry(
          zip,
          "META-INF/container.xml",
          """
          <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
            <rootfiles><rootfile full-path="content.opf"
              media-type="application/oebps-package+xml"/></rootfiles>
          </container>
          """);
      addEntry(
          zip,
          "content.opf",
          """
          <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="id" version="%s">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
              <dc:identifier id="id">test</dc:identifier><dc:title>Fixture</dc:title>
              <dc:language>en</dc:language>
            </metadata>
            <manifest>
              <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
              <item id="c" href="chapter.xhtml" media-type="application/xhtml+xml"/>
              %s
            </manifest>
            <spine toc="ncx"><itemref idref="c"/></spine>
          </package>
          """
              .formatted(includeNav ? "3.0" : "2.0", navItem));
      addEntry(
          zip,
          "chapter.xhtml",
          """
          <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Spine fallback label</title></head><body><p>Test</p></body>
          </html>
          """);
      addEntry(
          zip,
          "toc.ncx",
          malformedNcx
              ? "malformed"
              : """
              <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head><meta name="dtb:uid" content="test"/></head>
                <docTitle><text>Fixture</text></docTitle>
                <navMap><navPoint id="p1" playOrder="1">
                  <navLabel><text>Publisher NCX label</text></navLabel>
                  <content src="chapter.xhtml"/>
                </navPoint></navMap>
              </ncx>
              """);
      if (includeNav) {
        addEntry(
            zip,
            "nav.xhtml",
            """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <head><title>Navigation</title></head>
              <body><nav epub:type="%s"><ol>
                <li><a href="chapter.xhtml">Preferred NAV label</a></li>
              </ol></nav></body>
            </html>
            """
                .formatted(unusableNav ? "landmarks" : "toc"));
      }
    }
    return path;
  }

  private static void addEntry(ZipOutputStream zip, String name, String content)
      throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
