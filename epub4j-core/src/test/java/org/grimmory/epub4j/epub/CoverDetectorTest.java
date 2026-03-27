package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class CoverDetectorTest {

  @Test
  void returnsAlreadySetCover() {
    Book book = new Book();
    Resource cover = new Resource("cover", new byte[15000], "images/cover.jpg", MediaTypes.JPG);
    book.setCoverImage(cover);

    assertEquals(cover, CoverDetector.detectCoverImage(book));
  }

  @Test
  void detectsCoverByExactFilename() {
    Book book = new Book();
    Resource img1 =
        new Resource("img1", new byte[15000], "images/illustration.png", MediaTypes.PNG);
    Resource img2 = new Resource("img2", new byte[15000], "images/cover.jpg", MediaTypes.JPG);
    book.getResources().add(img1);
    book.getResources().add(img2);

    Resource detected = CoverDetector.detectCoverImage(book);
    assertEquals(img2, detected);
  }

  @Test
  void detectsCoverByPartialFilename() {
    Book book = new Book();
    Resource img =
        new Resource("img1", new byte[15000], "images/book-cover-front.png", MediaTypes.PNG);
    book.getResources().add(img);

    Resource detected = CoverDetector.detectCoverImage(book);
    assertEquals(img, detected);
  }

  @Test
  void prefersExactOverPartialMatch() {
    Book book = new Book();
    Resource partial = new Resource("img1", new byte[15000], "images/discover.png", MediaTypes.PNG);
    Resource exact = new Resource("img2", new byte[15000], "images/cover.jpg", MediaTypes.JPG);
    book.getResources().add(partial);
    book.getResources().add(exact);

    assertEquals(exact, CoverDetector.detectCoverImage(book));
  }

  @Test
  void detectsFromFirstSpineItemImgTag() {
    Book book = new Book();
    String xhtml =
        "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><title>Cover</title></head>"
            + "<body><img src=\"images/front.jpg\" alt=\"cover\"/></body></html>";
    Resource ch =
        new Resource(
            "ch1", xhtml.getBytes(StandardCharsets.UTF_8), "text/cover.xhtml", MediaTypes.XHTML);
    Resource img = new Resource("img1", new byte[15000], "text/images/front.jpg", MediaTypes.JPG);
    book.getResources().add(ch);
    book.getResources().add(img);
    book.getSpine().addResource(ch);

    Resource detected = CoverDetector.detectCoverImage(book);
    assertEquals(img, detected);
  }

  @Test
  void detectsLargestImageAsFallback() {
    Book book = new Book();
    Resource small = new Resource("img1", new byte[11000], "images/thumb.jpg", MediaTypes.JPG);
    Resource large = new Resource("img2", new byte[50000], "images/fullpage.png", MediaTypes.PNG);
    book.getResources().add(small);
    book.getResources().add(large);

    Resource detected = CoverDetector.detectCoverImage(book);
    assertEquals(large, detected);
  }

  @Test
  void returnsNullWhenNoImages() {
    Book book = new Book();
    Resource ch = new Resource("ch1", "text".getBytes(), "ch1.xhtml", MediaTypes.XHTML);
    book.getResources().add(ch);
    book.getSpine().addResource(ch);

    assertNull(CoverDetector.detectCoverImage(book));
  }

  @Test
  void ignoresImagesBelowMinSize() {
    Book book = new Book();
    Resource tinyImg = new Resource("img1", new byte[500], "images/tiny.jpg", MediaTypes.JPG);
    book.getResources().add(tinyImg);
    // No "cover" in name, no spine, below threshold → should not detect
    assertNull(CoverDetector.detectCoverImage(book));
  }

  @Test
  void detectsSvgImageFromSpine() {
    Book book = new Book();
    String xhtml =
        "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><title>Cover</title></head>"
            + "<body><svg><image xlink:href=\"img/art.png\"/></svg></body></html>";
    Resource ch =
        new Resource(
            "ch1", xhtml.getBytes(StandardCharsets.UTF_8), "text/cover.xhtml", MediaTypes.XHTML);
    Resource img = new Resource("img1", new byte[20000], "text/img/art.png", MediaTypes.PNG);
    book.getResources().add(ch);
    book.getResources().add(img);
    book.getSpine().addResource(ch);

    assertEquals(img, CoverDetector.detectCoverImage(book));
  }
}
