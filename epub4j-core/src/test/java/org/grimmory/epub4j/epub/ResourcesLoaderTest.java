package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.grimmory.epub4j.domain.*;
import org.grimmory.epub4j.util.IOUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ResourcesLoaderTest {

  private static final String encoding = "UTF-8";
  private static String testBookFilename;

  @BeforeAll
  public static void setUpClass() throws IOException {
    File testBook = File.createTempFile("testBook", ".epub");
    try (OutputStream out = new FileOutputStream(testBook);
        InputStream in = ResourcesLoaderTest.class.getResourceAsStream("/testbook1.epub")) {
      assertNotNull(in, "Missing test fixture: /testbook1.epub");
      IOUtil.copy(in, out);
    }

    ResourcesLoaderTest.testBookFilename = testBook.getAbsolutePath();
  }

  @AfterAll
  public static void tearDownClass() {
    //noinspection ResultOfMethodCallIgnored
    new File(testBookFilename).delete();
  }

  /** Loads the Resources from an InputStream */
  @Test
  public void testLoadResources_InputStream() throws IOException {
    // given
    InputStream inputStream = this.getClass().getResourceAsStream("/testbook1.epub");

    // when
    Resources resources = ResourcesLoader.loadResources(inputStream, encoding);

    // then
    verifyResources(resources);
  }

  /**
   * Loads the Resources from a zero length file.<br>
   * See <a href="https://github.com/psiegman/epublib/issues/122">Issue #122 Infinite loop</a>.
   */
  @Test
  public void testLoadResources_WithZeroLengthFile() {
    assertThrows(
        IOException.class,
        () -> {
          // given
          InputStream inputStream = this.getClass().getResourceAsStream("/zero_length_file.epub");

          // when
          ResourcesLoader.loadResources(inputStream, encoding);
        });
  }

  /**
   * Loads the Resources from a file that is not a valid zip.<br>
   * See <a href="https://github.com/psiegman/epublib/issues/122">Issue #122 Infinite loop</a>.
   */
  @Test
  public void testLoadResources_WithInvalidFile() {
    assertThrows(
        IOException.class,
        () -> {
          // given
          InputStream inputStream = this.getClass().getResourceAsStream("/not_a_zip.epub");

          // when
          ResourcesLoader.loadResources(inputStream, encoding);
        });
  }

  /** Loads the Resources from a Path */
  @Test
  public void testLoadResources_Path() throws IOException {
    // given
    Path path = Path.of(testBookFilename);

    // when
    Resources resources = ResourcesLoader.loadResources(path, encoding);

    // then
    verifyResources(resources);
  }

  /** Loads all Resources lazily from a Path */
  @Test
  public void testLoadResources_Path_lazy_all() throws IOException {
    // given
    Path path = Path.of(testBookFilename);

    // when
    Resources resources =
        ResourcesLoader.loadResources(path, encoding, Arrays.asList(MediaTypes.mediaTypes));

    // then
    verifyResources(resources);
    assertEquals(Resource.class, resources.getById("container").getClass());
    assertEquals(LazyResource.class, resources.getById("book1").getClass());
  }

  /** Loads the Resources from a Path, some of them lazily. */
  @Test
  public void testLoadResources_Path_partial_lazy() throws IOException {
    // given
    Path path = Path.of(testBookFilename);

    // when
    Resources resources =
        ResourcesLoader.loadResources(path, encoding, Collections.singletonList(MediaTypes.CSS));

    // then
    verifyResources(resources);
    assertEquals(Resource.class, resources.getById("container").getClass());
    assertEquals(LazyResource.class, resources.getById("book1").getClass());
    assertEquals(Resource.class, resources.getById("chapter1").getClass());
  }

  private void verifyResources(Resources resources) throws IOException {
    assertNotNull(resources);
    assertEquals(12, resources.getAll().size());
    List<String> allHrefs = new ArrayList<>(resources.getAllHrefs());
    Collections.sort(allHrefs);

    Resource resource;
    byte[] expectedData;

    // container
    resource = resources.getByHref(allHrefs.getFirst());
    assertEquals("container", resource.getId());
    assertEquals("META-INF/container.xml", resource.getHref());
    assertNull(resource.getMediaType());
    assertEquals(230, resource.getData().length);

    // book1.css
    resource = resources.getByHref(allHrefs.get(1));
    assertEquals("book1", resource.getId());
    assertEquals("OEBPS/book1.css", resource.getHref());
    assertEquals(MediaTypes.CSS, resource.getMediaType());
    assertEquals(74, resource.getData().length);
    expectedData = IOUtil.toByteArray(this.getClass().getResourceAsStream("/book1/book1.css"));
    assertArrayEquals(expectedData, resource.getData());

    // chapter1
    resource = resources.getByHref(allHrefs.get(2));
    assertEquals("chapter1", resource.getId());
    assertEquals("OEBPS/chapter1.html", resource.getHref());
    assertEquals(MediaTypes.XHTML, resource.getMediaType());
    assertEquals(5098, resource.getData().length);
    expectedData = IOUtil.toByteArray(this.getClass().getResourceAsStream("/book1/chapter1.html"));
    assertArrayEquals(expectedData, resource.getData());
  }
}
