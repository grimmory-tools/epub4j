package org.grimmory.epub4j.util;

import java.awt.Desktop;
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

    private static final long serialVersionUID = 1L;

    private BrowserLaunchException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
