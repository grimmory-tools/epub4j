package org.grimmory.epub4j.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying IOUtil's native-optimized stream operations work correctly and perform well for
 * various data sizes.
 */
public class IOUtilPerformanceTest {

  @Test
  public void testToByteArrayEmpty() throws IOException {
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(new byte[0]));
    assertEquals(0, result.length);
  }

  @Test
  public void testToByteArraySmall() throws IOException {
    byte[] data = "small data".getBytes();
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(data));
    assertArrayEquals(data, result);
  }

  @Test
  public void testToByteArrayLarge() throws IOException {
    byte[] data = new byte[5 * 1024 * 1024]; // 5MB
    new Random(42).nextBytes(data);
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(data));
    assertArrayEquals(data, result);
  }

  @Test
  public void testToByteArrayWithSize() throws IOException {
    byte[] data = new byte[10000];
    new Random(42).nextBytes(data);
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(data), 10000);
    assertArrayEquals(data, result);
  }

  @Test
  public void testToByteArrayWithSizeZero() throws IOException {
    byte[] data = "fallback to readAllBytes".getBytes();
    byte[] result = IOUtil.toByteArray(new ByteArrayInputStream(data), 0);
    assertArrayEquals(data, result);
  }

  @Test
  public void testToByteArrayReaderEncoding() throws IOException {
    String text = "Hello, UTF-8 world! \u00e9\u00e8\u00ea";
    byte[] result = IOUtil.toByteArray(new StringReader(text), "UTF-8");
    assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), result);
  }

  @Test
  public void testCopyLargeStream() throws IOException {
    byte[] data = new byte[2 * 1024 * 1024]; // 2MB
    new Random(42).nextBytes(data);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int copied = IOUtil.copy(new ByteArrayInputStream(data), out);
    assertEquals(data.length, copied);
    assertArrayEquals(data, out.toByteArray());
  }

  @Test
  public void testCopyPerformance() throws IOException {
    byte[] data = new byte[10 * 1024 * 1024]; // 10MB
    new Random(42).nextBytes(data);

    long startTime = System.nanoTime();
    for (int i = 0; i < 10; i++) {
      ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
      IOUtil.copy(new ByteArrayInputStream(data), out);
    }
    long elapsed = System.nanoTime() - startTime;
    double msPerOp = elapsed / 10.0 / 1_000_000.0;

    // transferTo should copy 10MB in well under 100ms
    assertTrue(msPerOp < 500, "copy(10MB) should be fast: " + msPerOp + "ms");
  }

  @Test
  public void testToByteArrayPerformance() throws IOException {
    byte[] data = new byte[10 * 1024 * 1024]; // 10MB
    new Random(42).nextBytes(data);

    long startTime = System.nanoTime();
    for (int i = 0; i < 10; i++) {
      IOUtil.toByteArray(new ByteArrayInputStream(data));
    }
    long elapsed = System.nanoTime() - startTime;
    double msPerOp = elapsed / 10.0 / 1_000_000.0;

    // readAllBytes should be fast
    assertTrue(msPerOp < 500, "toByteArray(10MB) should be fast: " + msPerOp + "ms");
  }

  @Test
  public void testNoResourceLeakOnLargeStreams() throws IOException {
    byte[] data = new byte[100_000];
    new Random(42).nextBytes(data);

    for (int i = 0; i < 5000; i++) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      IOUtil.copy(new ByteArrayInputStream(data), out);
    }
    // If we get here without OOM, no leak
  }
}
