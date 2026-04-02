/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Originally from epub4j (https://github.com/documentnode/epub4j)
 * Copyright (C) Paul Siegmund and epub4j contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grimmory.epub4j.util;

import java.io.IOException;
import java.io.Reader;
import java.util.Scanner;
import java.util.regex.Pattern;
import org.apache.commons.text.StringEscapeUtils;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;

/**
 * Various resource utility methods
 *
 * @author paul
 */
public class ToolsResourceUtil {

  private static final System.Logger log = System.getLogger(ToolsResourceUtil.class.getName());

  public static String getTitle(Resource resource) {
    if (resource == null) {
      return "";
    }
    if (resource.getMediaType() != MediaTypes.XHTML) {
      return resource.getHref();
    }
    String title = findTitleFromXhtml(resource);
    if (title == null) {
      title = "";
    }
    return title;
  }

  /**
   * Retrieves whatever it finds between &lt;title&gt;...&lt;/title&gt; or
   * &lt;h1-7&gt;...&lt;/h1-7&gt;. The first match is returned, even if it is a blank string. If it
   * finds nothing null is returned.
   *
   * @param resource
   * @return whatever it finds in the resource between &lt;title&gt;...&lt;/title&gt; or
   *     &lt;h1-7&gt;...&lt;/h1-7&gt;.
   */
  public static String findTitleFromXhtml(Resource resource) {
    if (resource == null) {
      return "";
    }
    if (resource.getTitle() != null) {
      return resource.getTitle();
    }
    Pattern h_tag = Pattern.compile("^h\\d\\s*", Pattern.CASE_INSENSITIVE);
    String title = null;
    try {
      try (Reader content = resource.getReader();
          Scanner scanner = new Scanner(content)) {
        scanner.useDelimiter("<");
        while (scanner.hasNext()) {
          String text = scanner.next();
          int closePos = text.indexOf('>');
          String tag = text.substring(0, closePos);
          if ("title".equalsIgnoreCase(tag) || h_tag.matcher(tag).find()) {

            title = text.substring(closePos + 1).trim();
            title = StringEscapeUtils.unescapeHtml4(title);
            break;
          }
        }
      }
    } catch (IOException e) {
      log.log(System.Logger.Level.ERROR, e.getMessage());
    }
    resource.setTitle(title);
    return title;
  }
}
