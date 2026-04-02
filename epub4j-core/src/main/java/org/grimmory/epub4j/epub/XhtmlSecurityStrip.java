/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.util.regex.Pattern;

/**
 * Strips dangerous HTML/XHTML elements that can execute code or embed external content. Used by
 * both {@link BookRepair} (full repair pipeline) and {@link EpubReader} (read-time sanitization).
 */
final class XhtmlSecurityStrip {

  private XhtmlSecurityStrip() {}

  private static final Pattern SCRIPT_BLOCK =
      Pattern.compile("(?is)<script\\b[^>]*>.*?</script\\s*>");
  private static final Pattern SCRIPT_SELF_CLOSING = Pattern.compile("(?is)<script\\b[^>]*/\\s*>");
  private static final Pattern INLINE_EVENT_HANDLER_ATTR =
      Pattern.compile("(?i)\\s+on[a-z0-9_-]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
  private static final Pattern JAVASCRIPT_URI_ATTR =
      Pattern.compile(
          "(?i)\\s+(href|src)\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*'|javascript:[^\\s>]+)");
  private static final Pattern OBJECT_BLOCK =
      Pattern.compile("(?is)<object\\b[^>]*>.*?</object\\s*>");
  private static final Pattern EMBED_TAG = Pattern.compile("(?is)<embed\\b[^>]*/?\\s*>");
  private static final Pattern APPLET_BLOCK =
      Pattern.compile("(?is)<applet\\b[^>]*>.*?</applet\\s*>");
  private static final Pattern IFRAME_BLOCK =
      Pattern.compile("(?is)<iframe\\b[^>]*>.*?</iframe\\s*>");
  private static final Pattern FORM_BLOCK = Pattern.compile("(?is)<form\\b[^>]*>.*?</form\\s*>");

  /**
   * Strip all dangerous elements from XHTML content.
   *
   * @param xhtml the XHTML content
   * @return sanitized content with dangerous tags removed
   */
  static String strip(String xhtml) {
    xhtml = SCRIPT_BLOCK.matcher(xhtml).replaceAll("");
    xhtml = SCRIPT_SELF_CLOSING.matcher(xhtml).replaceAll("");
    xhtml = INLINE_EVENT_HANDLER_ATTR.matcher(xhtml).replaceAll("");
    xhtml = JAVASCRIPT_URI_ATTR.matcher(xhtml).replaceAll("");
    xhtml = OBJECT_BLOCK.matcher(xhtml).replaceAll("");
    xhtml = EMBED_TAG.matcher(xhtml).replaceAll("");
    xhtml = APPLET_BLOCK.matcher(xhtml).replaceAll("");
    xhtml = IFRAME_BLOCK.matcher(xhtml).replaceAll("");
    xhtml = FORM_BLOCK.matcher(xhtml).replaceAll("");
    return xhtml;
  }
}
