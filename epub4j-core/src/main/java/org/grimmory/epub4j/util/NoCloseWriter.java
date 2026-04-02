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
import java.io.Writer;

/**
 * Writer with the close() disabled. We write multiple documents to a ZipOutputStream. Some of the
 * formatters call a close() after writing their data. We don't want them to do that, so we wrap
 * regular Writers in this NoCloseWriter.
 *
 * @author paul
 */
public class NoCloseWriter extends Writer {

  private final Writer writer;

  public NoCloseWriter(Writer writer) {
    this.writer = writer;
  }

  @Override
  public void close() {}

  @Override
  public void flush() throws IOException {
    writer.flush();
  }

  @Override
  public void write(char[] cbuf, int off, int len) throws IOException {
    writer.write(cbuf, off, len);
  }
}
