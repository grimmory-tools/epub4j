package org.grimmory.epub4j.optimize;

import java.util.ArrayList;
import java.util.List;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.epub.BookProcessor;
import org.grimmory.epub4j.epub.ResourceDeduplicator;

/**
 * Pipeline orchestrator for EPUB optimization. Runs a sequence of optimization steps controlled by
 * an {@link OptimizationConfig}.
 *
 * <p>Steps execute in a fixed order chosen to maximize savings: unused resource removal first (so
 * later steps process fewer resources), then image compression, font subsetting, CSS optimization,
 * and finally deduplication.
 */
public class EpubOptimizer implements BookProcessor {

  private static final System.Logger log = System.getLogger(EpubOptimizer.class.getName());

  private final OptimizationConfig config;

  public EpubOptimizer(OptimizationConfig config) {
    this.config = config;
  }

  @Override
  public Book processBook(Book book) {
    List<BookProcessor> steps = buildPipeline();
    if (steps.isEmpty()) {
      return book;
    }

    log.log(
        System.Logger.Level.DEBUG, "Starting EPUB optimization with " + steps.size() + " steps");

    for (BookProcessor step : steps) {
      book = step.processBook(book);
    }

    log.log(System.Logger.Level.DEBUG, "EPUB optimization complete");
    return book;
  }

  private List<BookProcessor> buildPipeline() {
    List<BookProcessor> steps = new ArrayList<>();

    // Prune first so later steps process fewer resources
    if (config.removeUnusedResources()) {
      steps.add(new UnusedResourcePruner());
    }

    if (config.compressImages()) {
      steps.add(new ImageCompressor(config));
    }

    if (config.subsetFonts()) {
      steps.add(new FontSubsetter());
    }

    if (config.optimizeCss()) {
      steps.add(new CssOptimizer());
    }

    // Deduplication last, after content may have been modified
    if (config.deduplicateResources()) {
      steps.add(
          book -> {
            ResourceDeduplicator.deduplicate(book);
            return book;
          });
    }

    return steps;
  }

  /** Optimizes the given book using the default configuration. */
  public static Book optimize(Book book) {
    return new EpubOptimizer(OptimizationConfig.defaultConfig()).processBook(book);
  }

  /** Optimizes the given book using the provided configuration. */
  public static Book optimize(Book book, OptimizationConfig config) {
    return new EpubOptimizer(config).processBook(book);
  }
}
