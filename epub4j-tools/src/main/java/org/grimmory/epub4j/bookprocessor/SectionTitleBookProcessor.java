package org.grimmory.epub4j.bookprocessor;

import java.io.IOException;
import java.util.List;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang3.StringUtils;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.TOCReference;
import org.grimmory.epub4j.epub.BookProcessor;
import org.xml.sax.InputSource;

public class SectionTitleBookProcessor implements BookProcessor {

  private static final System.Logger log =
      System.getLogger(SectionTitleBookProcessor.class.getName());

  @Override
  public Book processBook(Book book) {
    XPath xpath = createXPathExpression();
    processSections(book.getTableOfContents().getTocReferences(), xpath);
    return book;
  }

  private static void processSections(List<TOCReference> tocReferences, XPath xpath) {
    for (TOCReference tocReference : tocReferences) {
      if (!StringUtils.isBlank(tocReference.getTitle())) {
        continue;
      }
      try {
        String title = getTitle(tocReference, xpath);
        tocReference.setTitle(title);
      } catch (XPathExpressionException | IOException e) {
        log.log(
            System.Logger.Level.ERROR,
            "Failed to extract section title for "
                + tocReference.getResourceId()
                + ": "
                + e.getMessage());
      }
    }
  }

  private static String getTitle(TOCReference tocReference, XPath xpath)
      throws IOException, XPathExpressionException {
    Resource resource = tocReference.getResource();
    if (resource == null) {
      return null;
    }
    InputSource inputSource = new InputSource(resource.getInputStream());
    return xpath.evaluate("/html/head/title", inputSource);
  }

  private static XPath createXPathExpression() {
    return XPathFactory.newInstance().newXPath();
  }
}
