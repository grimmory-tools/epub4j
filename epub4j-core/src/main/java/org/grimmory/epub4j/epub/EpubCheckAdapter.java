/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import com.adobe.epubcheck.api.EPUBLocation;
import com.adobe.epubcheck.api.EpubCheck;
import com.adobe.epubcheck.messages.Message;
import com.adobe.epubcheck.messages.Severity;
import com.adobe.epubcheck.util.DefaultReportImpl;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.grimmory.epub4j.epub.EpubValidator.DetailedValidationResult;
import org.grimmory.epub4j.epub.EpubValidator.ValidationIssue;
import org.grimmory.epub4j.epub.EpubValidator.ValidationSeverity;

/**
 * Wraps the W3C epubcheck library for deep EPUB validation.
 *
 * <p>This class is loaded only when epubcheck is on the classpath. Access it through {@link
 * EpubValidator#validateFull(Path)} - never reference this class directly from code that must run
 * without epubcheck.
 */
final class EpubCheckAdapter {

  private EpubCheckAdapter() {}

  /** Runs W3C epubcheck against the given EPUB file and returns structured results. */
  static DetailedValidationResult validate(Path epubPath) throws IOException {
    return validate(epubPath.toFile());
  }

  /** Runs W3C epubcheck against the given EPUB data and returns structured results. */
  static DetailedValidationResult validate(InputStream in) throws IOException {
    // epubcheck needs a File  -  write to temp first
    Path tempFile = Files.createTempFile("epub4j-epubcheck-", ".epub");
    try {
      Files.write(tempFile, in.readAllBytes());
      return validate(tempFile.toFile());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private static DetailedValidationResult validate(File epubFile) {
    List<ValidationIssue> issues = Collections.synchronizedList(new ArrayList<>());

    // Collect messages via a custom report that captures structured data
    DefaultReportImpl report =
        new DefaultReportImpl(epubFile.getName()) {
          @Override
          public void message(Message message, EPUBLocation location, Object... args) {
            super.message(message, location, args);
            ValidationSeverity severity = mapSeverity(message.getSeverity());
            if (severity == null) {
              return;
            }

            String code = message.getID().toString();
            String text = formatMessage(message, location, args);
            issues.add(new ValidationIssue(code, severity, text));
          }
        };

    EpubCheck checker = new EpubCheck(epubFile, report);
    checker.doValidate();

    return new DetailedValidationResult(issues);
  }

  private static ValidationSeverity mapSeverity(Severity severity) {
    return switch (severity) {
      case FATAL, ERROR -> ValidationSeverity.ERROR;
      case WARNING -> ValidationSeverity.WARNING;
      // INFO/USAGE are informational, skip them
      default -> null;
    };
  }

  private static String formatMessage(Message message, EPUBLocation location, Object... args) {
    var sb = new StringBuilder();

    if (location != null && location.getPath() != null && !location.getPath().isEmpty()) {
      sb.append(location.getPath());
      if (location.getLine() > 0) {
        sb.append(':').append(location.getLine());
        if (location.getColumn() > 0) {
          sb.append(':').append(location.getColumn());
        }
      }
      sb.append("  -  ");
    }

    sb.append(message.getMessage());

    // Substitute arguments into the message template
    if (args != null && args.length > 0) {
      try {
        return sb.toString().formatted(args);
      } catch (Exception ignored) {
        // If formatting fails, return the raw message
      }
    }

    return sb.toString();
  }

  /** Returns true if epubcheck classes are available on the classpath. */
  static boolean isAvailable() {
    try {
      Class.forName(
          "com.adobe.epubcheck.api.EpubCheck", false, EpubCheckAdapter.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
