package org.grimmory.epub4j.html.htmlcleaner;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.bookprocessor.HtmlCleanerBookProcessor;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.junit.jupiter.api.Test;

public class HtmlCleanerBookProcessorTest {

  @Test
  public void testSimpleDocument1() {
    Book book = new Book();
    String testInput = "<html><head><title>title</title></head><body>Hello, world!</html>";
    String expectedResult =
        Constants.DOCTYPE_XHTML
            + "\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>title</title></head><body>Hello, world!</body></html>";
    try {
      Resource resource =
          new Resource(testInput.getBytes(Constants.CHARACTER_ENCODING), "test.html");
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String actualResult = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(expectedResult, actualResult);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }

  @Test
  public void testSimpleDocument2() {
    Book book = new Book();
    String testInput =
        "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>test page</title><link foo=\"bar\" /></head><body background=\"red\">Hello, world!</body></html>";
    try {
      Resource resource =
          new Resource(testInput.getBytes(Constants.CHARACTER_ENCODING), "test.html");
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String result = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(Constants.DOCTYPE_XHTML + "\n" + testInput, result);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }

  @Test
  public void testSimpleDocument3() {
    Book book = new Book();
    String testInput =
        "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>test page</title></head><body>Hello, world! ß</body></html>";
    try {
      Resource resource =
          new Resource(
              null,
              testInput.getBytes(Constants.CHARACTER_ENCODING),
              "test.html",
              MediaTypes.XHTML,
              Constants.CHARACTER_ENCODING);
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String result = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(Constants.DOCTYPE_XHTML + "\n" + testInput, result);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }

  @Test
  public void testSimpleDocument4() {
    Book book = new Book();
    String testInput =
        "<html><head><title>title</title></head><body>Hello, world!\nHow are you ?</html>";
    String expectedResult =
        Constants.DOCTYPE_XHTML
            + "\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>title</title></head><body>Hello, world!\nHow are you ?</body></html>";
    try {
      Resource resource =
          new Resource(testInput.getBytes(Constants.CHARACTER_ENCODING), "test.html");
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String actualResult = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(expectedResult, actualResult);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }

  @Test
  public void testMetaContentType() {
    Book book = new Book();
    String testInput =
        "<html><head><title>title</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\"/></head><body>Hello, world!</html>";
    String expectedResult =
        Constants.DOCTYPE_XHTML
            + "\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>title</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset="
            + Constants.CHARACTER_ENCODING
            + "\" /></head><body>Hello, world!</body></html>";
    try {
      Resource resource =
          new Resource(testInput.getBytes(Constants.CHARACTER_ENCODING), "test.html");
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String actualResult = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(expectedResult, actualResult);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }

  @Test
  public void testDocType1() {
    Book book = new Book();
    String testInput =
        "<html><head><title>title</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\"/></head><body>Hello, world!</html>";
    String expectedResult =
        Constants.DOCTYPE_XHTML
            + "\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>title</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset="
            + Constants.CHARACTER_ENCODING
            + "\" /></head><body>Hello, world!</body></html>";
    try {
      Resource resource =
          new Resource(testInput.getBytes(Constants.CHARACTER_ENCODING), "test.html");
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String actualResult = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(expectedResult, actualResult);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }

  @Test
  public void testDocType2() {
    Book book = new Book();
    String testInput =
        Constants.DOCTYPE_XHTML
            + "\n<html><head><title>title</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\"/></head><body>Hello, world!</html>";
    String expectedResult =
        Constants.DOCTYPE_XHTML
            + "\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>title</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset="
            + Constants.CHARACTER_ENCODING
            + "\" /></head><body>Hello, world!</body></html>";
    try {
      Resource resource =
          new Resource(testInput.getBytes(Constants.CHARACTER_ENCODING), "test.html");
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String actualResult = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(expectedResult, actualResult);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }

  @Test
  public void testXmlNS() {
    Book book = new Book();
    String testInput =
        "<html><head><title>title</title></head><body xmlns:xml=\"xml\">Hello, world!</html>";
    String expectedResult =
        Constants.DOCTYPE_XHTML
            + "\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>title</title></head><body>Hello, world!</body></html>";
    try {
      Resource resource =
          new Resource(testInput.getBytes(Constants.CHARACTER_ENCODING), "test.html");
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String actualResult = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(expectedResult, actualResult);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }

  @Test
  public void testApos() {
    Book book = new Book();
    String testInput =
        "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>test page</title></head><body>'hi'</body></html>";
    try {
      Resource resource =
          new Resource(
              null,
              testInput.getBytes(Constants.CHARACTER_ENCODING),
              "test.html",
              MediaTypes.XHTML,
              Constants.CHARACTER_ENCODING);
      book.getResources().add(resource);
      HtmlCleanerBookProcessor htmlCleanerBookProcessor = new HtmlCleanerBookProcessor();
      byte[] processedHtml = htmlCleanerBookProcessor.processHtml(resource, book);
      String result = new String(processedHtml, Constants.CHARACTER_ENCODING);
      assertEquals(Constants.DOCTYPE_XHTML + "\n" + testInput, result);
    } catch (IOException e) {
      assertTrue(false, e.getMessage());
    }
  }
}
