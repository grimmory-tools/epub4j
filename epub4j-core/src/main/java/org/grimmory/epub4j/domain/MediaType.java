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

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;

/**
 * MediaType is used to tell the type of content a resource is.
 *
 * <p>Examples of mediatypes are image/gif, text/css and application/xhtml+xml
 *
 * <p>All allowed mediaTypes are maintained bye the MediaTypeService.
 *
 * @author paul
 * @see MediaTypes
 */
public record MediaType(String name, String defaultExtension, Collection<String> extensions)
    implements Serializable {

  @Serial private static final long serialVersionUID = -7256091153727506788L;

  public MediaType(String name, String defaultExtension) {
    this(name, defaultExtension, new String[] {defaultExtension});
  }

  public MediaType(String name, String defaultExtension, String[] extensions) {
    this(name, defaultExtension, Arrays.asList(extensions));
  }

  public int hashCode() {
    if (name == null) {
      return 0;
    }
    return name.hashCode();
  }

  public boolean equals(Object otherMediaType) {
    if (!(otherMediaType instanceof MediaType other)) {
      return false;
    }
    if (name == null) {
      return other.name == null;
    }
    return name.equals(other.name);
  }

  public String toString() {
    return name;
  }
}
