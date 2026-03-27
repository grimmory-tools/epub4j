package org.grimmory.epub4j.native_parsing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NativeHtmlCleaner
 */
public class NativeHtmlCleanerTest {

    @Test
    public void testCleanSimpleHtml() {
        try (NativeHtmlCleaner cleaner = new NativeHtmlCleaner()) {
            String html = "<html><head><title>Test</title></head><body><p>Hello World!</p></body></html>";
            String xhtml = cleaner.clean(html);

            assertNotNull(xhtml, "XHTML should not be null");
            assertTrue(xhtml.contains("<?xml"), "Should contain XML declaration");
            assertTrue(xhtml.contains("<!DOCTYPE html>"), "Should contain DOCTYPE");
            assertTrue(xhtml.contains("http://www.w3.org/1999/xhtml"), "Should contain XHTML namespace");
            assertTrue(xhtml.contains("<p>"), "Should contain paragraph");
            assertTrue(xhtml.contains("Hello World!"), "Should contain Hello World");
        }
    }

    @Test
    public void testCleanMalformedHtml() {
        try (NativeHtmlCleaner cleaner = new NativeHtmlCleaner()) {
            // Missing closing tags, improper nesting
            String html = "<html><head><title>Test<body><p>Para 1<p>Para 2</body></html>";
            String xhtml = cleaner.clean(html);

            assertNotNull(xhtml, "XHTML should not be null");
            assertTrue(xhtml.contains("</html>"), "Should contain a closed html element");
            assertTrue(xhtml.contains("<body>") && xhtml.contains("</body>"), "Should contain a body element");
        }
    }

    @Test
    public void testCleanRemovesEventHandlers() {
        try (NativeHtmlCleaner cleaner = new NativeHtmlCleaner()) {
            String html = "<html><body><p onclick=\"alert('xss')\">Text</p></body></html>";
            String xhtml = cleaner.clean(html);

            assertNotNull(xhtml, "XHTML should not be null");
            // Event handlers should be stripped for security
            assertFalse(xhtml.toLowerCase().contains("onclick"), "Should not contain onclick");
        }
    }

    @Test
    public void testCleanVoidElements() {
        try (NativeHtmlCleaner cleaner = new NativeHtmlCleaner()) {
            String html = "<html><body><img src=\"test.jpg\"><br><hr></body></html>";
            String xhtml = cleaner.clean(html);

            assertNotNull(xhtml, "XHTML should not be null");
            // Void elements should be properly closed in XHTML
            assertTrue(xhtml.contains("img"), "Should contain img");
        }
    }

    @Test
    public void testCleanWithEncoding() {
        try (NativeHtmlCleaner cleaner = new NativeHtmlCleaner()) {
            String html = "<html><head><title>UTF-8 Test: äöü</title></head><body><p>Text</p></body></html>";
            String xhtml = cleaner.clean(html, "UTF-8");

            assertNotNull(xhtml, "XHTML should not be null");
            assertTrue(xhtml.contains("UTF-8"), "Should contain UTF-8 encoding");
        }
    }

    @Test
    public void testCleanSpecialCharacters() {
        try (NativeHtmlCleaner cleaner = new NativeHtmlCleaner()) {
            String html = "<html><body><p>5 &lt; 10 &amp; 10 &gt; 5</p></body></html>";
            String xhtml = cleaner.clean(html);

            assertNotNull(xhtml, "XHTML should not be null");
            // Special characters should be properly escaped
            assertTrue(xhtml.contains("&amp;"), "Should contain escaped ampersand");
        }
    }

    @Test
    public void testClose() {
        NativeHtmlCleaner cleaner = new NativeHtmlCleaner();
        cleaner.close();

        try {
            cleaner.clean("<html><body>Test</body></html>");
            fail("Should throw IllegalStateException after close");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test
    public void testCleanHtml5Elements() {
        try (NativeHtmlCleaner cleaner = new NativeHtmlCleaner()) {
            String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>" +
                         "<body><article><header><h1>Title</h1></header>" +
                         "<p>Content</p></article></body></html>";
            String xhtml = cleaner.clean(html);

            assertNotNull(xhtml, "XHTML should not be null");
            assertTrue(xhtml.contains("article"), "Should contain article element");
            assertTrue(xhtml.contains("header"), "Should contain header element");
        }
    }
}
