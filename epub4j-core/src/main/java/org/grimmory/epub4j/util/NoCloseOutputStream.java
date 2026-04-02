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
import java.io.OutputStream;

/**
 * OutputStream with the close() disabled. We write multiple documents to a ZipOutputStream. Some of
 * the formatters call a close() after writing their data. We don't want them to do that, so we wrap
 * regular OutputStreams in this NoCloseOutputStream.
 *
 * @author paul
 */
public class NoCloseOutputStream extends OutputStream {

  private final OutputStream outputStream;

  public NoCloseOutputStream(OutputStream outputStream) {
    this.outputStream = outputStream;
  }

  @Override
  public void write(int b) throws IOException {
    outputStream.write(b);
  }

  /** A close() that does not call it's parent's close() */
  public void close() {}
}
