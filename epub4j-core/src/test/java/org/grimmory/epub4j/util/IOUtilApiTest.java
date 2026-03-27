package org.grimmory.epub4j.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * API compatibility tests for IOUtil verifying that all upstream method signatures are preserved
 * and produce correct results.
 */
public class IOUtilApiTest {

  @Test
  public void testIOCopyBufferSizeConstant() {
    assertEquals(1024 * 64, IOUtil.IO_COPY_BUFFER_SIZE, "IO_COPY_BUFFER_SIZE must be 64KB");
  }

  @Test
  public void testToByteArrayInputStreamSignature() throws Exception {
    Method m = IOUtil.class.getMethod("toByteArray", InputStream.class);
    assertTrue(Modifier.isStatic(m.getModifiers()));
    assertTrue(Modifier.isPublic(m.getModifiers()));
    assertEquals(byte[].class, m.getReturnType());
  }

  @Test
  public void testToByteArrayInputStreamSizeSignature() throws Exception {
    Method m = IOUtil.class.getMethod("toByteArray", InputStream.class, int.class);
    assertTrue(Modifier.isStatic(m.getModifiers()));
    assertTrue(Modifier.isPublic(m.getModifiers()));
    assertEquals(byte[].class, m.getReturnType());
  }

  @Test
  public void testToByteArrayReaderEncodingSignature() throws Exception {
    Method m = IOUtil.class.getMethod("toByteArray", Reader.class, String.class);
    assertTrue(Modifier.isStatic(m.getModifiers()));
    assertTrue(Modifier.isPublic(m.getModifiers()));
    assertEquals(byte[].class, m.getReturnType());
  }

  @Test
  public void testCopyStreamsSignature() throws Exception {
    Method m = IOUtil.class.getMethod("copy", InputStream.class, OutputStream.class);
    assertTrue(Modifier.isStatic(m.getModifiers()));
    assertTrue(Modifier.isPublic(m.getModifiers()));
    assertEquals(int.class, m.getReturnType());
  }

  @Test
  public void testCopyReaderWriterSignature() throws Exception {
    Method m = IOUtil.class.getMethod("copy", Reader.class, Writer.class);
    assertTrue(Modifier.isStatic(m.getModifiers()));
    assertTrue(Modifier.isPublic(m.getModifiers()));
    assertEquals(int.class, m.getReturnType());
  }

  @Test
  public void testToByteArrayReturnsCorrectData() throws IOException {
    byte[] data = {1, 2, 3, 4, 5};
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(data));
    assertArrayEquals(data, result);
  }

  @Test
  public void testToByteArrayWithSizeReturnsExactBytes() throws IOException {
    byte[] data = {10, 20, 30, 40, 50};
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(data), 3);
    assertArrayEquals(new byte[] {10, 20, 30}, result);
  }

  @Test
  public void testToByteArrayWithSizeZeroFallsBack() throws IOException {
    byte[] data = {1, 2, 3};
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(data), 0);
    assertArrayEquals(data, result);
  }

  @Test
  public void testToByteArrayWithNegativeSizeFallsBack() throws IOException {
    // Negative size falls back to readAllBytes
    byte[] data = "test".getBytes();
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(data), -1);
    assertNotNull(result);
    assertArrayEquals(data, result);
  }

  @Test
  public void testCopyReturnsCorrectCount() throws IOException {
    byte[] data = new byte[1000];
    for (int i = 0; i < data.length; i++) data[i] = (byte) i;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int copied = IOUtil.copy(new ByteArrayInputStream(data), out);
    assertEquals(data.length, copied);
    assertArrayEquals(data, out.toByteArray());
  }

  @Test
  public void testCopyEmptyStream() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int copied = IOUtil.copy(new ByteArrayInputStream(new byte[0]), out);
    assertEquals(0, copied);
    assertEquals(0, out.size());
  }

  @Test
  public void testCopyReaderWriter() throws IOException {
    String text = "Hello, Reader/Writer copy test!";
    StringWriter out = new StringWriter();
    int copied = IOUtil.copy(new StringReader(text), out);
    assertEquals(text.length(), copied);
    assertEquals(text, out.toString());
  }

  @Test
  public void testToByteArrayReaderEncoding() throws IOException {
    String text = "UTF-8 test: \u00e9\u00e8\u00ea";
    byte[] result = IOUtil.toByteArray(new StringReader(text), "UTF-8");
    assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), result);
  }

  @Test
  public void testCalcNewNrReadSizeNormal() throws Exception {
    // Protected method, test via reflection
    Method m = IOUtil.class.getDeclaredMethod("calcNewNrReadSize", int.class, int.class);
    m.setAccessible(true);
    assertEquals(15, m.invoke(null, 5, 10));
  }

  @Test
  public void testCalcNewNrReadSizeOverflow() throws Exception {
    Method m = IOUtil.class.getDeclaredMethod("calcNewNrReadSize", int.class, int.class);
    m.setAccessible(true);
    assertEquals(-1, m.invoke(null, 1, Integer.MAX_VALUE));
  }

  @Test
  public void testCalcNewNrReadSizeNegativeTotal() throws Exception {
    Method m = IOUtil.class.getDeclaredMethod("calcNewNrReadSize", int.class, int.class);
    m.setAccessible(true);
    assertEquals(-1, m.invoke(null, 5, -1));
  }
}
