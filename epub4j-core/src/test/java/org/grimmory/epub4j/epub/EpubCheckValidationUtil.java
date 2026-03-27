package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.fail;

import com.adobe.epubcheck.api.EpubCheck;
import com.adobe.epubcheck.util.DefaultReportImpl;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Utility class for validating EPUB files using EPUBCheck. */
public class EpubCheckValidationUtil {

  /**
   * Validates an EPUB file using EPUBCheck and asserts no errors are found.
   *
   * @param epubFile the EPUB file to validate
   */
  public static void assertEpubValid(File epubFile) {
    DefaultReportImpl report = new DefaultReportImpl(epubFile.getName());

    EpubCheck checker = new EpubCheck(epubFile, report);
    checker.check();

    int errorCount = report.getErrorCount();
    int fatalCount = report.getFatalErrorCount();

    if (errorCount > 0 || fatalCount > 0) {
      fail(
          "EPUBCheck found "
              + (errorCount + fatalCount)
              + " error(s). Check the report for details.");
    }
  }

  /**
   * Validates an EPUB file from a byte array using EPUBCheck.
   *
   * @param epubData the EPUB file data
   * @throws IOException if an I/O error occurs
   */
  public static void assertEpubValid(byte[] epubData) throws IOException {
    Path tempFile = Files.createTempFile("epub4j-validation-", ".epub");
    try {
      Files.write(tempFile, epubData);
      assertEpubValid(tempFile.toFile());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }
}
