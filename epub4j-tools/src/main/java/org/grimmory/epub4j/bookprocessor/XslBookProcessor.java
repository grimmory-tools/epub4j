package org.grimmory.epub4j.bookprocessor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;
import org.grimmory.epub4j.epub.EpubProcessorSupport;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Uses the given xslFile to process all html resources of a Book.
 *
 * @author paul
 */
public class XslBookProcessor extends HtmlBookProcessor implements BookProcessor {

  private static final System.Logger log = System.getLogger(XslBookProcessor.class.getName());

  private final Transformer transformer;

  public XslBookProcessor(String xslFileName) throws TransformerConfigurationException {
    File xslFile = new File(xslFileName);
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    transformer = transformerFactory.newTransformer(new StreamSource(xslFile));
  }

  @Override
  public byte[] processHtml(Resource resource, Book book) throws IOException {
    try {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbFactory.newDocumentBuilder();
      db.setEntityResolver(EpubProcessorSupport.getEntityResolver());

      Document doc = db.parse(new InputSource(resource.getReader()));

      Source htmlSource = new DOMSource(doc.getDocumentElement());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
      Result streamResult = new StreamResult(writer);
      try {
        transformer.transform(htmlSource, streamResult);
      } catch (TransformerException e) {
        log.log(System.Logger.Level.ERROR, e.getMessage(), e);
        throw new IOException(e);
      }
      return out.toByteArray();
    } catch (Exception e) {
      throw new IOException(e);
    }
  }
}
