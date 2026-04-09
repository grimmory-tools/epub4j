/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/** Thrown when strict EPUB validation fails before parsing begins. */
public class EpubValidationException extends IOException {

  @Serial private static final long serialVersionUID = 1L;

  private final List<EpubValidator.ValidationIssue> issues;

  public EpubValidationException(EpubValidator.DetailedValidationResult result) {
    this(result == null ? List.of() : result.errors());
  }

  public EpubValidationException(List<EpubValidator.ValidationIssue> issues) {
    super(buildMessage(issues));
    List<EpubValidator.ValidationIssue> source = issues == null ? List.of() : issues;
    this.issues = List.copyOf(source);
  }

  public List<EpubValidator.ValidationIssue> issues() {
    return issues;
  }

  private static String buildMessage(List<EpubValidator.ValidationIssue> issues) {
    List<EpubValidator.ValidationIssue> source = issues == null ? List.of() : issues;
    List<String> errorTexts = new ArrayList<>();
    for (EpubValidator.ValidationIssue issue : source) {
      if (issue.severity() == EpubValidator.ValidationSeverity.ERROR) {
        errorTexts.add(issue.toString());
      }
    }
    return "EPUB validation failed with "
        + errorTexts.size()
        + " error(s): "
        + String.join(" | ", errorTexts);
  }
}
