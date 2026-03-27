package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.fail;

import com.adobe.epubcheck.api.EpubCheck;
import com.adobe.epubcheck.messages.MessageId;
import com.adobe.epubcheck.util.DefaultReportImpl;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.domain.Resource;

/**
 * Reusable JUnit 5 test base class that runs EPUBCheck 5.3.0 programmatically against any {@link
 * File} and asserts zero FATAL/ERROR messages.
 *
 * <p>Subclasses can use:
 *
 * <ul>
 *   <li>{@link #assertEpubValid(File)} - assert zero errors/fatals
 *   <li>{@link #assertMessageAbsent(File, MessageId)} - assert a specific message ID is absent
 *   <li>{@link #assertMessagePresent(File, MessageId)} - assert a specific message ID is present
 *       (for negative tests)
 *   <li>{@link #writeBookToTempFile(Book, Path)} - write a Book to a temp file for validation
 *   <li>{@link #createMinimalValidBook()} - create a minimal EPUB3-valid book
 * </ul>
 */
public abstract class EpubCheckTestBase {

  /**
   * Validates an EPUB file using EPUBCheck 5.3.0 and asserts zero FATAL or ERROR messages.
   *
   * @param epubFile the EPUB file to validate
   */
  protected static void assertEpubValid(File epubFile) {
    ValidationResult result = runEpubCheck(epubFile);
    if (result.fatalCount() > 0 || result.errorCount() > 0) {
      fail(
          "EPUBCheck found "
              + (result.fatalCount() + result.errorCount())
              + " error(s) in "
              + epubFile.getName()
              + ":\n"
              + result.report());
    }
  }

  /** Validates an EPUB from a byte array. */
  protected static void assertEpubValid(byte[] epubData, Path tmpDir) throws IOException {
    Path tempFile = Files.createTempFile(tmpDir, "epub4j-test-", ".epub");
    Files.write(tempFile, epubData);
    assertEpubValid(tempFile.toFile());
  }

  /**
   * Asserts that a specific EPUBCheck message ID is NOT present in the validation output. Useful
   * for testing that a specific fix eliminated a specific violation.
   *
   * @param epubFile the EPUB file to validate
   * @param messageId the message ID that must be absent
   */
  protected static void assertMessageAbsent(File epubFile, MessageId messageId) {
    ValidationResult result = runEpubCheck(epubFile);
    String report = result.report();
    String messageCode = messageId.toString();
    if (report.contains(messageCode)) {
      fail("EPUBCheck message " + messageCode + " should be absent but was found:\n" + report);
    }
  }

  /**
   * Asserts that a specific EPUBCheck message ID IS present in the validation output. Useful for
   * negative tests that verify a violation is detected before a fix.
   *
   * @param epubFile the EPUB file to validate
   * @param messageId the message ID that must be present
   */
  protected static void assertMessagePresent(File epubFile, MessageId messageId) {
    ValidationResult result = runEpubCheck(epubFile);
    String report = result.report();
    String messageCode = messageId.toString();
    if (!report.contains(messageCode)) {
      fail("EPUBCheck message " + messageCode + " should be present but was not found:\n" + report);
    }
  }

  /** Asserts zero FATAL/ERROR messages AND zero warnings. */
  protected static void assertEpubValidNoWarnings(File epubFile) {
    ValidationResult result = runEpubCheck(epubFile);
    if (result.fatalCount() > 0 || result.errorCount() > 0 || result.warningCount() > 0) {
      fail(
          "EPUBCheck found issues in "
              + epubFile.getName()
              + " (fatal="
              + result.fatalCount()
              + ", error="
              + result.errorCount()
              + ", warning="
              + result.warningCount()
              + "):\n"
              + result.report());
    }
  }

  /** Runs EPUBCheck on the given file and returns the full result. */
  protected static ValidationResult runEpubCheck(File epubFile) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    EpubCheck checker = new EpubCheck(epubFile, pw);
    int returnCode = checker.doValidate();
    String report = sw.toString();

    // Parse counts from the DefaultReportImpl
    DefaultReportImpl detailedReport = new DefaultReportImpl(epubFile.getName());
    EpubCheck detailedChecker = new EpubCheck(epubFile, detailedReport);
    detailedChecker.doValidate();

    return new ValidationResult(
        returnCode,
        detailedReport.getFatalErrorCount(),
        detailedReport.getErrorCount(),
        detailedReport.getWarningCount(),
        report);
  }

  /**
   * Writes a Book to a temp file in the given directory.
   *
   * @param book the book to write
   * @param tmpDir the temp directory (use @TempDir)
   * @return the path to the written EPUB file
   */
  protected static Path writeBookToTempFile(Book book, Path tmpDir) throws IOException {
    Path tempFile = Files.createTempFile(tmpDir, "epub4j-test-", ".epub");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new EpubWriter().write(book, out);
    Files.write(tempFile, out.toByteArray());
    return tempFile;
  }

  /** Writes a Book to byte array. */
  protected static byte[] writeBookToByteArray(Book book) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new EpubWriter().write(book, out);
    return out.toByteArray();
  }

  /**
   * Creates a minimal Book that should produce a valid EPUB3 when written. Contains: one XHTML
   * spine item, title, author, unique identifier.
   */
  protected static Book createMinimalValidBook() {
    Book book = new Book();
    book.getMetadata().addTitle("Minimal Test Book");
    book.getMetadata().addAuthor(new Author("Test", "Author"));
    book.getMetadata()
        .addIdentifier(
            new Identifier(
                Identifier.Scheme.UUID, "urn:uuid:12345678-1234-1234-1234-123456789012"));

    String xhtml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 1</title></head>
                <body><p>Hello, EPUB3 world.</p></body>
                </html>""";
    book.addSection(
        "Chapter 1", new Resource(xhtml.getBytes(StandardCharsets.UTF_8), "chapter1.xhtml"));

    return book;
  }

  /**
   * Creates a 1×1 transparent PNG image as a byte array. Useful as a minimal cover image for
   * testing.
   */
  protected static byte[] createMinimalPng() {
    return Base64.getDecoder()
        .decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
  }

  /** Holds the result of an EPUBCheck validation run. */
  protected record ValidationResult(
      int returnCode, int fatalCount, int errorCount, int warningCount, String report) {}
}
