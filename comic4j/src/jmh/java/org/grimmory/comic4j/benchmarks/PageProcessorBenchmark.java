package org.grimmory.comic4j.benchmarks;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.grimmory.comic4j.image.processing.ComicProcessingOptions;
import org.grimmory.comic4j.image.processing.PageProcessor;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for comic page processing - sequential vs parallel batch.
 *
 * <p>Run with: ./gradlew :comic4j:jmh
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(
    value = 1,
    jvmArgs = {"--enable-preview", "-Djava.awt.headless=true"})
public class PageProcessorBenchmark {

  @Param({"10", "50"})
  private int pageCount;

  private List<BufferedImage> pages;
  private PageProcessor processor;

  @Setup(Level.Trial)
  public void setup() {
    var options = ComicProcessingOptions.builder().normalize(true).grayscale(true).build();
    processor = new PageProcessor(options);

    pages = new ArrayList<>(pageCount);
    for (int i = 0; i < pageCount; i++) {
      // Simulate a typical comic page (800x1200 grayscale-ish)
      var img = new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      g.setColor(new Color(240, 240, 240));
      g.fillRect(0, 0, 800, 1200);
      g.setColor(Color.BLACK);
      g.fillRect(50, 50, 700, 1100);
      g.dispose();
      pages.add(img);
    }
  }

  @Benchmark
  public void processSequential(Blackhole bh) {
    for (BufferedImage page : pages) {
      bh.consume(processor.process(page));
    }
  }

  @Benchmark
  public void processParallelBatch(Blackhole bh) {
    bh.consume(processor.processBatch(pages, Runtime.getRuntime().availableProcessors()));
  }
}
