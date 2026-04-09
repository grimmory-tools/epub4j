/*
 * Originally from epub4j (https://github.com/documentnode/epub4j)
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) Paul Siegmund and epub4j contributors
 *
 * Modifications:
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.domain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serial;
import org.grimmory.epub4j.util.IOUtil;

/**
 * A Resource that loads its data only on-demand from a EPUB book file. This way larger books can
 * fit into memory and can be opened faster.
 */
public final class LazyResource extends Resource {

  @Serial private static final long serialVersionUID = 5089400472352002866L;
  private static final System.Logger log = System.getLogger(LazyResource.class.getName());

  private final LazyResourceProvider resourceProvider;
  private final long cachedSize;

  /**
   * Creates a lazy resource, when the size is unknown.
   *
   * @param resourceProvider The resource provider loads data on demand.
   * @param href The resource's href within the epub.
   */
  public LazyResource(LazyResourceProvider resourceProvider, String href) {
    this(resourceProvider, -1, href);
  }

  /**
   * Creates a Lazy resource, by not actually loading the data for this entry.
   *
   * <p>The data will be loaded on the first call to getData()
   *
   * @param resourceProvider The resource provider loads data on demand.
   * @param size The size of this resource.
   * @param href The resource's href within the epub.
   */
  public LazyResource(LazyResourceProvider resourceProvider, long size, String href) {
    super(null, null, href, MediaTypes.determineMediaType(href));
    this.resourceProvider = resourceProvider;
    this.cachedSize = size;
  }

  /**
   * Gets the contents of the Resource as an InputStream.
   *
   * @return The contents of the Resource.
   * @throws IOException
   */
  public InputStream getInputStream() throws IOException {
    if (isInitialized()) {
      return new ByteArrayInputStream(getData());
    } else {
      return resourceProvider.getResourceStream(this.originalHref);
    }
  }

  /**
   * Returns a streaming view that always reads from the provider, bypassing any cached data. This
   * avoids heap allocation when the caller only needs to stream the contents once.
   */
  @Override
  public InputStream asInputStream() throws IOException {
    return resourceProvider.getResourceStream(this.originalHref);
  }

  /**
   * Streams the resource contents directly from the provider to the output stream, without
   * materializing the full byte[] on-heap.
   */
  @Override
  public void writeTo(OutputStream out) throws IOException {
    if (isInitialized()) {
      out.write(getData());
      return;
    }
    try (InputStream in = resourceProvider.getResourceStream(this.originalHref)) {
      in.transferTo(out);
    }
  }

  /**
   * Initializes the resource by loading its data into memory.
   *
   * @throws IOException
   */
  public void initialize() throws IOException {
    getData();
  }

  /**
   * The contents of the resource as a byte[]
   *
   * <p>If this resource was lazy-loaded and the data was not yet loaded, it will be loaded into
   * memory at this point. This included opening the zip file, so expect a first load to be slow.
   *
   * @return The contents of the resource
   */
  public byte[] getData() throws IOException {

    if (data == null) {

      log.log(System.Logger.Level.DEBUG, "Initializing lazy resource: " + this.getHref());

      try (InputStream in = resourceProvider.getResourceStream(this.originalHref)) {
        byte[] readData = IOUtil.toByteArray(in, (int) this.cachedSize);
        if (readData == null) {
          throw new IOException("Could not load the contents of resource: " + this.getHref());
        }
        this.data = readData;
      }
    }

    return data;
  }

  /**
   * Tells this resource to release its cached data.
   *
   * <p>If this resource was not lazy-loaded, this is a no-op.
   */
  public void close() {
    if (this.resourceProvider != null) {
      this.data = null;
    }
  }

  /**
   * Returns if the data for this resource has been loaded into memory.
   *
   * @return true if data was loaded.
   */
  public boolean isInitialized() {
    return data != null;
  }

  /**
   * Returns the size of this resource in bytes.
   *
   * @return the size.
   */
  public long getSize() {
    if (data != null) {
      return data.length;
    }

    return cachedSize;
  }
}
