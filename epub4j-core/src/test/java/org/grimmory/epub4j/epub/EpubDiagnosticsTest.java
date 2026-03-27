package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class EpubDiagnosticsTest {

  @Test
  void healthyBookHasNoErrors() {
    Book book = createHealthyBook();
    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(result.isHealthy());
    assertFalse(result.hasErrors());
  }

  @Test
  void detectsMissingTitle() {
    Book book = createHealthyBook();
    book.getMetadata().getTitles().clear();

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.errors().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.META_NO_TITLE));
  }

  @Test
  void detectsMissingLanguage() {
    Book book = createHealthyBook();
    book.getMetadata().setLanguage(null);

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.errors().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.META_NO_LANGUAGE));
  }

  @Test
  void detectsMissingIdentifier() {
    Book book = createHealthyBook();
    book.getMetadata().getIdentifiers().clear();

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.errors().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.META_NO_IDENTIFIER));
  }

  @Test
  void detectsMissingCover() {
    Book book = new Book();
    // No cover image, no images at all
    Resource ch = new Resource("ch1", "data".getBytes(), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);
    book.getTableOfContents().addSection(ch, "Ch1");
    book.getMetadata().addTitle("Test");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "123"));

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.warnings().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.COVER_MISSING));
  }

  @Test
  void detectsEmptySpine() {
    Book book = new Book();
    book.getMetadata().addTitle("Test");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "123"));

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.errors().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.SPINE_EMPTY));
  }

  @Test
  void detectsEmptyTocWithSpine() {
    Book book = createHealthyBook();
    book.setTableOfContents(new TableOfContents());

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.warnings().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.TOC_EMPTY));
  }

  @Test
  void detectsBrokenInternalReferences() {
    Book book = createHealthyBook();
    // Add XHTML that references a non-existent resource
    String xhtml =
        "<html><head><title>t</title></head>"
            + "<body><a href=\"missing.xhtml\">link</a></body></html>";
    Resource ch2 =
        new Resource("ch2", xhtml.getBytes(StandardCharsets.UTF_8), "ch2.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch2);
    book.getSpine().addResource(ch2);

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.warnings().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.BROKEN_REFERENCE));
  }

  @Test
  void detectsOrphanedXhtml() {
    Book book = createHealthyBook();
    Resource orphan = new Resource("orphan", "data".getBytes(), "orphan.xhtml", MediaTypes.XHTML);
    book.getResources().add(orphan);
    // Not added to spine, toc, or guide

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.warnings().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.RESOURCE_ORPHANED_XHTML));
  }

  @Test
  void detectsNullMediaType() {
    Book book = createHealthyBook();
    Resource noType = new Resource("x", "data".getBytes(), "unknown.xyz", null);
    book.getResources().add(noType);

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.warnings().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.RESOURCE_NO_MEDIA_TYPE));
  }

  @Test
  void diagnosticResultSummary() {
    Book book = new Book();
    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    String summary = result.summary();
    assertNotNull(summary);
    assertTrue(summary.contains("errors="));
    assertTrue(summary.contains("warnings="));
  }

  @Test
  void diagnosticToStringFormat() {
    var diag =
        new EpubDiagnostics.Diagnostic(
            EpubDiagnostics.DiagnosticCode.META_NO_TITLE,
            EpubDiagnostics.Severity.ERROR,
            "Test message",
            "file.xhtml",
            true);
    String str = diag.toString();
    assertTrue(str.contains("ERROR"));
    assertTrue(str.contains("META_NO_TITLE"));
    assertTrue(str.contains("file.xhtml"));
    assertTrue(str.contains("auto-fixable"));
  }

  @Test
  void autoFixableCountReflectsFixableIssues() {
    Book book = new Book(); // Missing everything
    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(result.autoFixableCount() > 0);
  }

  @Test
  void detectsNullSpineReference() {
    Book book = createHealthyBook();
    book.getSpine().getSpineReferences().add(new SpineReference(null));

    EpubDiagnostics.DiagnosticResult result = EpubDiagnostics.check(book);
    assertTrue(
        result.errors().stream()
            .anyMatch(d -> d.code() == EpubDiagnostics.DiagnosticCode.SPINE_NULL_REF));
  }

  private static Book createHealthyBook() {
    Book book = new Book();
    String xhtml =
        "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><title>Chapter 1</title></head>"
            + "<body><p>content</p></body></html>";
    Resource ch =
        new Resource("ch1", xhtml.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);
    book.getTableOfContents().addSection(ch, "Chapter 1");
    book.getMetadata().addTitle("Test Book");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "978-0-123456-78-9"));
    byte[] coverData = new byte[20 * 1024];
    Resource cover = new Resource("cover", coverData, "images/cover.jpg", MediaTypes.JPG);
    book.getResources().add(cover);
    book.setCoverImage(cover);
    return book;
  }
}
