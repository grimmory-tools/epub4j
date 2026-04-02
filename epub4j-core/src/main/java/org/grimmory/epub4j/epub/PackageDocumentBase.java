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
package org.grimmory.epub4j.epub;

/**
 * Functionality shared by the PackageDocumentReader and the PackageDocumentWriter
 *
 * @author paul
 */
public class PackageDocumentBase {

  public static final String BOOK_ID_ID = "BookId";
  public static final String NAMESPACE_OPF = "http://www.idpf.org/2007/opf";
  public static final String NAMESPACE_DUBLIN_CORE = "http://purl.org/dc/elements/1.1/";
  public static final String NAMESPACE_DUBLIN_TERMS = "http://purl.org/dc/terms/";
  public static final String PREFIX_DUBLIN_CORE = "dc";
  public static final String PREFIX_DUBLIN_TERMS = "dcterms";
  public static final String PREFIX_OPF = "opf";
  public static final String dateFormat = "yyyy-MM-dd";

  protected interface DCTags {

    String title = "title";
    String creator = "creator";
    String subject = "subject";
    String description = "description";
    String publisher = "publisher";
    String contributor = "contributor";
    String date = "date";
    String type = "type";
    String format = "format";
    String identifier = "identifier";
    String source = "source";
    String language = "language";
    String relation = "relation";
    String coverage = "coverage";
    String rights = "rights";
  }

  protected interface DCAttributes {

    String scheme = "scheme";
    String id = "id";
  }

  protected interface OPFTags {

    String metadata = "metadata";
    String meta = "meta";
    String manifest = "manifest";
    String packageTag = "package";
    String itemref = "itemref";
    String spine = "spine";
    String reference = "reference";
    String guide = "guide";
    String item = "item";
  }

  protected interface OPFAttributes {

    String uniqueIdentifier = "unique-identifier";
    String idref = "idref";
    String name = "name";
    String content = "content";
    String type = "type";
    String href = "href";
    String linear = "linear";
    String event = "event";
    String role = "role";
    String file_as = "file-as";
    String id = "id";
    String media_type = "media-type";
    String title = "title";
    String toc = "toc";
    String version = "version";
    String scheme = "scheme";
    String property = "property";
    String properties = "properties";
    String refines = "refines";
  }

  protected interface OPFValues {

    String meta_cover = "cover";
    String reference_cover = "cover";
    String no = "no";
    String generator = "generator";
  }

  protected interface MetaProperties {

    String TITLE_TYPE = "title-type";
    String ROLE = "role";
    String BELONGS_TO_COLLECTION = "belongs-to-collection";
    String GROUP_POSITION = "group-position";
    String COLLECTION_TYPE = "collection-type";
    String SCHEMA_PAGE_COUNT = "schema:pagecount";
    String MEDIA_PAGE_COUNT = "media:pagecount";
  }

  // De facto standard meta element names used by many EPUB authoring tools;
  // they must be recognized to correctly parse series and page-count metadata
  // from real-world EPUB files.
  protected interface LegacyMeta {

    String SERIES = "calibre:series";
    String SERIES_INDEX = "calibre:series_index";
    String PAGES = "calibre:pages";
  }
}
