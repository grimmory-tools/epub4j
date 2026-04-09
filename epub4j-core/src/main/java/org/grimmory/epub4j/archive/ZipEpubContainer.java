/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.epub4j.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.util.XmlCleaner;
import org.w3c.dom.Document;

/**
 * ZIP-based EPUB container implementation. Handles EPUB files stored as ZIP archives.
 *
 * <p>Uses random-access {@link ZipFile} for lazy entry reading instead of loading all entries into
 * memory eagerly. Only modified entries are held in a dirty cache.
 *
 * @author Grimmory
 */
public final class ZipEpubContainer implements EpubContainer {

  private final Path path;
  private final Map<String, byte[]> dirtyCache;
  private final Set<String> deletedEntries;
  private ZipFile zipFile;
  private List<String> entryNames;
  private String opfName;
  private String epubVersion;
  private boolean closed;
  private boolean createdNew;

  /**
   * Open an existing EPUB ZIP file.
   *
   * @param path the path to the EPUB file
   * @throws IOException if reading fails
   */
  public ZipEpubContainer(Path path) throws IOException {
    this(path, false);
  }

  /**
   * Create or open an EPUB ZIP file.
   *
   * @param path the path to the EPUB file
   * @param create true to create a new EPUB
   * @throws IOException if reading/creation fails
   */
  public ZipEpubContainer(Path path, boolean create) throws IOException {
    this.path = path;
    this.dirtyCache = new LinkedHashMap<>();
    this.deletedEntries = new HashSet<>();

    if (create) {
      createDefaultEpub();
    } else {
      loadFromZip();
    }
  }

  /** Check if a path is a valid EPUB file. */
  public static boolean isValidEpub(Path path) {
    if (!Files.exists(path) || Files.isDirectory(path)) {
      return false;
    }

    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(path))) {
      ZipEntry entry = zis.getNextEntry();
      if (entry == null || !"mimetype".equals(entry.getName())) {
        return false;
      }

      // Read mimetype content
      byte[] mimetypeData = zis.readAllBytes();
      String mimetype = new String(mimetypeData, StandardCharsets.UTF_8).trim();
      return "application/epub+zip".equals(mimetype);
    } catch (Exception e) {
      return false;
    }
  }

  private void createDefaultEpub() {
    this.createdNew = true;

    // Create mimetype
    dirtyCache.put("mimetype", "application/epub+zip".getBytes(StandardCharsets.UTF_8));

    // Create META-INF/container.xml
    String containerXml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>""";
    dirtyCache.put("META-INF/container.xml", containerXml.getBytes(StandardCharsets.UTF_8));

    // Create minimal OPF
    String opfXml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="uid">default-id</dc:identifier>
                    <dc:title>Untitled</dc:title>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest/>
                  <spine/>
                </package>""";
    dirtyCache.put("OEBPS/content.opf", opfXml.getBytes(StandardCharsets.UTF_8));

    this.entryNames = new ArrayList<>(dirtyCache.keySet());
    opfName = "OEBPS/content.opf";
    epubVersion = "3.0";
  }

  private void loadFromZip() throws IOException {
    this.zipFile = new ZipFile(path.toFile());
    this.entryNames = new ArrayList<>();
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (!entry.isDirectory()) {
        entryNames.add(entry.getName());
      }
    }

    // Find OPF from container.xml
    findOpfName();
    determineEpubVersion();
  }

  private void findOpfName() throws IOException {
    byte[] containerData = readBytesInternal("META-INF/container.xml");
    if (containerData == null) {
      opfName = "OEBPS/content.opf"; // Default
      return;
    }

    String content = new String(containerData, StandardCharsets.UTF_8);
    int fullPathIndex = content.indexOf("full-path=\"");
    if (fullPathIndex >= 0) {
      int endIndex = content.indexOf('"', fullPathIndex + 11);
      if (endIndex > fullPathIndex) {
        opfName = content.substring(fullPathIndex + 11, endIndex);
        return;
      }
    }

    opfName = "OEBPS/content.opf"; // Default
  }

  private void determineEpubVersion() throws IOException {
    byte[] opfData = readBytesInternal(opfName);
    if (opfData == null) {
      epubVersion = "3.0"; // Default
      return;
    }

    String content = new String(opfData, StandardCharsets.UTF_8);
    if (content.contains("version=\"2.0\"") || content.contains("version='2.0'")) {
      epubVersion = "2.0";
    } else if (content.contains("version=\"3.0\"") || content.contains("version='3.0'")) {
      epubVersion = "3.0";
    } else if (content.contains("version=\"3.1\"") || content.contains("version='3.1'")) {
      epubVersion = "3.1";
    } else {
      epubVersion = "3.0"; // Default
    }
  }

  /**
   * Read bytes from the dirty cache first, then from the ZipFile. Returns null if the entry does
   * not exist.
   */
  private byte[] readBytesInternal(String name) throws IOException {
    if (deletedEntries.contains(name)) {
      return null;
    }
    byte[] cached = dirtyCache.get(name);
    if (cached != null) {
      return cached;
    }
    if (zipFile == null) {
      return null;
    }
    ZipEntry entry = zipFile.getEntry(name);
    if (entry == null) {
      return null;
    }
    try (InputStream is = zipFile.getInputStream(entry)) {
      return is.readAllBytes();
    }
  }

  @Override
  public byte[] readBytes(String name) throws IOException {
    checkOpen();
    byte[] data = readBytesInternal(name);
    if (data == null) {
      throw new IOException("File not found: " + name);
    }
    return data.clone();
  }

  @Override
  public void streamTo(String name, OutputStream out) throws IOException {
    checkOpen();
    if (deletedEntries.contains(name)) {
      throw new IOException("File not found: " + name);
    }
    byte[] cached = dirtyCache.get(name);
    if (cached != null) {
      out.write(cached);
      return;
    }
    if (zipFile == null) {
      throw new IOException("File not found: " + name);
    }
    ZipEntry entry = zipFile.getEntry(name);
    if (entry == null) {
      throw new IOException("File not found: " + name);
    }
    try (InputStream is = zipFile.getInputStream(entry)) {
      is.transferTo(out);
    }
  }

  @Override
  public void writeBytes(String name, byte[] data) {
    checkOpen();
    dirtyCache.put(name, data.clone());
    deletedEntries.remove(name);
    if (!entryNames.contains(name)) {
      entryNames.add(name);
    }
  }

  @Override
  public boolean exists(String name) {
    if (deletedEntries.contains(name)) {
      return false;
    }
    if (dirtyCache.containsKey(name)) {
      return true;
    }
    return entryNames.contains(name);
  }

  @Override
  public void delete(String name) throws IOException {
    checkOpen();
    if (!exists(name)) {
      throw new IOException("File not found: " + name);
    }
    dirtyCache.remove(name);
    entryNames.remove(name);
    deletedEntries.add(name);
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
    // For the lazy container, markDirty loads the entry into dirtyCache if not already there
    if (!dirtyCache.containsKey(name)) {
      try {
        byte[] data = readBytesInternal(name);
        if (data != null) {
          dirtyCache.put(name, data);
        }
      } catch (IOException e) {
        // Entry will be loaded on next readBytes call; log for debugging
        System.getLogger(ZipEpubContainer.class.getName())
            .log(System.Logger.Level.DEBUG, "markDirty: failed to pre-load entry " + name, e);
      }
    }
  }

  @Override
  public MediaType getMimeType(String name) {
    return MediaTypes.determineMediaType(name);
  }

  @Override
  public Map<String, MediaType> getMimeMap() {
    Map<String, MediaType> result = new LinkedHashMap<>();
    for (String name : entryNames) {
      if (!deletedEntries.contains(name)) {
        result.put(name, MediaTypes.determineMediaType(name));
      }
    }
    return Collections.unmodifiableMap(result);
  }

  @Override
  public String getOpfName() {
    return opfName;
  }

  @Override
  public List<String> listAllFiles() {
    List<String> result = new ArrayList<>();
    for (String name : entryNames) {
      if (!deletedEntries.contains(name)) {
        result.add(name);
      }
    }
    return List.copyOf(result);
  }

  @Override
  public List<String> listFiles(String pattern) {
    List<String> result = new ArrayList<>();
    String regex = pattern.replace(".", "\\.").replace("*", ".*");
    for (String name : entryNames) {
      if (!deletedEntries.contains(name) && name.matches(regex)) {
        result.add(name);
      }
    }
    return result;
  }

  @Override
  public String getRootPath() {
    return path.toString();
  }

  @Override
  public void commit() throws IOException {
    checkOpen();
    if (dirtyCache.isEmpty() && deletedEntries.isEmpty() && !createdNew && Files.exists(path)) {
      return; // No changes to save
    }

    // Write to temporary file first
    Path tempFile = Files.createTempFile("epub-", ".epub");
    try {
      try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
        // Collect all entry names in order
        Set<String> written = new HashSet<>();

        // Write mimetype first (uncompressed, must be first)
        byte[] mimetypeData = readBytesInternal("mimetype");
        if (mimetypeData == null) {
          mimetypeData = "application/epub+zip".getBytes(StandardCharsets.UTF_8);
        }
        ZipEntry mimetypeEntry = new ZipEntry("mimetype");
        mimetypeEntry.setMethod(ZipEntry.STORED);
        mimetypeEntry.setSize(mimetypeData.length);
        mimetypeEntry.setCrc(calculateCrc(mimetypeData));
        zos.putNextEntry(mimetypeEntry);
        zos.write(mimetypeData);
        zos.closeEntry();
        written.add("mimetype");

        // Write all entries (dirty cache takes priority over zipFile)
        for (String name : entryNames) {
          if ("mimetype".equals(name) || deletedEntries.contains(name) || written.contains(name)) {
            continue;
          }

          byte[] data = readBytesInternal(name);
          if (data == null) {
            continue;
          }

          ZipEntry zipEntry = new ZipEntry(name);
          zipEntry.setMethod(ZipEntry.DEFLATED);
          zos.putNextEntry(zipEntry);
          zos.write(data);
          zos.closeEntry();
          written.add(name);
        }
      }

      // Close the old ZipFile before replacing the file
      if (zipFile != null) {
        zipFile.close();
        zipFile = null;
      }

      // Replace original file
      Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);

      // Re-open the ZipFile for continued lazy reading
      dirtyCache.clear();
      deletedEntries.clear();
      createdNew = false;
      loadFromZip();

    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Override
  public boolean hasChanges() {
    return !dirtyCache.isEmpty() || !deletedEntries.isEmpty() || createdNew;
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
    dirtyCache.clear();
    deletedEntries.clear();
    if (zipFile != null) {
      try {
        zipFile.close();
      } catch (IOException ignored) {
      }
      zipFile = null;
    }
    if (entryNames != null) {
      entryNames.clear();
    }
  }

  private void checkOpen() {
    if (closed) {
      throw new IllegalStateException("Container is closed");
    }
  }

  private static long calculateCrc(byte[] data) {
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(data);
    return crc.getValue();
  }
}
