package org.grimmory.epub4j.bookprocessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import org.apache.commons.io.IOUtils;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Cleans up regular html into xhtml. Uses HtmlCleaner to do this.
 *
 * @author paul
 */
public class TextReplaceBookProcessor extends HtmlBookProcessor implements BookProcessor {

  public byte[] processHtml(Resource resource, Book book) throws IOException {
    Reader reader = resource.getReader();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Writer writer = new OutputStreamWriter(out, Constants.CHARACTER_ENCODING);
    for (String line : IOUtils.readLines(reader)) {
      writer.write(processLine(line));
      writer.flush();
    }
    return out.toByteArray();
  }

  private static String processLine(String line) {
    return line.replace("&apos;", "'");
  }
}
