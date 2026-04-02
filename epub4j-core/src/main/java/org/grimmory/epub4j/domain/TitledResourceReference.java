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
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.util.StringUtil;

public class TitledResourceReference extends ResourceReference implements Serializable {

  @Serial private static final long serialVersionUID = 3918155020095190080L;
  private String fragmentId;
  private String title;

  public TitledResourceReference(Resource resource) {
    this(resource, null);
  }

  public TitledResourceReference(Resource resource, String title) {
    this(resource, title, null);
  }

  public TitledResourceReference(Resource resource, String title, String fragmentId) {
    super(resource);
    this.title = title;
    this.fragmentId = fragmentId;
  }

  public String getFragmentId() {
    return fragmentId;
  }

  public void setFragmentId(String fragmentId) {
    this.fragmentId = fragmentId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * If the fragmentId is blank it returns the resource href, otherwise it returns the resource href
   * + '#' + the fragmentId.
   *
   * @return If the fragmentId is blank it returns the resource href, otherwise it returns the
   *     resource href + '#' + the fragmentId.
   */
  public String getCompleteHref() {
    if (StringUtil.isBlank(fragmentId)) {
      return resource.getHref();
    } else {
      return resource.getHref() + Constants.FRAGMENT_SEPARATOR_CHAR + fragmentId;
    }
  }

  public void setResource(Resource resource, String fragmentId) {
    super.setResource(resource);
    this.fragmentId = fragmentId;
  }

  /** Sets the resource to the given resource and sets the fragmentId to null. */
  public void setResource(Resource resource) {
    setResource(resource, null);
  }
}
