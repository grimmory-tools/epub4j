/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.util.XmlCleaner;
import org.w3c.dom.Document;

/**
 * Directory-based EPUB container implementation. Handles exploded EPUB directories.
 *
 * @author Grimmory
 */
public final class DirectoryEpubContainer implements EpubContainer {

  private static final System.Logger log = System.getLogger(DirectoryEpubContainer.class.getName());

  private final Path root;
  private final Map<String, MediaType> mimeMap;
  private final Set<String> dirtyFiles;
  private String opfName;
  private String epubVersion;
  private boolean closed;

  /**
   * Open an exploded EPUB directory.
   *
   * @param root the root directory of the EPUB
   * @throws IOException if reading fails
   */
  public DirectoryEpubContainer(Path root) throws IOException {
    this.root = root;
    this.mimeMap = new LinkedHashMap<>();
    this.dirtyFiles = new HashSet<>();

    loadMimeMap();
    findOpfName();
    determineEpubVersion();
  }

  /** Check if a directory is a valid exploded EPUB. */
  public static boolean isValidEpub(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return false;
    }

    // Check for mimetype file
    Path mimetypePath = directory.resolve("mimetype");
    if (!Files.exists(mimetypePath)) {
      return false;
    }

    String mimetype = Files.readString(mimetypePath, StandardCharsets.UTF_8).trim();
    return "application/epub+zip".equals(mimetype);
  }

  private void loadMimeMap() throws IOException {
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            try {
              String name = root.relativize(file).toString().replace('\\', '/');
              mimeMap.put(name, MediaTypes.determineMediaType(name));
            } catch (Exception e) {
              log.log(
                  System.Logger.Level.DEBUG,
                  "Skipping MIME detection for " + file + ": " + e.getMessage());
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private void findOpfName() {
    Path containerPath = root.resolve("META-INF/container.xml");
    if (!Files.exists(containerPath)) {
      opfName = "OEBPS/content.opf"; // Default
      return;
    }

    try {
      String content = Files.readString(containerPath, StandardCharsets.UTF_8);
      int fullPathIndex = content.indexOf("full-path=\"");
      if (fullPathIndex >= 0) {
        int endIndex = content.indexOf('"', fullPathIndex + 11);
        if (endIndex > fullPathIndex) {
          opfName = content.substring(fullPathIndex + 11, endIndex);
          return;
        }
      }
    } catch (IOException e) {
      log.log(
          System.Logger.Level.DEBUG,
          "Failed to read container.xml, using default OPF path: " + e.getMessage());
    }

    opfName = "OEBPS/content.opf"; // Default
  }

  private void determineEpubVersion() {
    Path opfPath = root.resolve(opfName);
    if (!Files.exists(opfPath)) {
      epubVersion = "3.0"; // Default
      return;
    }

    try {
      String content = Files.readString(opfPath, StandardCharsets.UTF_8);
      if (content.contains("version=\"2.0\"") || content.contains("version='2.0'")) {
        epubVersion = "2.0";
      } else if (content.contains("version=\"3.0\"") || content.contains("version='3.0'")) {
        epubVersion = "3.0";
      } else if (content.contains("version=\"3.1\"") || content.contains("version='3.1'")) {
        epubVersion = "3.1";
      } else {
        epubVersion = "3.0"; // Default
      }
    } catch (IOException e) {
      epubVersion = "3.0"; // Default
    }
  }

  /**
   * Resolve a relative entry name to an absolute path that is guaranteed to stay under the
   * container root. Rejects both {@code ../} traversal and symlink escapes by resolving the real
   * path of the closest existing ancestor.
   */
  private Path safePath(String name) throws IOException {
    if (name == null || name.isBlank()) {
      throw new IOException("Path must reference an entry");
    }
    Path realRoot = root.toRealPath();
    Path candidate = realRoot.resolve(name).normalize();
    if (candidate.equals(realRoot)) {
      throw new IOException("Path resolves to container root: " + name);
    }
    if (!candidate.startsWith(realRoot)) {
      throw new IOException("Path escapes container root: " + name);
    }
    // Walk up to the nearest existing ancestor and verify its real path stays under root.
    Path check = candidate;
    while (check != null && !Files.exists(check)) {
      check = check.getParent();
    }
    if (check != null && !check.toRealPath().startsWith(realRoot)) {
      throw new IOException("Path escapes container root via symlink: " + name);
    }
    return candidate;
  }

  @Override
  public byte[] readBytes(String name) throws IOException {
    checkOpen();
    Path filePath = safePath(name);
    if (!Files.exists(filePath)) {
      throw new IOException("File not found: " + name);
    }
    return Files.readAllBytes(filePath);
  }

  @Override
  public void streamTo(String name, OutputStream out) throws IOException {
    checkOpen();
    Path filePath = safePath(name);
    if (!Files.exists(filePath)) {
      throw new IOException("File not found: " + name);
    }
    Files.copy(filePath, out);
  }

  @Override
  public void writeBytes(String name, byte[] data) throws IOException {
    checkOpen();
    Path filePath = safePath(name);
    String entryName = root.toRealPath().relativize(filePath).toString().replace('\\', '/');

    // Create parent directories if needed
    Path parent = filePath.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }

    Files.write(filePath, data);
    mimeMap.put(entryName, MediaTypes.determineMediaType(entryName));
    markDirty(entryName);
  }

  @Override
  public boolean exists(String name) {
    try {
      Path filePath = safePath(name);
      return Files.exists(filePath);
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public void delete(String name) throws IOException {
    checkOpen();
    Path filePath = safePath(name);
    String entryName = root.toRealPath().relativize(filePath).toString().replace('\\', '/');
    if (!Files.exists(filePath)) {
      throw new IOException("File not found: " + name);
    }
    Files.delete(filePath);
    mimeMap.remove(entryName);
    dirtyFiles.add(entryName);
  }

  @Override
  public Document parseXml(String name) throws IOException {
    checkOpen();
    byte[] data = readBytes(name);
    try {
      return org.grimmory.epub4j.util.ResourceUtil.getAsDocument(
          new org.grimmory.epub4j.domain.Resource(name, data, name, getMimeType(name)));
    } catch (Exception e) {
      throw new IOException("Failed to parse XML resource: " + name, e);
    }
  }

  @Override
  public Document parseHtml(String name) throws IOException {
    checkOpen();
    byte[] data = readBytes(name);
    // Clean XML characters before parsing
    String content = new String(data, StandardCharsets.UTF_8);
    String cleaned = XmlCleaner.cleanXmlChars(content);
    try {
      return org.grimmory.epub4j.util.ResourceUtil.getAsDocument(
          new org.grimmory.epub4j.domain.Resource(
              name, cleaned.getBytes(StandardCharsets.UTF_8), name, getMimeType(name)));
    } catch (Exception e) {
      throw new IOException("Failed to parse HTML resource: " + name, e);
    }
  }

  @Override
  public void markDirty(String name) {
    dirtyFiles.add(name);
  }

  @Override
  public MediaType getMimeType(String name) {
    return mimeMap.getOrDefault(name, MediaTypes.determineMediaType(name));
  }

  @Override
  public Map<String, MediaType> getMimeMap() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(mimeMap));
  }

  @Override
  public String getOpfName() {
    return opfName;
  }

  @Override
  public List<String> listAllFiles() {
    List<String> result = new ArrayList<>();
    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              result.add(root.relativize(file).toString().replace('\\', '/'));
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      log.log(
          System.Logger.Level.DEBUG,
          "Failed to list files in directory container: " + e.getMessage());
    }
    return result;
  }

  @Override
  public List<String> listFiles(String pattern) {
    List<String> result = new ArrayList<>();
    String regex = pattern.replace(".", "\\.").replace("*", ".*");
    for (String name : mimeMap.keySet()) {
      if (name.matches(regex)) {
        result.add(name);
      }
    }
    return result;
  }

  @Override
  public String getRootPath() {
    return root.toString();
  }

  @Override
  public void commit() {
    checkOpen();
    // For directory containers, changes are written immediately
    // No need to commit
  }

  @Override
  public boolean hasChanges() {
    return !dirtyFiles.isEmpty();
  }

  @Override
  public String getEpubVersion() {
    return epubVersion;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    mimeMap.clear();
    dirtyFiles.clear();
  }

  private void checkOpen() {
    if (closed) {
      throw new IllegalStateException("Container is closed");
    }
  }
}
