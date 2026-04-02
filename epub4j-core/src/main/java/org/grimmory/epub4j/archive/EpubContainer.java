/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.archive;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.grimmory.epub4j.domain.MediaType;
import org.w3c.dom.Document;

/**
 * Abstraction for EPUB container (ZIP file or directory).
 *
 * <p>This interface provides a unified API for accessing EPUB content whether it's stored as a ZIP
 * file or an exploded directory structure.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try (EpubContainer container = EpubContainer.open(path)) {
 *     // Read OPF
 *     String opfName = container.getOpfName();
 *     Document opf = container.parseXml(opfName);
 *
 *     // List all files
 *     for (String name : container.listAllFiles()) {
 *         System.out.println(name + " -> " + container.getMimeType(name));
 *     }
 *
 *     // Read and modify content
 *     byte[] data = container.readBytes("chapter1.xhtml");
 *     // ... modify ...
 *     container.writeBytes("chapter1.xhtml", newData);
 *     container.markDirty("chapter1.xhtml");
 *
 *     // Save changes
 *     container.commit();
 * }
 * }</pre>
 *
 * @author Grimmory
 */
public sealed interface EpubContainer extends Closeable
    permits DirectoryEpubContainer, ZipEpubContainer {

  /**
   * Read bytes from a file in the container.
   *
   * @param name the file name (path within container)
   * @return the file content as bytes
   * @throws IOException if reading fails
   */
  byte[] readBytes(String name) throws IOException;

  /**
   * Write bytes to a file in the container.
   *
   * @param name the file name (path within container)
   * @param data the data to write
   * @throws IOException if writing fails
   */
  void writeBytes(String name, byte[] data) throws IOException;

  /**
   * Check if a file exists in the container.
   *
   * @param name the file name to check
   * @return true if the file exists
   */
  boolean exists(String name);

  /**
   * Delete a file from the container.
   *
   * @param name the file name to delete
   * @throws IOException if deletion fails
   */
  void delete(String name) throws IOException;

  /**
   * Parse a file as XML.
   *
   * @param name the file name
   * @return parsed XML document
   * @throws IOException if reading or parsing fails
   */
  Document parseXml(String name) throws IOException;

  /**
   * Parse a file as HTML/XHTML.
   *
   * @param name the file name
   * @return parsed HTML document
   * @throws IOException if reading or parsing fails
   */
  Document parseHtml(String name) throws IOException;

  /**
   * Mark a file as modified (dirty). Must be called after modifying files to ensure changes are
   * saved.
   *
   * @param name the file name that was modified
   */
  void markDirty(String name);

  /**
   * Get the MIME type for a file.
   *
   * @param name the file name
   * @return the MIME type, or null if unknown
   */
  MediaType getMimeType(String name);

  /**
   * Get a map of all files to their MIME types.
   *
   * @return map of file name to MIME type
   */
  Map<String, MediaType> getMimeMap();

  /**
   * Get the path to the OPF file.
   *
   * @return the OPF file name
   */
  String getOpfName();

  /**
   * Parse the OPF file as XML.
   *
   * @return parsed OPF document
   * @throws IOException if reading or parsing fails
   */
  default Document parseOpf() throws IOException {
    return parseXml(getOpfName());
  }

  /**
   * List all files in the container.
   *
   * @return list of all file names
   */
  List<String> listAllFiles();

  /**
   * List files matching a pattern.
   *
   * @param pattern the pattern to match (e.g., "*.xhtml")
   * @return list of matching file names
   */
  List<String> listFiles(String pattern);

  /**
   * List all XHTML files in the container.
   *
   * @return list of XHTML file names
   */
  default List<String> listXhtmlFiles() {
    return listFiles("*.xhtml");
  }

  /**
   * List all CSS files in the container.
   *
   * @return list of CSS file names
   */
  default List<String> listCssFiles() {
    return listFiles("*.css");
  }

  /**
   * List all image files in the container.
   *
   * @return list of image file names
   */
  default List<String> listImageFiles() {
    return listFiles("*.jpg");
  }

  /**
   * Get the root path/directory of the container.
   *
   * @return the root path
   */
  String getRootPath();

  /**
   * Commit all changes to the container. This writes all dirty files and saves the container.
   *
   * @throws IOException if saving fails
   */
  void commit() throws IOException;

  /**
   * Check if the container has any uncommitted changes.
   *
   * @return true if there are uncommitted changes
   */
  boolean hasChanges();

  /**
   * Get the EPUB version of this container.
   *
   * @return EPUB version string (e.g., "2.0", "3.0")
   */
  String getEpubVersion();

  /**
   * Factory method to open a container from a file path.
   *
   * @param path the path to the EPUB file or directory
   * @return the opened container
   * @throws IOException if opening fails
   */
  static EpubContainer open(String path) throws IOException {
    return EpubContainers.open(path);
  }

  /**
   * Factory method to create a new container.
   *
   * @param path the path where the container will be created
   * @return the new container
   * @throws IOException if creation fails
   */
  static EpubContainer create(String path) throws IOException {
    return EpubContainers.create(path);
  }
}
