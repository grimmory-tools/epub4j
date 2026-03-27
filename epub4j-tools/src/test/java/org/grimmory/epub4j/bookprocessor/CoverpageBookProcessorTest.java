package org.grimmory.epub4j.bookprocessor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class CoverpageBookProcessorTest {

  @Test
  public void testCalculateAbsoluteImageHref1() {
    String[] testData = {
      "/foo/index.html", "bar.html", "/foo/bar.html",
      "/foo/index.html", "../bar.html", "/bar.html",
      "/foo/index.html", "../sub/bar.html", "/sub/bar.html"
    };
    for (int i = 0; i < testData.length; i += 3) {
      String actualResult =
          CoverPageBookProcessor.calculateAbsoluteImageHref(testData[i + 1], testData[i]);
      assertEquals(testData[i + 2], actualResult);
    }
  }
}
