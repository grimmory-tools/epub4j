package org.grimmory.epub4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class NamespaceMigrationCoreTest {

  private static final String LEGACY_NAMESPACE = "io.documentnode" + ".epub4j";

  @Test
  public void testPublicCoreClassesLoadFromNewNamespace() throws Exception {
    String[] classes = {
      "org.grimmory.epub4j.Constants",
      "org.grimmory.epub4j.domain.Book",
      "org.grimmory.epub4j.epub.EpubReader",
      "org.grimmory.epub4j.epub.EpubWriter",
      "org.grimmory.epub4j.util.StringUtil"
    };

    for (String className : classes) {
      assertNotNull(Class.forName(className));
    }
  }

  @Test
  public void testLegacyCoreClassNamesAreNoLongerResolvable() {
    assertClassNotFound("io.documentnode.epub4j.Constants");
    assertClassNotFound("io.documentnode.epub4j.epub.EpubReader");
    assertClassNotFound("io.documentnode.epub4j.epub.EpubWriter");
    assertClassNotFound("io.documentnode.epub4j.domain.Book");
  }

  @Test
  public void testCoreSourcesContainNoLegacyNamespaceString() throws IOException {
    assertNoLegacyNamespaceInTree(Path.of("src/main/java"));
  }

  private static void assertClassNotFound(String className) {
    try {
      Class.forName(className);
      fail("Expected class to be absent after migration: " + className);
    } catch (ClassNotFoundException expected) {
      // expected
    }
  }

  private static void assertNoLegacyNamespaceInTree(Path root) throws IOException {
    try (Stream<Path> stream = Files.walk(root)) {
      stream
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(NamespaceMigrationCoreTest::assertNoLegacyNamespaceInFile);
    }
  }

  private static void assertNoLegacyNamespaceInFile(Path file) {
    try {
      String content = Files.readString(file);
      assertFalse(content.contains(LEGACY_NAMESPACE), "Found legacy namespace in " + file);
    } catch (IOException e) {
      throw new RuntimeException("Failed reading " + file, e);
    }
  }
}
