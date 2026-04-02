package org.grimmory.epub4j.benchmarks;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.epub.EpubProcessingPolicy;
import org.grimmory.epub4j.epub.EpubReader;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for EPUB reading - sequential vs parallel resource loading.
 *
 * <p>Run with: ./gradlew :epub4j-core:jmh
 *
 * <p>To use a custom EPUB, set -Depub4j.benchmark.file=/path/to/large.epub
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(
    value = 1,
    jvmArgs = {"--enable-preview", "--enable-native-access=ALL-UNNAMED"})
public class EpubReaderBenchmark {

  private byte[] epubBytes;

  @Setup(Level.Trial)
  public void setup() throws IOException {
    String customFile = System.getProperty("epub4j.benchmark.file");
    if (customFile != null && !customFile.isBlank()) {
      epubBytes = Files.readAllBytes(Path.of(customFile));
    } else {
      // Fall back to the bundled test EPUB
      try (var in = getClass().getResourceAsStream("/testbook1.epub")) {
        if (in == null) {
          throw new IllegalStateException(
              "No test EPUB found. Set -Depub4j.benchmark.file=/path/to/file.epub");
        }
        epubBytes = in.readAllBytes();
      }
    }
  }

  @Benchmark
  public void readSequential(Blackhole bh) throws IOException {
    var reader = new EpubReader();
    Book book = reader.readEpub(new ByteArrayInputStream(epubBytes));
    bh.consume(book);
  }

  @Benchmark
  public void readParallel(Blackhole bh) throws IOException {
    var policy =
        EpubProcessingPolicy.builder()
            .parallelLoading(true)
            .maxConcurrency(Runtime.getRuntime().availableProcessors())
            .build();
    var reader = new EpubReader(null, policy);
    Book book = reader.readEpub(new ByteArrayInputStream(epubBytes));
    bh.consume(book);
  }
}
