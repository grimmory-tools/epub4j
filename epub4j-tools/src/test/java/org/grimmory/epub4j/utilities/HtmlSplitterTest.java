package org.grimmory.epub4j.utilities;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.events.XMLEvent;
import org.grimmory.epub4j.Constants;
import org.junit.jupiter.api.Test;

public class HtmlSplitterTest {

  @Test
  public void test1() {
    HtmlSplitter htmlSplitter = new HtmlSplitter();
    try {
      String bookResourceName = "/holmes_scandal_bohemia.html";
      Reader input =
          new InputStreamReader(
              HtmlSplitterTest.class.getResourceAsStream(bookResourceName),
              Constants.CHARACTER_ENCODING);
      int maxSize = 3000;
      List<List<XMLEvent>> result = htmlSplitter.splitHtml(input, maxSize);
      XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
      for (List<XMLEvent> xmlEvents : result) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLEventWriter writer = xmlOutputFactory.createXMLEventWriter(out);
        try {
          for (XMLEvent xmlEvent : xmlEvents) {
            writer.add(xmlEvent);
          }
        } finally {
          writer.close();
        }
        byte[] data = out.toByteArray();
        assertTrue(data.length > 0);
        assertTrue(data.length <= maxSize);
      }
    } catch (Exception e) {
      throw new AssertionError("Unexpected exception during HTML split", e);
    }
  }
}
