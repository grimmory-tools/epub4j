package org.grimmory.epub4j.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Factory class for creating EpubContainer instances. Automatically detects whether the path is a
 * ZIP file or directory.
 *
 * @author Grimmory
 */
public final class EpubContainers {

  private EpubContainers() {
    // Utility class
  }

  /**
   * Open an EPUB container from a path. Automatically detects whether it's a ZIP file or directory.
   *
   * @param path the path to the EPUB
   * @return the opened container
   * @throws IOException if opening fails
   */
  public static EpubContainer open(String path) throws IOException {
    return open(Path.of(path));
  }

  /**
   * Open an EPUB container from a path.
   *
   * @param path the path to the EPUB
   * @return the opened container
   * @throws IOException if opening fails
   */
  public static EpubContainer open(Path path) throws IOException {
    if (!Files.exists(path)) {
      throw new IOException("Path does not exist: " + path);
    }

    if (Files.isDirectory(path)) {
      return new DirectoryEpubContainer(path);
    } else {
      return new ZipEpubContainer(path);
    }
  }

  /**
   * Create a new EPUB container at the specified path.
   *
   * @param path the path where the container will be created
   * @return the new container
   * @throws IOException if creation fails
   */
  public static EpubContainer create(String path) throws IOException {
    return create(Path.of(path));
  }

  /**
   * Create a new EPUB container at the specified path.
   *
   * @param path the path where the container will be created
   * @return the new container
   * @throws IOException if creation fails
   */
  public static EpubContainer create(Path path) throws IOException {
    // Create parent directories if needed
    Path parent = path.getParent();
    if (parent != null && !Files.exists(parent)) {
      Files.createDirectories(parent);
    }

    // Create as ZIP container by default
    return new ZipEpubContainer(path, true);
  }

  /**
   * Open an EPUB container from a directory (exploded EPUB).
   *
   * @param directory the directory path
   * @return the opened container
   * @throws IOException if opening fails
   */
  public static EpubContainer openDirectory(String directory) throws IOException {
    Path path = Path.of(directory);
    if (!Files.isDirectory(path)) {
      throw new IOException("Not a directory: " + directory);
    }
    return new DirectoryEpubContainer(path);
  }

  /**
   * Open an EPUB container from a ZIP file.
   *
   * @param zipPath the ZIP file path
   * @return the opened container
   * @throws IOException if opening fails
   */
  public static EpubContainer openZip(String zipPath) throws IOException {
    Path path = Path.of(zipPath);
    if (!Files.exists(path)) {
      throw new IOException("ZIP file does not exist: " + zipPath);
    }
    if (Files.isDirectory(path)) {
      throw new IOException("Path is a directory, not a ZIP file: " + zipPath);
    }
    return new ZipEpubContainer(path);
  }

  /**
   * Check if a path is a valid EPUB file.
   *
   * @param path the path to check
   * @return true if it's a valid EPUB
   */
  public static boolean isValidEpub(Path path) {
    if (!Files.exists(path) || Files.isDirectory(path)) {
      return false;
    }

    try {
      // Check if it's a ZIP with mimetype file
      return ZipEpubContainer.isValidEpub(path);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Check if a directory is a valid exploded EPUB.
   *
   * @param directory the directory to check
   * @return true if it's a valid exploded EPUB
   */
  public static boolean isValidExplodedEpub(Path directory) {
    if (!Files.isDirectory(directory)) {
      return false;
    }

    try {
      return DirectoryEpubContainer.isValidEpub(directory);
    } catch (Exception e) {
      return false;
    }
  }
}
