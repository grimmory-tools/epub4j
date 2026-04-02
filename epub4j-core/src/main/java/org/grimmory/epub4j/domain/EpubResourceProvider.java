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
package org.grimmory.epub4j.domain;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author jake
 */
public class EpubResourceProvider implements LazyResourceProvider {

  private final String epubFilename;

  /**
   * @param epubFilename the file name for the epub we're created from.
   */
  public EpubResourceProvider(String epubFilename) {
    this.epubFilename = epubFilename;
  }

  @Override
  public InputStream getResourceStream(String href) throws IOException {
    ZipFile zipFile = new ZipFile(epubFilename);
    try {
      ZipEntry zipEntry = zipFile.getEntry(href);
      if (zipEntry == null) {
        zipFile.close();
        throw new IllegalStateException(
            "Cannot find entry " + href + " in epub file " + epubFilename);
      }
      return new ResourceInputStream(zipFile.getInputStream(zipEntry), zipFile);
    } catch (RuntimeException | IOException e) {
      zipFile.close();
      throw e;
    }
  }
}
