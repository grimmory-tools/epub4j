package org.grimmory.epub4j.native_parsing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for NativeNCXDocument
 */
public class NativeNCXDocumentTest {

    private static final String SAMPLE_NCX =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                      <head>
                        <meta name="dtb:uid" content="urn:uuid:12345678-1234-1234-1234-123456789012"/>
                        <meta name="dtb:generator" content="test"/>
                        <meta name="dtb:depth" content="1"/>
                        <meta name="dtb:totalPageCount" content="0"/>
                        <meta name="dtb:maxPageNumber" content="0"/>
                      </head>
                      <docTitle><text>Test Book</text></docTitle>
                      <docAuthor><text>Test Author</text></docAuthor>
                      <navMap>
                        <navPoint id="navPoint-1" playOrder="1">
                          <navLabel><text>Chapter 1</text></navLabel>
                          <content src="chapter1.xhtml"/>
                        </navPoint>
                        <navPoint id="navPoint-2" playOrder="2">
                          <navLabel><text>Chapter 2</text></navLabel>
                          <content src="chapter2.xhtml"/>
                        </navPoint>
                        <navPoint id="navPoint-3" playOrder="3">
                          <navLabel><text>Chapter 3</text></navLabel>
                          <content src="chapter3.xhtml"/>
                        </navPoint>
                      </navMap>
                    </ncx>""";

    @Test
    public void testParse() {
        try (NativeNCXDocument ncx = NativeNCXDocument.parse(SAMPLE_NCX)) {
            assertNotNull(ncx, "NCX document should not be null");
        }
    }

    @Test
    public void testGetTocReferences() {
        try (NativeNCXDocument ncx = NativeNCXDocument.parse(SAMPLE_NCX)) {
            List<NativeNCXDocument.TOCReference> tocRefs = ncx.getTocReferences();
            assertNotNull(tocRefs, "TOC references should not be null");
            assertEquals(3, tocRefs.size(), "Should have 3 TOC references");

            NativeNCXDocument.TOCReference ref1 = tocRefs.getFirst();
            assertEquals("Chapter 1", ref1.title(), "First title should match");
            assertEquals("chapter1.xhtml", ref1.href(), "First href should match");
            assertEquals(1, ref1.playOrder(), "First playOrder should match");

            NativeNCXDocument.TOCReference ref2 = tocRefs.get(1);
            assertEquals("Chapter 2", ref2.title(), "Second title should match");
            assertEquals("chapter2.xhtml", ref2.href(), "Second href should match");
        }
    }

    @Test
    public void testWrite() {
        List<NativeNCXDocument.TOCReference> tocRefs = Arrays.asList(
            new NativeNCXDocument.TOCReference("Chapter 1", "chapter1.xhtml", 1, false),
            new NativeNCXDocument.TOCReference("Chapter 2", "chapter2.xhtml", 2, false),
            new NativeNCXDocument.TOCReference("Chapter 3", "chapter3.xhtml", 3, true)
        );

        String xml = NativeNCXDocument.write(
                List.of("urn:uuid:12345678-1234-1234-1234-123456789012"),
            "Test Book Title",
                List.of("Test Author"),
            tocRefs
        );

        assertNotNull(xml, "XML should not be null");
        assertTrue(xml.startsWith("<?xml"), "Should contain XML declaration");
        assertTrue(xml.contains("http://www.daisy.org/z3986/2005/ncx/"), "Should contain NCX namespace");
        assertTrue(xml.contains("Test Book Title"), "Should contain title");
        assertTrue(xml.contains("Test Author"), "Should contain author");
        assertTrue(xml.contains("Chapter 1"), "Should contain Chapter 1");
        assertTrue(xml.contains("chapter1.xhtml"), "Should contain chapter1.xhtml");
    }

    @Test
    public void testWriteEmptyToc() {
        String xml = NativeNCXDocument.write(
                List.of("urn:uuid:123"),
            "Empty Book",
                List.of("Author"),
                List.of()
        );

        assertNotNull(xml, "XML should not be null");
        assertTrue(xml.contains("<navMap>"), "Should contain navMap");
    }

    @Test
    public void testClose() {
        NativeNCXDocument ncx = NativeNCXDocument.parse(SAMPLE_NCX);
        ncx.close();

        try {
            ncx.getTocReferences();
            fail("Should throw IllegalStateException after close");
        } catch (IllegalStateException e) {
            // Expected
        }
    }
}
