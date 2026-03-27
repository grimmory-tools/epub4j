package org.grimmory.comic4j.xml;

import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.grimmory.comic4j.domain.*;
import org.grimmory.comic4j.error.ComicError;

/**
 * Serializes a {@link ComicInfo} to ComicInfo.xml bytes. Uses StAX for efficient, dependency-free
 * XML generation. Element ordering follows the original specification.
 */
public final class ComicInfoWriter {

  private ComicInfoWriter() {}

  /**
   * Serializes the given ComicInfo to XML bytes (UTF-8).
   *
   * @param info the ComicInfo to serialize
   * @return UTF-8 encoded XML bytes
   * @throws org.grimmory.comic4j.error.ComicException if serialization fails
   */
  public static byte[] write(ComicInfo info) {
    if (info == null) {
      throw ComicError.ERR_C013.exception("ComicInfo is null");
    }

    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
      XMLOutputFactory factory = XMLOutputFactory.newInstance();
      XMLStreamWriter writer = factory.createXMLStreamWriter(baos, "UTF-8");

      writer.writeStartDocument("UTF-8", "1.0");
      writer.writeStartElement("ComicInfo");

      // Core identification
      writeElement(writer, "Title", info.getTitle());
      writeElement(writer, "Series", info.getSeries());
      writeElement(writer, "Number", info.getNumber());
      writeIntElement(writer, "Count", info.getCount());
      writeIntElement(writer, "Volume", info.getVolume());

      // Alternate series
      writeElement(writer, "AlternateSeries", info.getAlternateSeries());
      writeElement(writer, "AlternateNumber", info.getAlternateNumber());
      writeIntElement(writer, "AlternateCount", info.getAlternateCount());

      // Description
      writeElement(writer, "Summary", info.getSummary());
      writeElement(writer, "Notes", info.getNotes());

      // Publication date
      writeIntElement(writer, "Year", info.getYear());
      writeIntElement(writer, "Month", info.getMonth());
      writeIntElement(writer, "Day", info.getDay());

      // Creators
      writeElement(writer, "Writer", info.getWriter());
      writeElement(writer, "Penciller", info.getPenciller());
      writeElement(writer, "Inker", info.getInker());
      writeElement(writer, "Colorist", info.getColorist());
      writeElement(writer, "Letterer", info.getLetterer());
      writeElement(writer, "CoverArtist", info.getCoverArtist());
      writeElement(writer, "Editor", info.getEditor());
      writeElement(writer, "Translator", info.getTranslator());

      // Publisher
      writeElement(writer, "Publisher", info.getPublisher());
      writeElement(writer, "Imprint", info.getImprint());

      // Classification
      writeElement(writer, "Genre", info.getGenre());
      writeElement(writer, "Tags", info.getTags());
      writeElement(writer, "Web", info.getWeb());
      writeIntElement(writer, "PageCount", info.getPageCount());
      writeElement(writer, "LanguageISO", info.getLanguageISO());
      writeElement(writer, "Format", info.getFormat());

      if (info.getBlackAndWhite() != null) {
        writeElement(writer, "BlackAndWhite", info.getBlackAndWhite().xmlValue());
      }
      if (info.getManga() != null) {
        writeElement(writer, "Manga", info.getManga().xmlValue());
      }

      // Content details
      writeElement(writer, "Characters", info.getCharacters());
      writeElement(writer, "Teams", info.getTeams());
      writeElement(writer, "Locations", info.getLocations());
      writeElement(writer, "ScanInformation", info.getScanInformation());

      // Story arcs
      writeElement(writer, "StoryArc", info.getStoryArc());
      writeElement(writer, "StoryArcNumber", info.getStoryArcNumber());
      writeElement(writer, "SeriesGroup", info.getSeriesGroup());

      // Age rating
      if (info.getAgeRating() != null) {
        writeElement(writer, "AgeRating", info.getAgeRating().xmlValue());
      }

      // Pages
      writePages(writer, info.getPages());

      // Ratings
      writeFloatElement(writer, "CommunityRating", info.getCommunityRating());

      // Additional fields
      writeElement(writer, "MainCharacterOrTeam", info.getMainCharacterOrTeam());
      writeElement(writer, "Review", info.getReview());
      writeElement(writer, "GTIN", info.getGtin());

      // Extensions
      writeElement(writer, "LocalizedSeries", info.getLocalizedSeries());
      writeElement(writer, "SeriesSort", info.getSeriesSort());
      writeElement(writer, "TitleSort", info.getTitleSort());

      writer.writeEndElement(); // ComicInfo
      writer.writeEndDocument();
      writer.flush();
      writer.close();

      return baos.toByteArray();
    } catch (XMLStreamException e) {
      throw ComicError.ERR_C013.exception(e);
    }
  }

  private static void writeElement(XMLStreamWriter writer, String name, String value)
      throws XMLStreamException {
    if (value == null) return;
    writer.writeStartElement(name);
    writer.writeCharacters(value);
    writer.writeEndElement();
  }

  private static void writeIntElement(XMLStreamWriter writer, String name, Integer value)
      throws XMLStreamException {
    if (value == null) return;
    writer.writeStartElement(name);
    writer.writeCharacters(Integer.toString(value));
    writer.writeEndElement();
  }

  private static void writeFloatElement(XMLStreamWriter writer, String name, Float value)
      throws XMLStreamException {
    if (value == null) return;
    writer.writeStartElement(name);
    writer.writeCharacters(Float.toString(value));
    writer.writeEndElement();
  }

  private static void writePages(XMLStreamWriter writer, List<ComicPage> pages)
      throws XMLStreamException {
    if (pages == null || pages.isEmpty()) return;

    writer.writeStartElement("Pages");
    for (ComicPage page : pages) {
      writer.writeEmptyElement("Page");
      writer.writeAttribute("Image", Integer.toString(page.imageIndex()));

      if (page.pageType() != null) {
        writer.writeAttribute("Type", page.pageType().xmlValue());
      }
      if (page.bookmark() != null) {
        writer.writeAttribute("Bookmark", page.bookmark());
      }
      if (page.imageFileSize() > 0) {
        writer.writeAttribute("ImageSize", Long.toString(page.imageFileSize()));
      }
      if (page.imageWidth() > 0) {
        writer.writeAttribute("ImageWidth", Integer.toString(page.imageWidth()));
      }
      if (page.imageHeight() > 0) {
        writer.writeAttribute("ImageHeight", Integer.toString(page.imageHeight()));
      }
      if (page.key() != null) {
        writer.writeAttribute("Key", page.key());
      }
    }
    writer.writeEndElement(); // Pages
  }
}
