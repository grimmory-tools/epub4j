package org.grimmory.epub4j.native_parsing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for NativeNavDocument
 */
public class NativeNavDocumentTest {

    @Test
    public void testCreate() {
        List<NativeNCXDocument.TOCReference> tocRefs = Arrays.asList(
            new NativeNCXDocument.TOCReference("Chapter 1", "chapter1.xhtml", 1, false),
            new NativeNCXDocument.TOCReference("Chapter 2", "chapter2.xhtml", 2, false),
            new NativeNCXDocument.TOCReference("Chapter 3", "chapter3.xhtml", 3, true)
        );

        String xhtml = NativeNavDocument.create("Test Book", tocRefs);

        assertNotNull(xhtml, "XHTML should not be null");
        assertTrue(xhtml.contains("<!DOCTYPE html>"), "Should contain DOCTYPE");
        assertTrue(xhtml.contains("http://www.w3.org/1999/xhtml"), "Should contain XHTML namespace");
        assertTrue(xhtml.contains("http://www.idpf.org/2007/ops"), "Should contain EPUB namespace");
        assertTrue(xhtml.contains("epub:type=\"toc\""), "Should contain nav element with epub:type=\"toc\"");
        assertTrue(xhtml.contains("Test Book"), "Should contain title");
        assertTrue(xhtml.contains("Chapter 1"), "Should contain Chapter 1");
        assertTrue(xhtml.contains("chapter1.xhtml"), "Should contain chapter1.xhtml");
        assertTrue(xhtml.contains("<ol>"), "Should contain ordered list");
        assertTrue(xhtml.contains("<li>"), "Should contain list items");
    }

    @Test
    public void testCreateMinimal() {
        String xhtml = NativeNavDocument.createMinimal("Minimal Book");

        assertNotNull(xhtml, "XHTML should not be null");
        assertTrue(xhtml.contains("<!DOCTYPE html>"), "Should contain DOCTYPE");
        assertTrue(xhtml.contains("<nav"), "Should contain nav element");
        assertTrue(xhtml.contains("Minimal Book"), "Should contain title");
    }

    @Test
    public void testCreateSingle() {
        String xhtml = NativeNavDocument.createSingle("Single Chapter Book", "The Only Chapter", "chapter.xhtml");

        assertNotNull(xhtml, "XHTML should not be null");
        assertTrue(xhtml.contains("<a href=\"chapter.xhtml\""), "Should contain link");
        assertTrue(xhtml.contains("The Only Chapter"), "Should contain entry title");
    }

    @Test
    public void testCreateEmptyToc() {
        String xhtml = NativeNavDocument.create("Empty TOC Book", List.of());

        assertNotNull(xhtml, "XHTML should not be null");
        assertTrue(xhtml.contains("<nav"), "Should contain nav element");
    }
}
