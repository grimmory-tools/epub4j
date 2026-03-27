package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.grimmory.epub4j.epub.FilenameMetadataExtractor.FilenameMetadata;
import org.junit.jupiter.api.Test;

class FilenameMetadataExtractorTest {

  @Test
  void authorDashTitle() {
    FilenameMetadata meta =
        FilenameMetadataExtractor.extract("Brandon Sanderson - The Way of Kings.epub");
    assertTrue(meta.hasAny());
    assertEquals("The Way of Kings", meta.title().orElse(null));
    assertEquals("Brandon Sanderson", meta.author().orElse(null));
  }

  @Test
  void authorDashTitleWithYear() {
    FilenameMetadata meta =
        FilenameMetadataExtractor.extract("Isaac Asimov - Foundation (1951).epub");
    assertEquals("Foundation", meta.title().orElse(null));
    assertEquals("Isaac Asimov", meta.author().orElse(null));
    assertEquals("1951", meta.year().orElse(null));
  }

  @Test
  void seriesVolumeTitle() {
    FilenameMetadata meta =
        FilenameMetadataExtractor.extract("Wheel of Time v03 - The Dragon Reborn.epub");
    assertEquals("The Dragon Reborn", meta.title().orElse(null));
    assertEquals("Wheel of Time", meta.series().orElse(null));
    assertEquals(3.0f, meta.seriesNumber().orElse(0f), 0.01);
  }

  @Test
  void seriesHashVolume() {
    FilenameMetadata meta = FilenameMetadataExtractor.extract("Discworld #15 - Men at Arms.epub");
    assertEquals("Men at Arms", meta.title().orElse(null));
    assertEquals("Discworld", meta.series().orElse(null));
    assertEquals(15.0f, meta.seriesNumber().orElse(0f), 0.01);
  }

  @Test
  void titleBracketAuthor() {
    FilenameMetadata meta = FilenameMetadataExtractor.extract("The Hobbit [J.R.R. Tolkien].epub");
    assertEquals("The Hobbit", meta.title().orElse(null));
    assertEquals("J.R.R. Tolkien", meta.author().orElse(null));
  }

  @Test
  void authorSeriesNumberTitle() {
    FilenameMetadata meta =
        FilenameMetadataExtractor.extract(
            "Patrick Rothfuss - Kingkiller Chronicle 2 - The Wise Man's Fear.epub");
    assertEquals("The Wise Man's Fear", meta.title().orElse(null));
    assertEquals("Patrick Rothfuss", meta.author().orElse(null));
    assertEquals("Kingkiller Chronicle", meta.series().orElse(null));
    assertEquals(2.0f, meta.seriesNumber().orElse(0f), 0.01);
  }

  @Test
  void pathBasedExtraction() {
    FilenameMetadata meta =
        FilenameMetadataExtractor.extract(
            Path.of("/books/library/Terry Pratchett - Going Postal.epub"));
    assertEquals("Going Postal", meta.title().orElse(null));
    assertEquals("Terry Pratchett", meta.author().orElse(null));
  }

  @Test
  void unrecognizedPatternReturnsEmpty() {
    FilenameMetadata meta = FilenameMetadataExtractor.extract("randomfile.epub");
    assertFalse(meta.hasAny());
  }

  @Test
  void nullInputReturnsEmpty() {
    FilenameMetadata meta = FilenameMetadataExtractor.extract((String) null);
    assertFalse(meta.hasAny());
  }

  @Test
  void blankInputReturnsEmpty() {
    FilenameMetadata meta = FilenameMetadataExtractor.extract("   ");
    assertFalse(meta.hasAny());
  }

  @Test
  void seriesWithDecimalVolume() {
    FilenameMetadata meta = FilenameMetadataExtractor.extract("Cosmere v2.5 - Secret History.epub");
    assertEquals(2.5f, meta.seriesNumber().orElse(0f), 0.01);
  }
}
