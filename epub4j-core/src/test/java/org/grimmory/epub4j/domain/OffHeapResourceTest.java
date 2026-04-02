package org.grimmory.epub4j.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OffHeapResourceTest {

  @Test
  void testGetDataReturnsCopy() throws IOException {
    byte[] original = "hello off-heap".getBytes(StandardCharsets.UTF_8);
    try (var resource = new OffHeapResource(original, "test.txt")) {
      byte[] copy = resource.getData();
      assertArrayEquals(original, copy);
      // Verify it is a copy, not the same array
      assertNotSame(original, copy);
    }
  }

  @Test
  void testGetInputStreamReadsCorrectly() throws IOException {
    byte[] data = "stream test data".getBytes(StandardCharsets.UTF_8);
    try (var resource = new OffHeapResource(data, "test.html")) {
      try (InputStream is = resource.getInputStream()) {
        byte[] read = is.readAllBytes();
        assertArrayEquals(data, read);
      }
    }
  }

  @Test
  void testGetSizeMatchesData() throws IOException {
    byte[] data = new byte[1024];
    try (var resource = new OffHeapResource(data, "image.png")) {
      assertEquals(1024, resource.getSize());
    }
  }

  @Test
  void testSetDataReplacesContent() throws IOException {
    byte[] original = "original".getBytes(StandardCharsets.UTF_8);
    byte[] replacement = "replaced content".getBytes(StandardCharsets.UTF_8);
    try (var resource = new OffHeapResource(original, "test.txt")) {
      resource.setData(replacement);
      assertArrayEquals(replacement, resource.getData());
      assertEquals(replacement.length, resource.getSize());
    }
  }

  @Test
  void testCloseReleasesMemory() throws IOException {
    byte[] data = "closeable".getBytes(StandardCharsets.UTF_8);
    var resource = new OffHeapResource(data, "test.txt");
    resource.close();

    assertEquals(0, resource.getSize());
    assertNull(resource.getSegment());
  }

  @Test
  void testDoubleCloseIsSafe() {
    byte[] data = "double close".getBytes(StandardCharsets.UTF_8);
    var resource = new OffHeapResource(data, "test.txt");
    resource.close();
    assertDoesNotThrow(resource::close);
  }

  @Test
  void testGetDataAfterCloseThrowsIOException() {
    byte[] data = "closed data".getBytes(StandardCharsets.UTF_8);
    var resource = new OffHeapResource(data, "test.txt");
    resource.close();
    assertThrows(IOException.class, resource::getData);
  }

  @Test
  void testGetInputStreamAfterCloseThrowsIOException() {
    byte[] data = "closed stream".getBytes(StandardCharsets.UTF_8);
    var resource = new OffHeapResource(data, "test.txt");
    resource.close();
    assertThrows(IOException.class, resource::getInputStream);
  }

  @Test
  void testMediaTypeDetected() {
    byte[] data = new byte[10];
    try (var resource = new OffHeapResource(data, "cover.jpg")) {
      assertEquals(MediaTypes.JPG, resource.getMediaType());
    }
  }

  @Test
  void testIdConstructor() throws IOException {
    byte[] data = "with id".getBytes(StandardCharsets.UTF_8);
    try (var resource = new OffHeapResource("res-1", data, "chapter.xhtml", MediaTypes.XHTML)) {
      assertEquals("res-1", resource.getId());
      assertEquals("chapter.xhtml", resource.getHref());
      assertEquals(MediaTypes.XHTML, resource.getMediaType());
      assertArrayEquals(data, resource.getData());
    }
  }

  @Test
  void testSegmentAccessible() {
    byte[] data = new byte[256];
    try (var resource = new OffHeapResource(data, "font.otf")) {
      assertNotNull(resource.getSegment());
      assertEquals(256, resource.getSegment().byteSize());
    }
  }

  @Test
  void testSerializationRoundTrip() throws Exception {
    byte[] data = "serialize me".getBytes(StandardCharsets.UTF_8);
    OffHeapResource original = new OffHeapResource(data, "test.txt");

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(original);
    }
    original.close();

    try (ObjectInputStream ois =
        new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
      OffHeapResource deserialized = (OffHeapResource) ois.readObject();
      assertArrayEquals(data, deserialized.getData());
      assertNotNull(deserialized.getSegment());
      deserialized.close();
    }
  }

  @Test
  void testAsInputStreamReturnsWorkingStream() throws IOException {
    byte[] data = "as-inputstream data".getBytes(StandardCharsets.UTF_8);
    try (var resource = new OffHeapResource(data, "test.html")) {
      try (InputStream is = resource.asInputStream()) {
        byte[] read = is.readAllBytes();
        assertArrayEquals(data, read);
      }
    }
  }

  @Test
  void testLargeResource() throws IOException {
    // 1MB resource to verify off-heap works at scale
    byte[] data = new byte[1024 * 1024];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i & 0xFF);
    }
    try (var resource = new OffHeapResource(data, "large.bin")) {
      assertEquals(1024 * 1024, resource.getSize());
      byte[] read = resource.getData();
      assertArrayEquals(data, read);
    }
  }
}
