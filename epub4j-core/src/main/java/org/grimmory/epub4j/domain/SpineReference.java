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

/**
 * A Section of a book. Represents both an item in the package document and a item in the index.
 *
 * @author paul
 */
public class SpineReference extends ResourceReference {

  @Serial private static final long serialVersionUID = -7921609197351510248L;
  private boolean linear = true;

  public SpineReference(Resource resource) {
    this(resource, true);
  }

  public SpineReference(Resource resource, boolean linear) {
    super(resource);
    this.linear = linear;
  }

  /**
   * Linear denotes whether the section is Primary or Auxiliary. Usually the cover page has linear
   * set to false and all the other sections have it set to true.
   *
   * <p>It's an optional property that readers may also ignore.
   *
   * <blockquote>
   *
   * primary or auxiliary is useful for Reading Systems which opt to present auxiliary content
   * differently than primary content. For example, a Reading System might opt to render auxiliary
   * content in a popup window apart from the main window which presents the primary content. (For
   * an example of the types of content that may be considered auxiliary, refer to the example below
   * and the subsequent discussion.)
   *
   * </blockquote>
   *
   * @see <a href="http://www.idpf.org/epub/20/spec/OPF_2.0.1_draft.htm#Section2.4">OPF Spine
   *     specification</a>
   * @return whether the section is Primary or Auxiliary.
   */
  public boolean isLinear() {
    return linear;
  }

  public void setLinear(boolean linear) {
    this.linear = linear;
  }
}
