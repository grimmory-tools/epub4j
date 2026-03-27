package org.grimmory.epub4j.epub;

import java.io.FileOutputStream;
import java.time.ZonedDateTime;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Date;
import org.grimmory.epub4j.domain.Date.Event;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.TOCReference;

/**
 * @author jake
 */
public class Sample {

  static void main(String[] args) {
    try {
      // Create new Book
      Book book = new Book();

      // Set the title
      book.getMetadata().addTitle("EPUB4J test book 1");

      // Add an Author
      book.getMetadata().addAuthor(new Author("Joe", "Tester"));
      book.getMetadata().addAuthor(new Author("Jill", "Second"));
      book.getMetadata().addContributor(new Author("Mike", "Contrib"));
      book.getMetadata().addPublisher("Document Node 1.4");
      ZonedDateTime now = ZonedDateTime.now();
      book.getMetadata().addDate(new Date(now, Event.CREATION));
      book.getMetadata().addDate(new Date(now, Event.MODIFICATION));
      book.getMetadata().addDate(new Date(now, Event.PUBLICATION));

      // Set cover image
      book.setCoverImage(
          new Resource(Sample.class.getResourceAsStream("/book1/cover.png"), "cover.png"));

      // Set cover page
      book.setCoverPage(
          new Resource(Sample.class.getResourceAsStream("/book1/cover.html"), "cover.html"));

      // Add Chapter 1
      TOCReference chapter1 =
          book.addSection(
              "Introduction",
              new Resource(
                  Sample.class.getResourceAsStream("/book1/chapter1.html"), "chapter1.html"));
      // Add Chapter 1 Section 2 using fragmentId
      book.addSection(chapter1, "Chapter 1, Section 2", chapter1.getResource(), "ch-1-2");

      // Add css file
      book.getResources()
          .add(new Resource(Sample.class.getResourceAsStream("/book1/book1.css"), "book1.css"));

      // Add Chapter 2
      TOCReference chapter2 =
          book.addSection(
              "Second Chapter",
              new Resource(
                  Sample.class.getResourceAsStream("/book1/chapter2.html"), "chapter2.html"));

      // Add image used by Chapter 2
      book.getResources()
          .add(new Resource(Sample.class.getResourceAsStream("/book1/flowers.jpg"), "flowers.jpg"));

      // Add Chapter2, Section 1
      book.addSection(
          chapter2,
          "Chapter 2, section 1",
          new Resource(
              Sample.class.getResourceAsStream("/book1/chapter2_1.html"), "chapter2_1.html"));

      // Add Chapter 3
      book.addSection(
          "Conclusion",
          new Resource(Sample.class.getResourceAsStream("/book1/chapter3.html"), "chapter3.html"));

      // Create EpubWriter
      EpubWriter epubWriter = new EpubWriter();

      // Write the Book as Epub
      epubWriter.write(book, new FileOutputStream("testbook1.epub"));
    } catch (Exception e) {
      throw new RuntimeException("Sample EPUB creation failed", e);
    }
  }
}
