/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.epub;

import java.util.regex.Pattern;

/** Strips dangerous HTML/XHTML elements that can execute code or embed external content. */
final class XhtmlSecurityStrip {

  private XhtmlSecurityStrip() {}

  private static final Pattern SCRIPT_BLOCK =
      Pattern.compile("<script\\b[^>]*>.*?</script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern SCRIPT_SELF_CLOSING = Pattern.compile("<script\\b[^>]*/\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern INLINE_EVENT_HANDLER_ATTR =
      Pattern.compile("\\son[a-z0-9_-]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", Pattern.CASE_INSENSITIVE);
  private static final Pattern JAVASCRIPT_URI_ATTR =
      Pattern.compile(
          "\\s(href|src)\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*'|javascript:[^\\s>]+)",
              Pattern.CASE_INSENSITIVE
      );
  private static final Pattern OBJECT_BLOCK =
      Pattern.compile("<object\\b[^>]*>.*?</object\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern EMBED_TAG = Pattern.compile("<embed\\b[^>]*/?\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern APPLET_BLOCK =
      Pattern.compile("<applet\\b[^>]*>.*?</applet\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern IFRAME_BLOCK =
      Pattern.compile("<iframe\\b[^>]*>.*?</iframe\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern FORM_BLOCK = Pattern.compile("<form\\b[^>]*>.*?</form\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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
