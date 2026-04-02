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
package org.grimmory.epub4j.viewer;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLEditorKit.Parser;
import org.apache.commons.io.IOUtils;
import org.grimmory.epub4j.browsersupport.NavigationEvent;
import org.grimmory.epub4j.browsersupport.NavigationEventListener;
import org.grimmory.epub4j.browsersupport.Navigator;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;

/**
 * Creates swing HTML documents from resources.
 *
 * <p>Between books the init(Book) function needs to be called in order for images to appear
 * correctly.
 *
 * @author paul.siegmann
 */
public class HTMLDocumentFactory implements NavigationEventListener {

  private static final System.Logger log = System.getLogger(HTMLDocumentFactory.class.getName());

  // After opening the book we wait a while before we starting indexing the rest of the pages.
  // This way the book opens, everything settles down, and while the user looks at the cover page
  // the rest of the book is indexed.
  public static final int DOCUMENT_CACHE_INDEXER_WAIT_TIME = 500;

  private final ImageLoaderCache imageLoaderCache;
  private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
  private final Lock cacheReadLock = cacheLock.readLock();
  private final Lock cacheWriteLock = cacheLock.writeLock();
  private final Map<String, HTMLDocument> documentCache = new HashMap<>();
  private final MyHtmlEditorKit editorKit;

  public HTMLDocumentFactory(Navigator navigator, EditorKit editorKit) {
    this.editorKit = new MyHtmlEditorKit((HTMLEditorKit) editorKit);
    this.imageLoaderCache = new ImageLoaderCache(navigator);
    init(navigator.getBook());
    navigator.addNavigationEventListener(this);
  }

  public void init(Book book) {
    if (book == null) {
      return;
    }
    imageLoaderCache.initBook(book);
    initDocumentCache(book);
  }

  private void putDocument(Resource resource, HTMLDocument document) {
    if (document == null) {
      return;
    }
    cacheWriteLock.lock();
    try {
      documentCache.put(resource.getHref(), document);
    } finally {
      cacheWriteLock.unlock();
    }
  }

  /**
   * Get the HTMLDocument representation of the resource. If the resource is not an XHTML resource
   * then it returns null. It first tries to get the document from the cache. If the document is not
   * in the cache it creates a document from the resource and adds it to the cache.
   *
   * @param resource
   * @return the HTMLDocument representation of the resource.
   */
  public HTMLDocument getDocument(Resource resource) {
    // try to get the document from  the cache
    cacheReadLock.lock();
    HTMLDocument document;
    try {
      document = documentCache.get(resource.getHref());
    } finally {
      cacheReadLock.unlock();
    }

    // document was not in the cache, try to create it and add it to the cache
    if (document == null) {
      document = createDocument(resource);
      putDocument(resource, document);
    }

    // initialize the imageLoader for the specific document
    if (document != null) {
      imageLoaderCache.initImageLoader(document);
    }

    return document;
  }

  private static String stripHtml(String input) {
    //		result = result.replaceAll("<meta\\s+[^>]*http-equiv=\"Content-Type\"[^>]*>", "");
    return removeControlTags(input);
  }

  /**
   * Quick and dirty stripper of all &lt;?...&gt; and &lt;!...&gt; tags as these confuse the html
   * viewer.
   *
   * @param input
   * @return the input stripped of control characters
   */
  private static String removeControlTags(String input) {
    StringBuilder result = new StringBuilder();
    boolean inControlTag = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (inControlTag) {
        if (c == '>') {
          inControlTag = false;
        }
      } else if (c == '<' // look for &lt;! or &lt;?
          && i < input.length() - 1
          && (input.charAt(i + 1) == '!' || input.charAt(i + 1) == '?')) {
        inControlTag = true;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /**
   * Creates a swing HTMLDocument from the given resource.
   *
   * <p>If the resources is not of type XHTML then null is returned.
   *
   * @param resource
   * @return a swing HTMLDocument created from the given resource.
   */
  private HTMLDocument createDocument(Resource resource) {
    HTMLDocument result = null;
    if (resource.getMediaType() != MediaTypes.XHTML) {
      return null;
    }
    try {
      HTMLDocument document = (HTMLDocument) editorKit.createDefaultDocument();
      MyParserCallback parserCallback = new MyParserCallback(document.getReader(0));
      Parser parser = editorKit.getParser();
      String pageContent = IOUtils.toString(resource.getReader());
      pageContent = stripHtml(pageContent);
      document.remove(0, document.getLength());
      Reader contentReader = new StringReader(pageContent);
      parser.parse(contentReader, parserCallback, true);
      parserCallback.flush();
      result = document;
    } catch (Exception e) {
      log.log(System.Logger.Level.ERROR, e.getMessage());
    }
    return result;
  }

  private void initDocumentCache(Book book) {
    if (book == null) {
      return;
    }
    documentCache.clear();
    // Virtual thread: lightweight, no need to manage thread priority for I/O-bound indexing
    Thread.ofVirtual().name("DocumentIndexer").start(new DocumentIndexer(book));
  }

  private class DocumentIndexer implements Runnable {

    private final Book book;

    public DocumentIndexer(Book book) {
      this.book = book;
    }

    @Override
    public void run() {
      try {
        Thread.sleep(DOCUMENT_CACHE_INDEXER_WAIT_TIME);
      } catch (InterruptedException e) {
        log.log(System.Logger.Level.ERROR, e.getMessage());
      }
      addAllDocumentsToCache(book);
    }

    private void addAllDocumentsToCache(Book book) {
      for (Resource resource : book.getResources().getAll()) {
        getDocument(resource);
      }
    }
  }

  @Override
  public void navigationPerformed(NavigationEvent navigationEvent) {
    if (navigationEvent.isBookChanged() || navigationEvent.isResourceChanged()) {
      imageLoaderCache.clear();
    }
  }
}
