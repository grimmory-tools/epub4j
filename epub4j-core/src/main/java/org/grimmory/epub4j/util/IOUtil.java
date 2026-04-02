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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Most of the functions herein are re-implementations of the ones in apache io IOUtils.
 *
 * <p>The reason for re-implementing this is that the functions are fairly simple and using my own
 * implementation saves the inclusion of a 200Kb jar file.
 *
 * <p>This implementation uses JVM-intrinsic native-optimized methods ({@code
 * InputStream.transferTo()}, {@code readAllBytes()}, {@code readNBytes()}) for maximum throughput.
 * These methods use OS-level optimizations like {@code sendfile()} on Linux and
 * hardware-accelerated buffer copies.
 */
public class IOUtil {

  public static final int IO_COPY_BUFFER_SIZE = 1024 * 64;

  /**
   * Gets the contents of the Reader as a byte[], with the given character encoding.
   *
   * @param in
   * @param encoding
   * @return the contents of the Reader as a byte[], with the given character encoding.
   * @throws IOException
   */
  public static byte[] toByteArray(Reader in, String encoding) throws IOException {
    StringWriter out = new StringWriter();
    in.transferTo(out);
    out.flush();
    return out.toString().getBytes(encoding);
  }

  /**
   * Returns the contents of the InputStream as a byte[].
   *
   * <p>Uses {@code InputStream.readAllBytes()} which is a JVM intrinsic that avoids intermediate
   * buffer copies.
   *
   * @param in
   * @return the contents of the InputStream as a byte[]
   * @throws IOException
   */
  public static byte[] toByteArray(InputStream in) throws IOException {
    return in.readAllBytes();
  }

  /**
   * Reads data from the InputStream, using the specified buffer size.
   *
   * <p>This is meant for situations where memory is tight, since it prevents buffer expansion.
   *
   * @param in the stream to read data from
   * @param size the size of the array to create
   * @return the array, or null
   * @throws IOException
   */
  public static byte[] toByteArray(InputStream in, int size) throws IOException {

    try {
      if (size > 0) {
        return in.readNBytes(size);
      } else {
        return in.readAllBytes();
      }
    } catch (OutOfMemoryError error) {
      // Return null so it gets loaded lazily.
      return null;
    }
  }

  /**
   * if totalNrRead &lt; 0 then totalNrRead is returned, if (nrRead + totalNrRead) &lt;
   * Integer.MAX_VALUE then nrRead + totalNrRead is returned, -1 otherwise.
   *
   * @param nrRead
   * @param totalNrNread
   * @return if totalNrRead &lt; 0 then totalNrRead is returned, if (nrRead + totalNrRead) &lt;
   *     Integer.MAX_VALUE then nrRead + totalNrRead is returned, -1 otherwise.
   */
  protected static int calcNewNrReadSize(int nrRead, int totalNrNread) {
    if (totalNrNread < 0) {
      return totalNrNread;
    }
    if (totalNrNread > (Integer.MAX_VALUE - nrRead)) {
      return -1;
    } else {
      return (totalNrNread + nrRead);
    }
  }

  /**
   * Copies the contents of the InputStream to the OutputStream.
   *
   * <p>Uses {@code InputStream.transferTo()} which is a JVM intrinsic that uses OS-level
   * optimizations (e.g. {@code sendfile()} on Linux) for zero-copy transfers when possible.
   *
   * @param in
   * @param out
   * @return the nr of bytes read, or -1 if the amount &gt; Integer.MAX_VALUE
   * @throws IOException
   */
  public static int copy(InputStream in, OutputStream out) throws IOException {
    long transferred = in.transferTo(out);
    out.flush();
    return transferred > Integer.MAX_VALUE ? -1 : (int) transferred;
  }

  /**
   * Copies the contents of the Reader to the Writer.
   *
   * <p>Uses {@code Reader.transferTo()} for native-optimized character stream copying.
   *
   * @param in
   * @param out
   * @return the nr of characters read, or -1 if the amount &gt; Integer.MAX_VALUE
   * @throws IOException
   */
  public static int copy(Reader in, Writer out) throws IOException {
    long transferred = in.transferTo(out);
    out.flush();
    return transferred > Integer.MAX_VALUE ? -1 : (int) transferred;
  }
}
