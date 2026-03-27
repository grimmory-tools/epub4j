package org.grimmory.comic4j;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.grimmory.comic4j.xml.ComicInfoReader;

/** Helper for creating test archive files programmatically. */
public final class TestArchiveHelper {

  private TestArchiveHelper() {}

  /** Creates a minimal CBZ with the given number of JPEG pages and no ComicInfo.xml. */
  public static Path createMinimalCbz(Path dir, String name, int pageCount) throws IOException {
    Path cbz = dir.resolve(name);
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbz))) {
      for (int i = 0; i < pageCount; i++) {
        String pageName = String.format("page%02d.jpg", i + 1);
        zos.putNextEntry(new ZipEntry(pageName));
        zos.write(createTestJpeg(100, 150, Color.BLUE));
        zos.closeEntry();
      }
    }
    return cbz;
  }

  /** Creates a CBZ with ComicInfo.xml and the specified number of pages. */
  public static Path createCbzWithComicInfo(
      Path dir, String name, String comicInfoXml, int pageCount) throws IOException {
    Path cbz = dir.resolve(name);
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbz))) {
      // ComicInfo.xml first
      zos.putNextEntry(new ZipEntry(ComicInfoReader.COMIC_INFO_FILENAME));
      zos.write(comicInfoXml.getBytes(StandardCharsets.UTF_8));
      zos.closeEntry();

      // Pages
      for (int i = 0; i < pageCount; i++) {
        String pageName = String.format("page%02d.jpg", i + 1);
        zos.putNextEntry(new ZipEntry(pageName));
        zos.write(createTestJpeg(100, 150, new Color(i * 50 % 256, 100, 200)));
        zos.closeEntry();
      }
    }
    return cbz;
  }

  /** Creates a CBZ with macOS resource fork files included. */
  public static Path createCbzWithMacOSFiles(Path dir, String name) throws IOException {
    Path cbz = dir.resolve(name);
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbz))) {
      // Valid pages
      zos.putNextEntry(new ZipEntry("page01.jpg"));
      zos.write(createTestJpeg(100, 150, Color.RED));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("page02.png"));
      zos.write(createTestPng(100, 150, Color.GREEN));
      zos.closeEntry();

      // macOS junk
      zos.putNextEntry(new ZipEntry("__MACOSX/._page01.jpg"));
      zos.write(new byte[] {0, 1, 2, 3});
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry(".DS_Store"));
      zos.write(new byte[] {0, 1, 2, 3});
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("Thumbs.db"));
      zos.write(new byte[] {0, 1, 2, 3});
      zos.closeEntry();
    }
    return cbz;
  }

  /** Creates a CBZ with images in subdirectories. */
  public static Path createCbzWithSubdirectories(Path dir, String name) throws IOException {
    Path cbz = dir.resolve(name);
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbz))) {
      zos.putNextEntry(new ZipEntry("chapter1/page01.jpg"));
      zos.write(createTestJpeg(100, 150, Color.RED));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("chapter1/page02.jpg"));
      zos.write(createTestJpeg(100, 150, Color.BLUE));
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("chapter2/page01.jpg"));
      zos.write(createTestJpeg(100, 150, Color.GREEN));
      zos.closeEntry();
    }
    return cbz;
  }

  /** Creates a CBZ with unsorted page names to test natural sort. */
  public static Path createCbzWithUnsortedPages(Path dir, String name) throws IOException {
    Path cbz = dir.resolve(name);
    try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbz))) {
      // Add in wrong order to test sorting
      for (String pageName :
          new String[] {"page10.jpg", "page2.jpg", "page1.jpg", "page20.jpg", "page3.jpg"}) {
        zos.putNextEntry(new ZipEntry(pageName));
        zos.write(createTestJpeg(100, 150, Color.GRAY));
        zos.closeEntry();
      }
    }
    return cbz;
  }

  /** Creates a minimal test JPEG image as bytes. */
  public static byte[] createTestJpeg(int width, int height, Color color) throws IOException {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, width, height);
    g.dispose();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", baos);
    return baos.toByteArray();
  }

  /** Creates a minimal test PNG image as bytes. */
  public static byte[] createTestPng(int width, int height, Color color) throws IOException {
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, width, height);
    g.dispose();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "png", baos);
    return baos.toByteArray();
  }

  /** A full ComicInfo XML string for testing. */
  public static final String FULL_COMIC_INFO_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <ComicInfo>
        <Title>Integration Test</Title>
        <Series>Test Series</Series>
        <Number>1</Number>
        <Count>10</Count>
        <Volume>1</Volume>
        <Writer>Test Writer</Writer>
        <Publisher>Test Publisher</Publisher>
        <LanguageISO>en</LanguageISO>
        <AgeRating>Teen</AgeRating>
        <Manga>No</Manga>
        <BlackAndWhite>No</BlackAndWhite>
        <Genre>Action, Adventure</Genre>
        <Characters>Hero, Villain</Characters>
        <Pages>
          <Page Image="0" Type="FrontCover" ImageWidth="100" ImageHeight="150"/>
          <Page Image="1" Type="Story"/>
          <Page Image="2" Type="Story"/>
        </Pages>
      </ComicInfo>
      """;
}
