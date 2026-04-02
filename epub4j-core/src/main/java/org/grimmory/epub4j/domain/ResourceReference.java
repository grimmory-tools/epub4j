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

public class ResourceReference implements Serializable {

  @Serial private static final long serialVersionUID = 2596967243557743048L;

  protected Resource resource;

  public ResourceReference(Resource resource) {
    this.resource = resource;
  }

  public Resource getResource() {
    return resource;
  }

  /**
   * Besides setting the resource it also sets the fragmentId to null.
   *
   * @param resource
   */
  public void setResource(Resource resource) {
    this.resource = resource;
  }

  /**
   * The id of the reference referred to.
   *
   * <p>null of the reference is null or has a null id itself.
   *
   * @return The id of the reference referred to.
   */
  public String getResourceId() {
    if (resource != null) {
      return resource.getId();
    }
    return null;
  }
}
