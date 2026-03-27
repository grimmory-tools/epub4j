package org.grimmory.comic4j.domain;

import java.nio.file.Path;
import java.util.List;
import org.grimmory.comic4j.archive.ArchiveFormat;
import org.grimmory.comic4j.image.ImageEntry;

public record ComicBook(
    Path path,
    ArchiveFormat format,
    ComicInfo comicInfo,
    List<ImageEntry> pages,
    List<String> otherEntries) {}
