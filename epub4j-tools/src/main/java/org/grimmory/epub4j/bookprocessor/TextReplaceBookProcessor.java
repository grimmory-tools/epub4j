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
package org.grimmory.epub4j.bookprocessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import org.apache.commons.io.IOUtils;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;

/**
 * Cleans up regular html into xhtml. Uses HtmlCleaner to do this.
 *
 * @author paul
 */
public class TextReplaceBookProcessor extends HtmlBookProcessor implements BookProcessor {

  public byte[] processHtml(Resource resource, Book book) throws IOException {
    Reader reader = resource.getReader();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Writer writer = new OutputStreamWriter(out, Constants.CHARACTER_ENCODING);
    for (String line : IOUtils.readLines(reader)) {
      writer.write(processLine(line));
      writer.flush();
    }
    return out.toByteArray();
  }

  private static String processLine(String line) {
    return line.replace("&apos;", "'");
  }
}
