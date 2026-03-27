package org.grimmory.epub4j.search;

import org.grimmory.epub4j.domain.Resource;

public class SearchResult {
  private final int pagePos;
  private final String searchTerm;
  private final Resource resource;

  public SearchResult(int pagePos, String searchTerm, Resource resource) {
    super();
    this.pagePos = pagePos;
    this.searchTerm = searchTerm;
    this.resource = resource;
  }

  public int getPagePos() {
    return pagePos;
  }

  public String getSearchTerm() {
    return searchTerm;
  }

  public Resource getResource() {
    return resource;
  }
}
