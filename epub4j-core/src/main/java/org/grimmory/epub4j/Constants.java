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
package org.grimmory.epub4j;

import org.grimmory.epub4j.util.GVersion;

public interface Constants {

  String CHARACTER_ENCODING = "UTF-8";
  String DOCTYPE_XHTML =
      "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">";
  String NAMESPACE_XHTML = "http://www.w3.org/1999/xhtml";
  String EPUB4J_GENERATOR_NAME =
      "EPUB4J v" + GVersion.VERSION.replace("-SNAPSHOT", "") + "." + GVersion.GIT_REVISION;
  char FRAGMENT_SEPARATOR_CHAR = '#';
  String DEFAULT_TOC_ID = "toc";
}
