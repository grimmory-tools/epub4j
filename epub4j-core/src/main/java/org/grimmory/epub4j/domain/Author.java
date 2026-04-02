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
import org.grimmory.epub4j.util.StringUtil;

/**
 * Represents one of the authors of the book
 *
 * @author paul
 */
public class Author implements Serializable {

  @Serial private static final long serialVersionUID = 6663408501416574200L;

  private String firstname;
  private String lastname;
  private Relator relator = Relator.AUTHOR;

  public Author(String singleName) {
    this("", singleName);
  }

  public Author(String firstname, String lastname) {
    this.firstname = firstname;
    this.lastname = lastname;
  }

  public String getFirstname() {
    return firstname;
  }

  public void setFirstname(String firstname) {
    this.firstname = firstname;
  }

  public String getLastname() {
    return lastname;
  }

  public void setLastname(String lastname) {
    this.lastname = lastname;
  }

  public String toString() {
    return lastname + ", " + firstname;
  }

  public int hashCode() {
    return StringUtil.hashCode(firstname, lastname);
  }

  public boolean equals(Object authorObject) {
    if (!(authorObject instanceof Author other)) {
      return false;
    }
    return StringUtil.equals(firstname, other.firstname)
        && StringUtil.equals(lastname, other.lastname);
  }

  public void setRole(String code) {
    Relator result = Relator.byCode(code);
    if (result == null) {
      result = Relator.AUTHOR;
    }
    this.relator = result;
  }

  public Relator getRelator() {
    return relator;
  }

  public void setRelator(Relator relator) {
    this.relator = relator;
  }
}
