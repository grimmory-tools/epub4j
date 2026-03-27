package org.grimmory.epub4j.native_parsing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

/**
 * Tests for NativePackageReader
 */
public class NativePackageReaderTest {

    private static final String SAMPLE_OPF =
            """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:identifier id="uid">urn:uuid:12345678-1234-1234-1234-123456789012</dc:identifier>
                        <dc:title>Test Book Title</dc:title>
                        <dc:creator>Test Author</dc:creator>
                        <dc:language>en</dc:language>
                        <meta property="dcterms:modified">2024-01-01T00:00:00Z</meta>
                      </metadata>
                      <manifest>
                        <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                      </manifest>
                      <spine>
                        <itemref idref="cover"/>
                        <itemref idref="chapter1"/>
                        <itemref idref="chapter2"/>
                      </spine>
                    </package>""";

    @Test
    public void testParse() {
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            assertNotNull(reader, "Reader should not be null");
        }
    }

    @Test
    public void testGetPackageId() {
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            String id = reader.getPackageId();
            assertEquals("urn:uuid:12345678-1234-1234-1234-123456789012", id, "Package ID should match");
        }
    }

    @Test
    public void testGetMetadata() {
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            String title = reader.getMetadata("title");
            assertEquals("Test Book Title", title, "Title should match");

            String creator = reader.getMetadata("creator");
            assertEquals("Test Author", creator, "Creator should match");

            String language = reader.getMetadata("language");
            assertEquals("en", language, "Language should match");
        }
    }

    @Test
    public void testGetSpineItems() {
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            List<String> spineItems = reader.getSpineItems();
            assertNotNull(spineItems, "Spine items should not be null");
            assertEquals(3, spineItems.size(), "Should have 3 spine items");
            assertEquals("cover", spineItems.get(0), "First item should be cover");
            assertEquals("chapter1", spineItems.get(1), "Second item should be chapter1");
            assertEquals("chapter2", spineItems.get(2), "Third item should be chapter2");
        }
    }

    @Test
    public void testGetManifestItem() {
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            NativePackageReader.ManifestItem item = reader.getManifestItem("chapter1");
            assertNotNull(item, "Item should not be null");
            assertEquals("chapter1.xhtml", item.href(), "Href should match");
            assertEquals("application/xhtml+xml", item.mediaType(), "Media type should match");

            // Test non-existent item
            NativePackageReader.ManifestItem missing = reader.getManifestItem("nonexistent");
            assertNull(missing, "Missing item should be null");
        }
    }

    @Test
    public void testGetAllMetadata() {
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            Map<String, String> metadata = reader.getAllMetadata();
            assertNotNull(metadata, "Metadata should not be null");
            assertTrue(metadata.containsKey("title"), "Should have title");
            assertTrue(metadata.containsKey("creator"), "Should have creator");
            assertTrue(metadata.containsKey("language"), "Should have language");
        }
    }

    @Test
    public void testClose() {
        NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF);
        reader.close();

        // Should throw IllegalStateException after close
        try {
            reader.getPackageId();
            fail("Should throw IllegalStateException after close");
        } catch (IllegalStateException e) {
            // Expected
        }
    }

    @Test
    public void testGetAllManifestItems() {
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            Map<String, NativePackageReader.ManifestItem> allItems = reader.getAllManifestItems();
            assertNotNull(allItems, "All manifest items should not be null");
            assertEquals(4, allItems.size(), "Should have 4 manifest items");

            // Check all items are present
            assertTrue(allItems.containsKey("cover"), "Should have cover");
            assertTrue(allItems.containsKey("chapter1"), "Should have chapter1");
            assertTrue(allItems.containsKey("chapter2"), "Should have chapter2");
            assertTrue(allItems.containsKey("nav"), "Should have nav");

            // Verify item details
            NativePackageReader.ManifestItem navItem = allItems.get("nav");
            assertEquals("nav.xhtml", navItem.href(), "Nav href should match");
            assertEquals("application/xhtml+xml", navItem.mediaType(), "Nav media type should match");
        }
    }

    @Test
    public void testGetAllManifestItemsVsSpineItems() {
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            Map<String, NativePackageReader.ManifestItem> allItems = reader.getAllManifestItems();
            List<String> spineItems = reader.getSpineItems();

            // All spine items should be in manifest
            for (String spineId : spineItems) {
                assertTrue(allItems.containsKey(spineId), "Spine item " + spineId + " should be in manifest");
            }

            // Manifest may have items not in spine (like nav)
            assertTrue(allItems.size() >= spineItems.size(), "Manifest should have at least as many items as spine");
        }
    }

    @Test
    public void testMemoryNoLeakMultipleCalls() {
        // Test that multiple calls don't leak memory
        try (NativePackageReader reader = NativePackageReader.parse(SAMPLE_OPF)) {
            for (int i = 0; i < 100; i++) {
                String id = reader.getPackageId();
                assertNotNull(id);

                Map<String, NativePackageReader.ManifestItem> items = reader.getAllManifestItems();
                assertNotNull(items);

                List<String> spine = reader.getSpineItems();
                assertNotNull(spine);
            }
            // If there's a memory leak, this test would cause OOM in a stress test
            // For regular test, just completing without error is success
        }
    }
}
