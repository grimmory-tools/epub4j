/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.error;

import java.io.Serial;

public final class ComicException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final ComicError error;

  ComicException(ComicError error, String message) {
    super(message);
    this.error = error;
  }

  ComicException(ComicError error, String message, Throwable cause) {
    super(message, cause);
    this.error = error;
  }

  public ComicError error() {
    return error;
  }
}
