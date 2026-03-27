package org.grimmory.epub4j.archive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.util.XmlCleaner;
import org.w3c.dom.Document;

/**
 * ZIP-based EPUB container implementation. Handles EPUB files stored as ZIP archives.
 *
 * @author Grimmory
 */
public final class ZipEpubContainer implements EpubContainer {

  private final Path path;
  private final Map<String, byte[]> fileCache;
  private final Map<String, MediaType> mimeMap;
  private final Set<String> dirtyFiles;
  private String opfName;
  private String epubVersion;
  private boolean closed;

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
    this.fileCache = new LinkedHashMap<>();
    this.mimeMap = new LinkedHashMap<>();
    this.dirtyFiles = new HashSet<>();

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
    // Create mimetype
    fileCache.put("mimetype", "application/epub+zip".getBytes(StandardCharsets.UTF_8));
    mimeMap.put("mimetype", MediaTypes.EPUB);

    // Create META-INF/container.xml
    String containerXml =
        """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>""";
    fileCache.put("META-INF/container.xml", containerXml.getBytes(StandardCharsets.UTF_8));
    mimeMap.put("META-INF/container.xml", MediaTypes.determineMediaType("container.xml"));

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
    fileCache.put("OEBPS/content.opf", opfXml.getBytes(StandardCharsets.UTF_8));
    mimeMap.put("OEBPS/content.opf", MediaTypes.determineMediaType("content.opf"));

    opfName = "OEBPS/content.opf";
    epubVersion = "3.0";
  }

  private void loadFromZip() throws IOException {
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(path))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          String name = entry.getName();
          byte[] data = zis.readAllBytes();
          fileCache.put(name, data);
          mimeMap.put(name, MediaTypes.determineMediaType(name));
        }
        zis.closeEntry();
      }
    }

    // Find OPF from container.xml
    findOpfName();
    determineEpubVersion();
  }

  private void findOpfName() {
    byte[] containerData = fileCache.get("META-INF/container.xml");
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

  private void determineEpubVersion() {
    byte[] opfData = fileCache.get(opfName);
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

  @Override
  public byte[] readBytes(String name) throws IOException {
    checkOpen();
    byte[] data = fileCache.get(name);
    if (data == null) {
      throw new IOException("File not found: " + name);
    }
    return data.clone();
  }

  @Override
  public void writeBytes(String name, byte[] data) {
    checkOpen();
    fileCache.put(name, data.clone());
    mimeMap.put(name, MediaTypes.determineMediaType(name));
    markDirty(name);
  }

  @Override
  public boolean exists(String name) {
    return fileCache.containsKey(name);
  }

  @Override
  public void delete(String name) throws IOException {
    checkOpen();
    if (!fileCache.containsKey(name)) {
      throw new IOException("File not found: " + name);
    }
    fileCache.remove(name);
    mimeMap.remove(name);
    dirtyFiles.add(name);
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
    return List.copyOf(fileCache.keySet());
  }

  @Override
  public List<String> listFiles(String pattern) {
    List<String> result = new ArrayList<>();
    String regex = pattern.replace(".", "\\.").replace("*", ".*");
    for (String name : fileCache.keySet()) {
      if (name.matches(regex)) {
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
    if (dirtyFiles.isEmpty() && Files.exists(path)) {
      return; // No changes to save
    }

    // Write to temporary file first
    Path tempFile = Files.createTempFile("epub-", ".epub");
    try {
      try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
        // Write mimetype first (uncompressed, must be first)
        ZipEntry mimetypeEntry = new ZipEntry("mimetype");
        mimetypeEntry.setMethod(ZipEntry.STORED);
        byte[] mimetypeData = fileCache.get("mimetype");
        if (mimetypeData == null) {
          mimetypeData = "application/epub+zip".getBytes(StandardCharsets.UTF_8);
        }
        mimetypeEntry.setSize(mimetypeData.length);
        mimetypeEntry.setCrc(calculateCrc(mimetypeData));
        zos.putNextEntry(mimetypeEntry);
        zos.write(mimetypeData);
        zos.closeEntry();

        // Write all other entries
        for (Map.Entry<String, byte[]> entry : fileCache.entrySet()) {
          String name = entry.getKey();
          if ("mimetype".equals(name)) {
            continue; // Already written
          }

          ZipEntry zipEntry = new ZipEntry(name);
          zipEntry.setMethod(ZipEntry.DEFLATED);
          zos.putNextEntry(zipEntry);
          zos.write(entry.getValue());
          zos.closeEntry();
        }
      }

      // Replace original file
      Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
      dirtyFiles.clear();

    } finally {
      Files.deleteIfExists(tempFile);
    }
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
    fileCache.clear();
    mimeMap.clear();
    dirtyFiles.clear();
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
