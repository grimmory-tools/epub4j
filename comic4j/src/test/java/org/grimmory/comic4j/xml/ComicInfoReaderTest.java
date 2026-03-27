package org.grimmory.comic4j.xml;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.grimmory.comic4j.domain.*;
import org.grimmory.comic4j.error.ComicException;
import org.junit.jupiter.api.Test;

class ComicInfoReaderTest {

  @Test
  void readFullComicInfo() {
    ComicInfo info = readResource("comicinfo/comicinfo-full.xml");

    // Core identification
    assertEquals("The Dark Night Returns", info.getTitle());
    assertEquals("Batman", info.getSeries());
    assertEquals("1", info.getNumber());
    assertEquals(4, info.getCount());
    assertEquals(2, info.getVolume());

    // Alternate series
    assertEquals("Detective Comics", info.getAlternateSeries());
    assertEquals("405", info.getAlternateNumber());
    assertEquals(500, info.getAlternateCount());

    // Description
    assertTrue(info.getSummary().startsWith("A retired Bruce Wayne"));
    assertNotNull(info.getNotes());
    assertEquals("A masterpiece of the genre.", info.getReview());

    // Publication date
    assertEquals(1986, info.getYear());
    assertEquals(2, info.getMonth());
    assertEquals(15, info.getDay());

    // Creators
    assertEquals("Frank Miller", info.getWriter());
    assertEquals("Frank Miller", info.getPenciller());
    assertEquals("Klaus Janson", info.getInker());
    assertEquals("Lynn Varley", info.getColorist());
    assertEquals("John Costanza", info.getLetterer());
    assertEquals("Frank Miller", info.getCoverArtist());
    assertEquals("Dennis O'Neil", info.getEditor());
    assertEquals("Jean Dupont", info.getTranslator());

    // Publisher
    assertEquals("DC Comics", info.getPublisher());
    assertEquals("Vertigo", info.getImprint());

    // Classification
    assertEquals("Superhero, Action, Drama", info.getGenre());
    assertEquals("batman, dark knight, classic", info.getTags());
    assertEquals("https://example.com/batman", info.getWeb());
    assertEquals(48, info.getPageCount());
    assertEquals("en", info.getLanguageISO());
    assertEquals("TPB", info.getFormat());
    assertEquals(YesNo.NO, info.getBlackAndWhite());
    assertEquals(ReadingDirection.LEFT_TO_RIGHT, info.getManga());
    assertEquals(AgeRating.MATURE_17_PLUS, info.getAgeRating());
    assertEquals(4.5f, info.getCommunityRating(), 0.01f);

    // Content details
    assertTrue(info.getCharacters().contains("Batman"));
    assertTrue(info.getCharacters().contains("Joker"));
    assertEquals("Justice League", info.getTeams());
    assertTrue(info.getLocations().contains("Gotham City"));
    assertEquals("Batman", info.getMainCharacterOrTeam());
    assertEquals("300 DPI, cleaned", info.getScanInformation());

    // Story arcs
    assertEquals("The Dark Knight Saga", info.getStoryArc());
    assertEquals("1", info.getStoryArcNumber());
    assertEquals("Batman Universe", info.getSeriesGroup());

    // Identifiers
    assertEquals("978-1-56389-342-7", info.getGtin());

    // Extensions
    assertEquals("バットマン", info.getLocalizedSeries());
    assertEquals("Batman", info.getSeriesSort());
    assertEquals("Dark Night Returns, The", info.getTitleSort());
  }

  @Test
  void readFullPages() {
    ComicInfo info = readResource("comicinfo/comicinfo-full.xml");
    List<ComicPage> pages = info.getPages();

    assertNotNull(pages);
    assertEquals(4, pages.size());

    ComicPage cover = pages.getFirst();
    assertEquals(0, cover.imageIndex());
    assertEquals(PageType.FRONT_COVER, cover.pageType());
    assertEquals(245760, cover.imageFileSize());
    assertEquals(1280, cover.imageWidth());
    assertEquals(1920, cover.imageHeight());
    assertEquals("abc123", cover.key());

    ComicPage story = pages.get(1);
    assertEquals(1, story.imageIndex());
    assertEquals(PageType.STORY, story.pageType());
    assertNull(story.key());

    ComicPage back = pages.get(3);
    assertEquals(PageType.BACK_COVER, back.pageType());
  }

  @Test
  void readMinimalComicInfo() {
    ComicInfo info = readResource("comicinfo/comicinfo-minimal.xml");

    assertEquals("Test Issue", info.getTitle());
    assertEquals("Test Series", info.getSeries());

    // All other fields should be null
    assertNull(info.getNumber());
    assertNull(info.getCount());
    assertNull(info.getVolume());
    assertNull(info.getWriter());
    assertTrue(info.getPages().isEmpty());
    assertNull(info.getBlackAndWhite());
    assertNull(info.getManga());
    assertNull(info.getAgeRating());
    assertNull(info.getCommunityRating());
  }

  @Test
  void readMalformedXmlThrows() {
    assertThrows(ComicException.class, () -> readResource("comicinfo/comicinfo-malformed.xml"));
  }

  @Test
  void readXxeAttackThrows() {
    ComicException ex =
        assertThrows(ComicException.class, () -> readResource("comicinfo/comicinfo-xxe.xml"));
    // Should be ERR_C012 (XXE) or ERR_C011 (malformed due to DTD rejection)
    assertNotNull(ex.error());
  }

  @Test
  void readFromByteArray() {
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <ComicInfo>
          <Title>Byte Test</Title>
          <Volume>5</Volume>
          <CommunityRating>3,5</CommunityRating>
        </ComicInfo>
        """;
    ComicInfo info = ComicInfoReader.read(xml.getBytes(StandardCharsets.UTF_8));
    assertEquals("Byte Test", info.getTitle());
    assertEquals(5, info.getVolume());
    // Verify comma decimal separator handling
    assertEquals(3.5f, info.getCommunityRating(), 0.01f);
  }

  private ComicInfo readResource(String resource) {
    InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
    assertNotNull(is, "Test resource not found: " + resource);
    return ComicInfoReader.read(is);
  }
}
