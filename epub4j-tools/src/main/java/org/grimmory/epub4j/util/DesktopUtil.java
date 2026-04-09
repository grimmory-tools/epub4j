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

import java.awt.Desktop;
import java.io.Serial;
import java.net.URL;

public class DesktopUtil {

  /**
   * Open a URL in the default web browser.
   *
   * @param url a URL to open in a web browser.
   */
  public static void launchBrowser(URL url) throws BrowserLaunchException {
    if (Desktop.isDesktopSupported()) {
      try {
        Desktop.getDesktop().browse(url.toURI());
      } catch (Exception ex) {
        throw new BrowserLaunchException("Browser could not be launched for " + url, ex);
      }
    }
  }

  public static class BrowserLaunchException extends Exception {

    @Serial private static final long serialVersionUID = 1L;

    private BrowserLaunchException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
