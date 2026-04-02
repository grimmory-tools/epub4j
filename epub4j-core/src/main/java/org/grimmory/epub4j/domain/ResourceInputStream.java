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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * A wrapper class for closing a ZipFile object when the InputStream derived from it is closed.
 *
 * @author ttopalov
 */
public class ResourceInputStream extends FilterInputStream {

  private final ZipFile zipFile;

  /**
   * Constructor.
   *
   * @param in The InputStream object.
   * @param zipFile The ZipFile object.
   */
  public ResourceInputStream(InputStream in, ZipFile zipFile) {
    super(in);
    this.zipFile = zipFile;
  }

  @Override
  public void close() throws IOException {
    super.close();
    zipFile.close();
  }
}
