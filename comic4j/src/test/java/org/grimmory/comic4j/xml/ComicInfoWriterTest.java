package org.grimmory.comic4j.xml;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.grimmory.comic4j.domain.*;
import org.grimmory.comic4j.error.ComicException;
import org.junit.jupiter.api.Test;

class ComicInfoWriterTest {

  @Test
  void writeMinimal() {
    ComicInfo info = new ComicInfo();
    info.setTitle("Test Issue");
    info.setSeries("Test Series");

    byte[] xml = ComicInfoWriter.write(info);
    String xmlStr = new String(xml, StandardCharsets.UTF_8);

    assertTrue(xmlStr.contains("<Title>Test Issue</Title>"));
    assertTrue(xmlStr.contains("<Series>Test Series</Series>"));
    assertFalse(xmlStr.contains("<Volume>")); // null fields omitted
    assertFalse(xmlStr.contains("<Pages>"));
  }

  @Test
  void writeAllFields() {
    ComicInfo info = createFullComicInfo();
    byte[] xml = ComicInfoWriter.write(info);
    String xmlStr = new String(xml, StandardCharsets.UTF_8);

    assertTrue(xmlStr.contains("<Title>The Test</Title>"));
    assertTrue(xmlStr.contains("<Series>Test Series</Series>"));
    assertTrue(xmlStr.contains("<Number>1</Number>"));
    assertTrue(xmlStr.contains("<Count>4</Count>"));
    assertTrue(xmlStr.contains("<Volume>2</Volume>"));
    assertTrue(xmlStr.contains("<Writer>Writer One, Writer Two</Writer>"));
    assertTrue(xmlStr.contains("<BlackAndWhite>Yes</BlackAndWhite>"));
    assertTrue(xmlStr.contains("<Manga>YesAndRightToLeft</Manga>"));
    assertTrue(xmlStr.contains("<AgeRating>Teen</AgeRating>"));
    assertTrue(xmlStr.contains("<CommunityRating>4.0</CommunityRating>"));
    assertTrue(xmlStr.contains("<GTIN>978-0-12345-678-9</GTIN>"));
    assertTrue(xmlStr.contains("<LocalizedSeries>テスト</LocalizedSeries>"));
    assertTrue(xmlStr.contains("<Translator>Trans Lator</Translator>"));
  }

  @Test
  void writePages() {
    ComicInfo info = new ComicInfo();
    info.setTitle("Pages Test");
    info.setPages(
        List.of(
            ComicPage.builder()
                .imageIndex(0)
                .pageType(PageType.FRONT_COVER)
                .imageWidth(1280)
                .imageHeight(1920)
                .imageFileSize(245760)
                .key("k1")
                .build(),
            ComicPage.builder().imageIndex(1).pageType(PageType.STORY).build()));

    byte[] xml = ComicInfoWriter.write(info);
    String xmlStr = new String(xml, StandardCharsets.UTF_8);

    assertTrue(xmlStr.contains("<Pages>"));
    assertTrue(xmlStr.contains("Image=\"0\""));
    assertTrue(xmlStr.contains("Type=\"FrontCover\""));
    assertTrue(xmlStr.contains("ImageWidth=\"1280\""));
    assertTrue(xmlStr.contains("ImageHeight=\"1920\""));
    assertTrue(xmlStr.contains("ImageSize=\"245760\""));
    assertTrue(xmlStr.contains("Key=\"k1\""));
    assertTrue(xmlStr.contains("Image=\"1\""));
    assertTrue(xmlStr.contains("Type=\"Story\""));
    assertTrue(xmlStr.contains("</Pages>"));
  }

  @Test
  void writeNullThrows() {
    assertThrows(ComicException.class, () -> ComicInfoWriter.write(null));
  }

  @Test
  void roundTrip() {
    ComicInfo original = createFullComicInfo();
    original.setPages(
        List.of(
            ComicPage.builder()
                .imageIndex(0)
                .pageType(PageType.FRONT_COVER)
                .imageWidth(800)
                .imageHeight(1200)
                .imageFileSize(100000)
                .key("hash1")
                .build(),
            ComicPage.builder()
                .imageIndex(1)
                .pageType(PageType.STORY)
                .imageWidth(800)
                .imageHeight(1200)
                .build()));

    byte[] xml = ComicInfoWriter.write(original);
    ComicInfo restored = ComicInfoReader.read(xml);

    assertEquals(original.getTitle(), restored.getTitle());
    assertEquals(original.getSeries(), restored.getSeries());
    assertEquals(original.getNumber(), restored.getNumber());
    assertEquals(original.getCount(), restored.getCount());
    assertEquals(original.getVolume(), restored.getVolume());
    assertEquals(original.getWriter(), restored.getWriter());
    assertEquals(original.getPenciller(), restored.getPenciller());
    assertEquals(original.getPublisher(), restored.getPublisher());
    assertEquals(original.getBlackAndWhite(), restored.getBlackAndWhite());
    assertEquals(original.getManga(), restored.getManga());
    assertEquals(original.getAgeRating(), restored.getAgeRating());
    assertEquals(original.getCommunityRating(), restored.getCommunityRating(), 0.01f);
    assertEquals(original.getGtin(), restored.getGtin());
    assertEquals(original.getLocalizedSeries(), restored.getLocalizedSeries());
    assertEquals(original.getTranslator(), restored.getTranslator());

    // Pages round-trip
    assertNotNull(restored.getPages());
    assertEquals(2, restored.getPages().size());
    assertEquals(PageType.FRONT_COVER, restored.getPages().getFirst().pageType());
    assertEquals(800, restored.getPages().get(0).imageWidth());
    assertEquals("hash1", restored.getPages().get(0).key());
    assertEquals(PageType.STORY, restored.getPages().get(1).pageType());
  }

  @Test
  void xmlDeclaration() {
    ComicInfo info = new ComicInfo();
    info.setTitle("Test");
    byte[] xml = ComicInfoWriter.write(info);
    String xmlStr = new String(xml, StandardCharsets.UTF_8);
    assertTrue(xmlStr.startsWith("<?xml"));
    assertTrue(xmlStr.contains("UTF-8"));
  }

  private static ComicInfo createFullComicInfo() {
    ComicInfo info = new ComicInfo();
    info.setTitle("The Test");
    info.setSeries("Test Series");
    info.setNumber("1");
    info.setCount(4);
    info.setVolume(2);
    info.setAlternateSeries("Alt Series");
    info.setAlternateNumber("10");
    info.setAlternateCount(20);
    info.setSummary("A test summary.");
    info.setNotes("Some notes.");
    info.setReview("Great!");
    info.setYear(2024);
    info.setMonth(6);
    info.setDay(15);
    info.setWriter("Writer One, Writer Two");
    info.setPenciller("Penciller");
    info.setInker("Inker");
    info.setColorist("Colorist");
    info.setLetterer("Letterer");
    info.setCoverArtist("Cover Artist");
    info.setEditor("Editor");
    info.setTranslator("Trans Lator");
    info.setPublisher("Test Publisher");
    info.setImprint("Test Imprint");
    info.setGenre("Action, Adventure");
    info.setTags("tag1, tag2");
    info.setWeb("https://example.com");
    info.setPageCount(48);
    info.setLanguageISO("en");
    info.setFormat("TPB");
    info.setBlackAndWhite(YesNo.YES);
    info.setManga(ReadingDirection.RIGHT_TO_LEFT_MANGA);
    info.setAgeRating(AgeRating.TEEN);
    info.setCommunityRating(4.0f);
    info.setCharacters("Hero, Villain");
    info.setTeams("Heroes Inc");
    info.setLocations("City, Mountain");
    info.setMainCharacterOrTeam("Hero");
    info.setScanInformation("HQ scan");
    info.setStoryArc("The Big Story");
    info.setStoryArcNumber("1");
    info.setSeriesGroup("Universe");
    info.setGtin("978-0-12345-678-9");
    info.setLocalizedSeries("テスト");
    info.setSeriesSort("Test Series");
    info.setTitleSort("Test, The");
    return info;
  }
}
