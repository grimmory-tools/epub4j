package org.grimmory.epub4j.bookprocessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.grimmory.epub4j.epub.BookProcessor;
import org.grimmory.epub4j.epub.BookProcessorPipeline;

/**
 * A book processor that combines several other bookprocessors
 *
 * <p>Fixes coverpage/coverimage. Cleans up the XHTML.
 *
 * @author paul.siegmann
 */
public class DefaultBookProcessorPipeline extends BookProcessorPipeline {

  public DefaultBookProcessorPipeline() {
    super(createDefaultBookProcessors());
  }

  private static List<BookProcessor> createDefaultBookProcessors() {
    return new ArrayList<>(
        Arrays.asList(
            new SectionHrefSanityCheckBookProcessor(),
            new HtmlCleanerBookProcessor(),
            new CoverPageBookProcessor(),
            new FixIdentifierBookProcessor()));
  }
}
