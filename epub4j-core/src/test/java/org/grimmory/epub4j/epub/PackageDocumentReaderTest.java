package org.grimmory.epub4j.epub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.*;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class PackageDocumentReaderTest {

  @Test
  public void testFindCoverHref_content1() throws SAXException, IOException {
    Document packageDocument;
    packageDocument =
        EpubProcessorSupport.createDocumentBuilder()
            .parse(PackageDocumentReaderTest.class.getResourceAsStream("/opf/test1.opf"));
    Collection<String> coverHrefs = PackageDocumentReader.findCoverHrefs(packageDocument);
    assertEquals(1, coverHrefs.size());
    assertEquals("cover.html", coverHrefs.iterator().next());
  }

  @Test
  public void testFindCoverHref_URLEncoded() throws SAXException, IOException {
    Set<String> expected = new HashSet<>(Arrays.asList("cover+.png", "cover+.html"));
    Document packageDocument;
    packageDocument =
        EpubProcessorSupport.createDocumentBuilder()
            .parse(
                PackageDocumentReaderTest.class.getResourceAsStream(
                    "/opf/test_urlencoded_href.opf"));
    Collection<String> coverHrefs = PackageDocumentReader.findCoverHrefs(packageDocument);
    assertEquals(2, coverHrefs.size());
    assertEquals(expected, coverHrefs);
  }

  @Test
  public void testFindTableOfContentsResource_simple_correct_toc_id() {
    // given
    String tocResourceId = "foo";
    Resources resources = mock(Resources.class);
    Resource resource = mock(Resource.class);
    when(resources.getByIdOrHref(tocResourceId)).thenReturn(resource);

    // when
    Resource actualResult = PackageDocumentReader.findTableOfContentsResource("foo", resources);

    // then
    assertEquals(resource, actualResult);
    Mockito.verify(resources).getByIdOrHref(tocResourceId);
    Mockito.verifyNoMoreInteractions(resources);
  }

  @Test
  public void testFindTableOfContentsResource_NCX_media_resource() {
    // given
    String tocResourceId = "foo";
    Resources resources = mock(Resources.class);
    Resource resource = mock(Resource.class);
    when(resources.getByIdOrHref(tocResourceId)).thenReturn(null);
    when(resources.getByIdOrHref(Mockito.anyString())).thenReturn(null);
    when(resources.findFirstResourceByMediaType(MediaTypes.NCX)).thenReturn(resource);

    // when
    Resource actualResult = PackageDocumentReader.findTableOfContentsResource("foo", resources);

    // then
    assertEquals(resource, actualResult);
    // First tries the given tocResourceId, then conventional IDs, then falls back to media type
    Mockito.verify(resources).getByIdOrHref(tocResourceId);
    Mockito.verify(resources).findFirstResourceByMediaType(MediaTypes.NCX);
  }

  @Test
  public void testFindTableOfContentsResource_by_possible_id() {
    // given
    String tocResourceId = "foo";
    Resources resources = mock(Resources.class);
    Resource resource = mock(Resource.class);
    when(resources.getByIdOrHref(tocResourceId)).thenReturn(null);
    when(resources.getByIdOrHref("toc")).thenReturn(null);
    when(resources.getByIdOrHref("TOC")).thenReturn(null);
    when(resources.getByIdOrHref("ncx")).thenReturn(null);
    when(resources.getByIdOrHref("NCX")).thenReturn(resource);

    // when
    Resource actualResult = PackageDocumentReader.findTableOfContentsResource("foo", resources);

    // then
    assertEquals(resource, actualResult);
    Mockito.verify(resources).getByIdOrHref(tocResourceId);
    Mockito.verify(resources).getByIdOrHref("toc");
    Mockito.verify(resources).getByIdOrHref("TOC");
    Mockito.verify(resources).getByIdOrHref("ncx");
    Mockito.verify(resources).getByIdOrHref("NCX");
    // Should NOT need media type fallback since NCX id was found
    Mockito.verifyNoMoreInteractions(resources);
  }

  @Test
  public void testFindTableOfContentsResource_nothing_found() {
    // given
    String tocResourceId = "foo";
    Resources resources = mock(Resources.class);
    when(resources.getByIdOrHref(Mockito.anyString())).thenReturn(null);
    when(resources.findFirstResourceByMediaType(MediaTypes.NCX)).thenReturn(null);

    // when
    Resource actualResult = PackageDocumentReader.findTableOfContentsResource("foo", resources);

    // then
    assertNull(actualResult);
    Mockito.verify(resources).getByIdOrHref(tocResourceId);
    Mockito.verify(resources).getByIdOrHref("toc");
    Mockito.verify(resources).getByIdOrHref("TOC");
    Mockito.verify(resources).getByIdOrHref("ncx");
    Mockito.verify(resources).getByIdOrHref("NCX");
    Mockito.verify(resources).getByIdOrHref("ncxtoc");
    Mockito.verify(resources).getByIdOrHref("NCXTOC");
    Mockito.verify(resources).findFirstResourceByMediaType(MediaTypes.NCX);
    Mockito.verifyNoMoreInteractions(resources);
  }

  @Test
  public void testFixHrefs_simple_correct() {
    // given
    String packageHref = "OEBPS/content.opf";
    String resourceHref = "OEBPS/foo/bar.html";
    Resources resources = mock(Resources.class);
    Resource resource = mock(Resource.class);
    when(resources.getAll()).thenReturn(Collections.singletonList(resource));
    when(resource.getHref()).thenReturn(resourceHref);

    // when
    PackageDocumentReader.fixHrefs(packageHref, resources);

    // then
    Mockito.verify(resource).setHref("foo/bar.html");
  }

  @Test
  public void testFixHrefs_invalid_prefix() {
    // given
    String packageHref = "123456789/";
    String resourceHref = "1/2.html";
    Resources resources = mock(Resources.class);
    Resource resource = mock(Resource.class);
    when(resources.getAll()).thenReturn(Collections.singletonList(resource));
    when(resource.getHref()).thenReturn(resourceHref);

    // when
    PackageDocumentReader.fixHrefs(packageHref, resources);

    // then
    assertTrue(true);
  }
}
