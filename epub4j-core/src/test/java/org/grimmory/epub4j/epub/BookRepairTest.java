package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class BookRepairTest {

  private static final String XHTML_WITH_TITLE =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
          + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter One</title></head>"
          + "<body><p>content</p></body></html>";

  @Test
  void repairFixesMissingMediaTypes() {
    Book book = new Book();
    Resource res = new Resource("id1", "data".getBytes(), "styles/main.css", null);
    book.getResources().add(res);
    book.getMetadata().addTitle("Test");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "123"));

    BookRepair.RepairResult result = new BookRepair().repair(book);
    assertTrue(result.hasChanges());
    assertEquals(MediaTypes.CSS, res.getMediaType());
  }

  @Test
  void repairNormalizesHrefs() {
    Book book = new Book();
    Resource res =
        new Resource("id1", "data".getBytes(), "content/../text/ch01.xhtml", MediaTypes.XHTML);
    book.getResources().add(res);
    book.getSpine().addResource(res);
    book.getMetadata().addTitle("Test");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "123"));

    BookRepair.RepairResult result = new BookRepair().repair(book);
    assertTrue(
        result.actions().stream().anyMatch(a -> a.code() == BookRepair.RepairCode.NORMALIZE_HREF));
  }

  @Test
  void repairFixesDuplicateIds() {
    Book book = new Book();
    Resource res1 = new Resource("dup", "a".getBytes(), "ch1.xhtml", MediaTypes.XHTML);
    Resource res2 = new Resource("dup", "b".getBytes(), "ch2.xhtml", MediaTypes.XHTML);
    book.getResources().add(res1);
    book.getResources().add(res2);
    book.getMetadata().addTitle("Test");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "123"));

    new BookRepair().repair(book);
    assertNotEquals(res1.getId(), res2.getId());
  }

  @Test
  void repairRemovesNullSpineReferences() {
    Book book = new Book();
    book.getSpine().getSpineReferences().add(new SpineReference(null));
    Resource valid =
        new Resource("id1", XHTML_WITH_TITLE.getBytes(), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(valid);
    book.getSpine().addResource(valid);
    book.getMetadata().addTitle("Test");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "123"));

    BookRepair.RepairResult result = new BookRepair().repair(book);
    assertTrue(
        result.actions().stream().anyMatch(a -> a.code() == BookRepair.RepairCode.SPINE_NULL_REF));
    assertEquals(1, book.getSpine().size());
  }

  @Test
  void repairSynthesizesMissingToc() {
    Book book = new Book();
    Resource ch =
        new Resource(
            "id1",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "ch1.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);
    book.getMetadata().addTitle("Test");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "123"));

    assertEquals(0, book.getTableOfContents().size());
    new BookRepair().repair(book);
    assertTrue(book.getTableOfContents().size() > 0);
  }

  @Test
  void repairFixesEmptyMetadata() {
    Book book = new Book();
    // All metadata blank
    BookRepair.RepairResult result = new BookRepair().repair(book);
    assertTrue(result.hasChanges());
    assertFalse(book.getMetadata().getFirstTitle().isBlank());
    assertNotNull(book.getMetadata().getLanguage());
    assertFalse(book.getMetadata().getIdentifiers().isEmpty());
  }

  @Test
  void repairExtractsTitleFromContent() {
    Book book = new Book();
    Resource ch =
        new Resource(
            "id1",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "ch1.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    new BookRepair().repair(book);
    assertEquals("Chapter One", book.getMetadata().getFirstTitle());
  }

  @Test
  void repairNoChangesOnHealthyBook() {
    Book book = createHealthyBook();
    BookRepair.RepairResult result = new BookRepair().repair(book);
    assertEquals(0, result.fixCount());
  }

  @Test
  void repairCleansKindleLikeUppercaseAttributesAndEmptyInlineTags() throws Exception {
    Book book = new Book();
    String raw =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>T</title></head>"
            + "<body LANG=\"EN\" DIR=\"LTR\"><a href=\"#\"></a><b></b><i> </i><p>ok</p></body></html>";
    Resource ch =
        new Resource("id1", raw.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    BookRepair.RepairResult result = new BookRepair().repair(book);
    String repaired = new String(ch.getData(), StandardCharsets.UTF_8);

    assertTrue(
        result.actions().stream().anyMatch(a -> a.code() == BookRepair.RepairCode.MARKUP_CLEANED));
    assertFalse(repaired.contains(" LANG="));
    assertFalse(repaired.contains(" DIR="));
    assertFalse(repaired.contains("<a href=\"#\"></a>"));
    assertFalse(repaired.contains("<b></b>"));
  }

  @Test
  void repairStripsMicrosoftOfficeMarkup() throws Exception {
    Book book = new Book();
    String raw =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>T</title></head>"
            + "<body><o:p>Office</o:p><p>Keep</p><o:p/></body></html>";
    Resource ch =
        new Resource("id1", raw.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    new BookRepair().repair(book);
    String repaired = new String(ch.getData(), StandardCharsets.UTF_8);

    assertFalse(repaired.toLowerCase().contains("<o:p"));
    assertTrue(repaired.contains("Office"));
    assertTrue(repaired.contains("<p>Keep</p>"));
  }

  @Test
  void repairStrictlyNormalizesLegacyXhtmlCaseButKeepsNamespacedAttributes() throws Exception {
    Book book = new Book();
    String raw =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<HTML xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">"
            + "<BODY LANG=\"EN\" DIR=\"LTR\"><P CLASS=\"Intro\">Hello</P>"
            + "<svg:image XLINK:HREF=\"cover.jpg\"/></BODY></HTML>";
    Resource ch =
        new Resource("id1", raw.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    new BookRepair().repair(book);
    String repaired = new String(ch.getData(), StandardCharsets.UTF_8);

    assertTrue(repaired.contains("<html"));
    assertTrue(repaired.contains("<body"));
    assertTrue(repaired.contains("<p class=\"Intro\">"));
    assertFalse(repaired.contains(" LANG="));
    assertFalse(repaired.contains(" DIR="));
  }

  @Test
  void repairStripsDrmMetaAndJavascriptArtifacts() throws Exception {
    Book book = new Book();
    String raw =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>"
            + "<meta name=\"adept.resource\" content=\"urn:uuid:test\"/>"
            + "<script type=\"text/javascript\">alert(1)</script>"
            + "</head><body>"
            + "<a href=\"javascript:alert(2)\" onclick=\"alert(3)\">Go</a>"
            + "</body></html>";
    Resource ch =
        new Resource("id1", raw.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    new BookRepair().repair(book);
    String repaired = new String(ch.getData(), StandardCharsets.UTF_8);

    assertFalse(repaired.toLowerCase().contains("adept.resource"));
    assertFalse(repaired.toLowerCase().contains("<script"));
    assertFalse(repaired.toLowerCase().contains("onclick="));
    assertFalse(repaired.toLowerCase().contains("javascript:"));
    assertTrue(repaired.contains("<a>Go</a>"));
  }

  @Test
  void repairPrunesBrokenTocEntriesAndPromotesChildren() {
    Book book = new Book();

    Resource valid =
        new Resource(
            "id-valid",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "text/ch1.xhtml",
            MediaTypes.XHTML);
    Resource broken =
        new Resource(
            "id-broken",
            "<html/>".getBytes(StandardCharsets.UTF_8),
            "text/missing.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(valid);

    TOCReference parent = new TOCReference("Broken Parent", broken);
    TOCReference child = new TOCReference("Valid Child", valid);
    parent.addChildSection(child);
    book.getTableOfContents().addTOCReference(parent);

    BookRepair.RepairResult result = new BookRepair().repair(book);

    assertTrue(
        result.actions().stream()
            .anyMatch(a -> a.code() == BookRepair.RepairCode.BROKEN_TOC_PRUNED));
    assertEquals(1, book.getTableOfContents().getTocReferences().size());
    assertEquals("Valid Child", book.getTableOfContents().getTocReferences().getFirst().getTitle());
    assertEquals(
        "text/ch1.xhtml",
        book.getTableOfContents().getTocReferences().getFirst().getResource().getHref());
  }

  @Test
  void repairRemovesUnreferencedJavascriptResources() {
    Book book = new Book();

    Resource html =
        new Resource(
            "id-html",
            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head></head><body></body></html>")
                .getBytes(StandardCharsets.UTF_8),
            "text/ch1.xhtml",
            MediaTypes.XHTML);
    Resource usedJs =
        new Resource(
            "id-js-used",
            "console.log('a');".getBytes(StandardCharsets.UTF_8),
            "scripts/used.js",
            MediaTypes.JAVASCRIPT);
    Resource orphanJs =
        new Resource(
            "id-js-orphan",
            "console.log('b');".getBytes(StandardCharsets.UTF_8),
            "scripts/orphan.js",
            MediaTypes.JAVASCRIPT);
    Resource htmlRef =
        new Resource(
            "id-html-ref",
            ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><link rel=\"preload\" href=\"../scripts/used.js\"/></head><body></body></html>")
                .getBytes(StandardCharsets.UTF_8),
            "text/ch2.xhtml",
            MediaTypes.XHTML);

    book.getResources().add(html);
    book.getResources().add(usedJs);
    book.getResources().add(orphanJs);
    book.getResources().add(htmlRef);
    book.getSpine().addResource(html);
    book.getSpine().addResource(htmlRef);

    BookRepair.RepairResult result = new BookRepair().repair(book);

    assertNotNull(book.getResources().getByHref("scripts/used.js"));
    assertNull(book.getResources().getByHref("scripts/orphan.js"));
    assertTrue(
        result.actions().stream()
            .anyMatch(a -> a.code() == BookRepair.RepairCode.JS_ORPHAN_REMOVED));
  }

  @Test
  void repairRemovesKnownArtifactResources() {
    Book book = new Book();
    Resource html =
        new Resource(
            "id-html",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "text/ch1.xhtml",
            MediaTypes.XHTML);
    Resource dsStore = new Resource("id-ds", new byte[] {1}, ".DS_Store", MediaTypes.CSS);
    Resource bookmarks =
        new Resource("id-bm", new byte[] {1}, "META-INF/calibre_bookmarks.txt", MediaTypes.CSS);

    book.getResources().add(html);
    book.getResources().add(dsStore);
    book.getResources().add(bookmarks);
    book.getSpine().addResource(html);

    BookRepair.RepairResult result = new BookRepair().repair(book);

    assertNull(book.getResources().getByHref(".DS_Store"));
    assertNull(book.getResources().getByHref("META-INF/calibre_bookmarks.txt"));
    assertTrue(
        result.actions().stream()
            .anyMatch(a -> a.code() == BookRepair.RepairCode.ARTIFACT_REMOVED));
  }

  @Test
  void repairNormalizesInvalidLanguageTag() {
    Book book = new Book();
    book.getMetadata().addTitle("T");
    book.getMetadata().setLanguage("invalid_language_tag!!");
    book.getMetadata().addIdentifier(new Identifier("isbn", "123"));

    BookRepair.RepairResult result = new BookRepair().repair(book);

    assertEquals("en", book.getMetadata().getLanguage());
    assertTrue(
        result.actions().stream()
            .anyMatch(a -> a.code() == BookRepair.RepairCode.INVALID_LANGUAGE));
  }

  @Test
  void repairRemovesStrayImgTagsWithoutSource() throws Exception {
    Book book = new Book();
    String raw =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>T</title></head>"
            + "<body><img alt=\"x\"/><img src=\"\"/><img src=\"cover.jpg\"/></body></html>";
    Resource ch =
        new Resource("id1", raw.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    new BookRepair().repair(book);
    String repaired = new String(ch.getData(), StandardCharsets.UTF_8);

    assertFalse(repaired.contains("<img alt=\"x\"/>"));
    assertFalse(repaired.contains("<img src=\"\"/>"));
    assertTrue(repaired.contains("cover.jpg") || repaired.contains("<img"));
  }

  @Test
  void repairNormalizesSpineAndAddsMissingXhtmlItems() {
    Book book = new Book();

    Resource ch1 =
        new Resource(
            "id-ch1",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "text/ch1.xhtml",
            MediaTypes.XHTML);
    Resource ch2 =
        new Resource(
            "id-ch2",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "text/ch2.xhtml",
            MediaTypes.XHTML);
    Resource img = new Resource("id-img", new byte[] {1, 2, 3}, "images/pic.jpg", MediaTypes.JPG);

    book.getResources().add(ch1);
    book.getResources().add(ch2);
    book.getResources().add(img);

    // Broken/duplicate-ish spine: non-XHTML first, then duplicate chapter, missing ch2
    book.getSpine().addResource(img);
    book.getSpine().addResource(ch1);
    book.getSpine().addResource(ch1);

    BookRepair.RepairResult result = new BookRepair().repair(book);

    assertEquals(2, book.getSpine().size());
    assertEquals("text/ch1.xhtml", book.getSpine().getResource(0).getHref());
    assertEquals("text/ch2.xhtml", book.getSpine().getResource(1).getHref());

    assertTrue(
        result.actions().stream()
            .anyMatch(a -> a.code() == BookRepair.RepairCode.SPINE_NON_XHTML_REMOVED));
    assertTrue(
        result.actions().stream()
            .anyMatch(a -> a.code() == BookRepair.RepairCode.SPINE_DUPLICATE_REMOVED));
    assertTrue(
        result.actions().stream()
            .anyMatch(action -> action.code() == BookRepair.RepairCode.SPINE_ITEM_ADDED));
  }

  @Test
  void repairBuildsSpineFromManifestXhtmlWhenSpineEmpty() {
    Book book = new Book();

    Resource b =
        new Resource(
            "id-b",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "text/b.xhtml",
            MediaTypes.XHTML);
    Resource a =
        new Resource(
            "id-a",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "text/a.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(b);
    book.getResources().add(a);

    assertTrue(book.getSpine().isEmpty());

    BookRepair.RepairResult result = new BookRepair().repair(book);

    assertEquals(2, book.getSpine().size());
    assertEquals("text/a.xhtml", book.getSpine().getResource(0).getHref());
    assertEquals("text/b.xhtml", book.getSpine().getResource(1).getHref());
    assertTrue(
        result.actions().stream()
            .anyMatch(action -> action.code() == BookRepair.RepairCode.SPINE_ITEM_ADDED));
  }

  @Test
  void repairReconcilesSpineHrefAliasToManifestResource() {
    Book book = new Book();

    Resource manifestResource =
        new Resource(
            "id-ch1",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "Text/ChapterOne.xhtml",
            MediaTypes.XHTML);
    Resource aliasOnly =
        new Resource(
            "alias",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "text/chapterone.xhtml",
            MediaTypes.XHTML);

    book.getResources().add(manifestResource);
    book.getSpine().addSpineReference(new SpineReference(aliasOnly));

    BookRepair.RepairResult result = new BookRepair().repair(book);

    assertEquals(1, book.getSpine().size());
    assertEquals("Text/ChapterOne.xhtml", book.getSpine().getResource(0).getHref());
    assertTrue(
        result.actions().stream()
            .anyMatch(action -> action.code() == BookRepair.RepairCode.SPINE_ALIAS_RECONCILED));
  }

  @Test
  void repairHardensMalformedXhtmlBeforeParsing() throws Exception {
    Book book = new Book();
    String raw =
        """


                \uFEFF<?xml version="1.0" encoding="UTF-8"?><html><head><title>T</title></head>\
                <body><p>A & B</p></body></html>""";

    Resource ch =
        new Resource(
            "id1", raw.getBytes(StandardCharsets.UTF_8), "text/ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    BookRepair.RepairResult result = new BookRepair().repair(book);
    String repaired = new String(ch.getData(), StandardCharsets.UTF_8);

    assertTrue(repaired.contains("&amp; B"));
    assertTrue(repaired.contains("xmlns=\"http://www.w3.org/1999/xhtml\""));
    assertTrue(
        result.actions().stream()
            .anyMatch(action -> action.code() == BookRepair.RepairCode.XHTML_PREPARSE_HARDENED));
  }

  @Test
  void repairRewritesBrokenInternalLinksUsingSafeAliasHeuristics() throws Exception {
    Book book = new Book();

    String chapter1 =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>One</title></head>"
            + "<body><a href=\"chapter2.xhtml\">Next</a></body></html>";
    String chapter2 =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Two</title></head><body/></html>";

    Resource ch1 =
        new Resource(
            "id1", chapter1.getBytes(StandardCharsets.UTF_8), "Text/ch1.xhtml", MediaTypes.XHTML);
    Resource ch2 =
        new Resource(
            "id2",
            chapter2.getBytes(StandardCharsets.UTF_8),
            "Text/Chapter2.xhtml",
            MediaTypes.XHTML);

    book.getResources().add(ch1);
    book.getResources().add(ch2);
    book.getSpine().addResource(ch1);
    book.getSpine().addResource(ch2);

    BookRepair.RepairResult result = new BookRepair().repair(book);
    String repaired = new String(ch1.getData(), StandardCharsets.UTF_8);

    assertTrue(repaired.contains("href=\"Chapter2.xhtml\""));
    assertTrue(
        result.actions().stream()
            .anyMatch(action -> action.code() == BookRepair.RepairCode.LINK_REWRITTEN));
  }

  @Test
  void repairResultRecordMethods() {
    Book book = new Book();
    BookRepair.RepairResult result = new BookRepair().repair(book);
    assertTrue(result.hasChanges());
    assertTrue(result.fixCount() > 0);
    assertNotNull(result.book());
    assertNotNull(result.actions());
  }

  @Test
  void repairStripsDangerousEmbeddedContentTags() throws Exception {
    Book book = new Book();
    String raw =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>T</title></head>"
            + "<body>"
            + "<object data=\"flash.swf\" type=\"application/x-shockwave-flash\"><param name=\"movie\" value=\"flash.swf\"/></object>"
            + "<embed src=\"plugin.swf\" type=\"application/x-shockwave-flash\"/>"
            + "<applet code=\"Malicious.class\" width=\"300\" height=\"300\">Fallback</applet>"
            + "<iframe src=\"https://evil.example.com\"></iframe>"
            + "<form action=\"https://evil.example.com/steal\"><input type=\"text\"/></form>"
            + "<p>Safe content</p>"
            + "</body></html>";
    Resource ch =
        new Resource("id1", raw.getBytes(StandardCharsets.UTF_8), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    new BookRepair().repair(book);
    String repaired = new String(ch.getData(), StandardCharsets.UTF_8);

    assertFalse(repaired.toLowerCase().contains("<object"));
    assertFalse(repaired.toLowerCase().contains("<embed"));
    assertFalse(repaired.toLowerCase().contains("<applet"));
    assertFalse(repaired.toLowerCase().contains("<iframe"));
    assertFalse(repaired.toLowerCase().contains("<form"));
    assertTrue(repaired.contains("<p>Safe content</p>"));
  }

  private static Book createHealthyBook() {
    Book book = new Book();
    Resource ch =
        new Resource(
            "id1",
            XHTML_WITH_TITLE.getBytes(StandardCharsets.UTF_8),
            "ch1.xhtml",
            MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);
    book.getTableOfContents().addSection(ch, "Chapter One");
    book.getMetadata().addTitle("My Book");
    book.getMetadata().setLanguage("en");
    book.getMetadata().addIdentifier(new Identifier("isbn", "978-0-123456-78-9"));
    byte[] coverData = new byte[20 * 1024]; // 20KB fake image
    Resource cover = new Resource("cover", coverData, "images/cover.jpg", MediaTypes.JPG);
    book.getResources().add(cover);
    book.setCoverImage(cover);
    return book;
  }
}
