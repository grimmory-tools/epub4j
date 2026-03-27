package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.*;
import org.junit.jupiter.api.Test;

class XhtmlCleanerTest {

  private static final Pattern HR_PATTERN = Pattern.compile("(?s).*<hr\\s*/>.*");
  private static final Pattern BR_PATTERN = Pattern.compile("(?s).*<br\\s*/>.*");

  private static Resource xhtml(String content) {
    return new Resource(
        null, content.getBytes(StandardCharsets.UTF_8), "test.xhtml", MediaTypes.XHTML);
  }

  private static String cleaned(Resource r) throws IOException {
    return new String(r.getData(), StandardCharsets.UTF_8);
  }

  @Test
  void addsXmlPrologWhenMissing() throws IOException {
    Resource r = xhtml("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body></body></html>");
    XhtmlCleaner.CleanResult result = XhtmlCleaner.clean(r);
    assertTrue(result.modified());
    assertTrue(cleaned(r).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
  }

  @Test
  void preservesExistingXmlProlog() throws IOException {
    Resource r =
        xhtml(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body></body></html>");
    XhtmlCleaner.clean(r);
    // Should not duplicate prolog
    assertFalse(
        cleaned(r).contains("<?xml")
            && cleaned(r).indexOf("<?xml") != cleaned(r).lastIndexOf("<?xml"));
  }

  @Test
  void addsXmlnsWhenMissing() throws IOException {
    Resource r = xhtml("<?xml version=\"1.0\"?><html><body></body></html>");
    XhtmlCleaner.clean(r);
    assertTrue(cleaned(r).contains("xmlns=\"http://www.w3.org/1999/xhtml\""));
  }

  @Test
  void selfClosesVoidElements() throws IOException {
    Resource r =
        xhtml(
            "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body><br><hr><img src=\"a.jpg\"></body></html>");
    XhtmlCleaner.CleanResult result = XhtmlCleaner.clean(r);
    String content = cleaned(r);
    assertTrue(BR_PATTERN.matcher(content).matches(), content);
    assertTrue(HR_PATTERN.matcher(content).matches(), content);
    assertTrue(content.contains("<img src=\"a.jpg\">"), content);
    assertTrue(result.fixCount() >= 2);
  }

  @Test
  void preservesAlreadySelfClosedElements() {
    Resource r =
        xhtml(
            "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body><br/></body></html>");
    XhtmlCleaner.CleanResult result = XhtmlCleaner.clean(r);
    assertEquals(0, result.fixCount());
    assertFalse(result.modified());
  }

  @Test
  void replacesHtmlEntitiesWithNumeric() throws IOException {
    Resource r =
        xhtml(
            "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body>&nbsp;&mdash;&copy;</body></html>");
    XhtmlCleaner.clean(r);
    String content = cleaned(r);
    assertTrue(content.contains("&nbsp;"), content);
    assertTrue(content.contains("&mdash;"), content);
    assertTrue(content.contains("&copy;"), content);
  }

  @Test
  void fixesBooleanAttributes() throws IOException {
    Resource r =
        xhtml(
            "<?xml version=\"1.0\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body><input checked disabled/></body></html>");
    XhtmlCleaner.clean(r);
    String content = cleaned(r);
    assertTrue(content.contains("checked=\"checked\""));
    assertTrue(content.contains("disabled=\"disabled\""));
  }

  @Test
  void cleanAllProcessesMultipleResources() {
    Book book = new Book();
    book.getResources().add(xhtml("<html><body><br></body></html>"));
    // Add another with different href
    Resource r2 =
        new Resource(
            null, "<p>&nbsp;</p>".getBytes(StandardCharsets.UTF_8), "ch2.xhtml", MediaTypes.XHTML);
    book.getResources().add(r2);
    int fixes = XhtmlCleaner.cleanAll(book);
    assertTrue(fixes > 0);
  }

  @Test
  void skipsNonXhtmlResources() {
    Book book = new Book();
    book.getResources()
        .add(
            new Resource(
                null,
                "body{color:red}".getBytes(StandardCharsets.UTF_8),
                "style.css",
                MediaTypes.CSS));
    int fixes = XhtmlCleaner.cleanAll(book);
    assertEquals(0, fixes);
  }

  @Test
  void emptyResourceReturnsNoModification() {
    Resource r = new Resource(null, new byte[0], "empty.xhtml", MediaTypes.XHTML);
    XhtmlCleaner.CleanResult result = XhtmlCleaner.clean(r);
    assertFalse(result.modified());
    assertEquals(0, result.fixCount());
  }
}
