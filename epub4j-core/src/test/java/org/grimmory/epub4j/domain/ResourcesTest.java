package org.grimmory.epub4j.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ResourcesTest {

  @Test
  public void testGetResourcesByMediaType1() {
    Resources resources = new Resources();
    resources.add(new Resource("foo".getBytes(), MediaTypes.XHTML));
    resources.add(new Resource("bar".getBytes(), MediaTypes.XHTML));
    assertEquals(0, resources.getResourcesByMediaType(MediaTypes.PNG).size());
    assertEquals(2, resources.getResourcesByMediaType(MediaTypes.XHTML).size());
    assertEquals(2, resources.getResourcesByMediaTypes(new MediaType[] {MediaTypes.XHTML}).size());
  }

  @Test
  public void testGetResourcesByMediaType2() {
    Resources resources = new Resources();
    resources.add(new Resource("foo".getBytes(), MediaTypes.XHTML));
    resources.add(new Resource("bar".getBytes(), MediaTypes.PNG));
    resources.add(new Resource("baz".getBytes(), MediaTypes.PNG));
    assertEquals(2, resources.getResourcesByMediaType(MediaTypes.PNG).size());
    assertEquals(1, resources.getResourcesByMediaType(MediaTypes.XHTML).size());
    assertEquals(1, resources.getResourcesByMediaTypes(new MediaType[] {MediaTypes.XHTML}).size());
    assertEquals(
        3,
        resources
            .getResourcesByMediaTypes(new MediaType[] {MediaTypes.XHTML, MediaTypes.PNG})
            .size());
    assertEquals(
        3,
        resources
            .getResourcesByMediaTypes(
                new MediaType[] {MediaTypes.CSS, MediaTypes.XHTML, MediaTypes.PNG})
            .size());
  }
}
