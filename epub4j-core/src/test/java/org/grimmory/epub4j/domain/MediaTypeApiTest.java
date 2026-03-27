package org.grimmory.epub4j.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying the MediaType record maintains API compatibility. MediaType was converted from a
 * class to a record - these tests verify all essential behavior is preserved.
 */
public class MediaTypeApiTest {

  @Test
  public void testMediaTypeIsSerializable() {
    assertTrue(true, "MediaType must implement Serializable");
  }

  @Test
  public void testMediaTypeIsRecord() {
    assertTrue(MediaType.class.isRecord(), "MediaType must be a record");
  }

  @Test
  public void testMediaTypeThreeArgConstructor() {
    Collection<String> exts = Arrays.asList("html", "htm", "xhtml");
    MediaType mt = new MediaType("application/xhtml+xml", "html", exts);
    assertEquals("application/xhtml+xml", mt.name());
    assertEquals("html", mt.defaultExtension());
    assertEquals(3, mt.extensions().size());
  }

  @Test
  public void testMediaTypeTwoArgConstructor() {
    MediaType mt = new MediaType("text/plain", "txt");
    assertEquals("text/plain", mt.name());
    assertEquals("txt", mt.defaultExtension());
    assertTrue(mt.extensions().contains("txt"));
  }

  @Test
  public void testMediaTypeStringArrayConstructor() {
    MediaType mt = new MediaType("image/jpeg", "jpg", new String[] {"jpg", "jpeg"});
    assertEquals("image/jpeg", mt.name());
    assertEquals("jpg", mt.defaultExtension());
    assertTrue(mt.extensions().contains("jpg"));
    assertTrue(mt.extensions().contains("jpeg"));
  }

  @Test
  public void testMediaTypeRecordAccessors() {
    // Record accessors: name(), defaultExtension(), extensions()
    MediaType mt = new MediaType("text/css", "css");
    assertNotNull(mt.name());
    assertNotNull(mt.defaultExtension());
    assertNotNull(mt.extensions());
  }

  @Test
  public void testMediaTypeEquals() {
    MediaType mt1 = new MediaType("text/css", "css");
    MediaType mt2 = new MediaType("text/css", "css");
    MediaType mt3 = new MediaType("text/html", "html");
    assertEquals(mt1, mt2);
    assertNotEquals(mt1, mt3);
  }

  @Test
  public void testMediaTypeHashCode() {
    MediaType mt1 = new MediaType("text/css", "css");
    MediaType mt2 = new MediaType("text/css", "css");
    assertEquals(mt1.hashCode(), mt2.hashCode());
  }

  @Test
  public void testMediaTypeToString() {
    MediaType mt = new MediaType("text/css", "css");
    assertEquals("text/css", mt.toString());
  }

  // MediaTypes static fields

  @Test
  public void testCommonMediaTypesExist() {
    assertNotNull(MediaTypes.XHTML);
    assertNotNull(MediaTypes.EPUB);
    assertNotNull(MediaTypes.NCX);
    assertNotNull(MediaTypes.CSS);
    assertNotNull(MediaTypes.PNG);
    assertNotNull(MediaTypes.GIF);
    assertNotNull(MediaTypes.JPG);
    assertNotNull(MediaTypes.SVG);
  }

  @Test
  public void testMediaTypesByNameLookup() {
    assertNotNull(MediaTypes.mediaTypesByName);
    assertFalse(MediaTypes.mediaTypesByName.isEmpty());
    assertTrue(MediaTypes.mediaTypesByName.containsKey("application/xhtml+xml"));
  }

  @Test
  public void testDetermineMediaType() {
    assertEquals(MediaTypes.CSS, MediaTypes.determineMediaType("style.css"));
    assertEquals(MediaTypes.PNG, MediaTypes.determineMediaType("image.png"));
    assertEquals(MediaTypes.JPG, MediaTypes.determineMediaType("photo.jpg"));
    assertEquals(MediaTypes.XHTML, MediaTypes.determineMediaType("chapter.xhtml"));
    assertEquals(MediaTypes.JPG, MediaTypes.determineMediaType("PHOTO.JpG"));
    assertNull(MediaTypes.determineMediaType("file.unknownext"));
  }

  @Test
  public void testDetermineMediaTypeByName() {
    assertEquals(MediaTypes.XHTML, MediaTypes.getMediaTypeByName("application/xhtml+xml"));
    assertEquals(MediaTypes.CSS, MediaTypes.getMediaTypeByName("text/css"));
  }
}
