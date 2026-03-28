package org.grimmory.epub4j.fileset;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.bookprocessor.DefaultBookProcessorPipeline;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.grimmory.epub4j.domain.Spine;
import org.grimmory.epub4j.domain.TOCReference;
import org.grimmory.epub4j.domain.TableOfContents;
import org.grimmory.epub4j.epub.BookProcessor;
import org.grimmory.epub4j.util.ResourceUtil;
import org.grimmory.epub4j.util.VFSUtil;

/**
 * Creates a Book from a collection of html and image files.
 *
 * @author paul
 */
public class FilesetBookCreator {

  private static final Comparator<FileObject> fileComparator =
      (o1, o2) -> o1.getName().getBaseName().compareToIgnoreCase(o2.getName().getBaseName());

  private static final BookProcessor bookProcessor = new DefaultBookProcessorPipeline();

  public static Book createBookFromDirectory(File rootDirectory) throws IOException {
    return createBookFromDirectory(rootDirectory, Constants.CHARACTER_ENCODING);
  }

  public static Book createBookFromDirectory(File rootDirectory, String encoding)
      throws IOException {
    FileObject rootFileObject =
        VFS.getManager().resolveFile("file:" + rootDirectory.getCanonicalPath());
    return createBookFromDirectory(rootFileObject, encoding);
  }

  public static Book createBookFromDirectory(FileObject rootDirectory) throws IOException {
    return createBookFromDirectory(rootDirectory, Constants.CHARACTER_ENCODING);
  }

  /**
   * Recursively adds all files that are allowed to be part of an epub to the Book.
   *
   * @see org.grimmory.epub4j.domain.MediaTypes
   * @param rootDirectory
   * @return the newly created Book
   * @throws IOException
   */
  public static Book createBookFromDirectory(FileObject rootDirectory, String encoding)
      throws IOException {
    Book result = new Book();
    List<TOCReference> sections = new ArrayList<>();
    Resources resources = new Resources();
    processDirectory(rootDirectory, rootDirectory, sections, resources, encoding);
    result.setResources(resources);
    TableOfContents tableOfContents = new TableOfContents(sections);
    result.setTableOfContents(tableOfContents);
    result.setSpine(new Spine(tableOfContents));

    result = bookProcessor.processBook(result);

    return result;
  }

  private static void processDirectory(
      FileObject rootDir,
      FileObject directory,
      List<TOCReference> sections,
      Resources resources,
      String inputEncoding)
      throws IOException {
    FileObject[] files = directory.getChildren();
    Arrays.sort(files, fileComparator);
    for (FileObject file : files) {
      if (file.getType() == FileType.FOLDER) {
        processSubdirectory(rootDir, file, sections, resources, inputEncoding);
      } else if (MediaTypes.determineMediaType(file.getName().getBaseName()) == null) {
        continue;
      } else {
        Resource resource = VFSUtil.createResource(rootDir, file, inputEncoding);
        if (resource == null) {
          continue;
        }
        resources.add(resource);
        if (MediaTypes.XHTML == resource.getMediaType()) {
          TOCReference section = new TOCReference(file.getName().getBaseName(), resource);
          sections.add(section);
        }
      }
    }
  }

  private static void processSubdirectory(
      FileObject rootDir,
      FileObject file,
      List<TOCReference> sections,
      Resources resources,
      String inputEncoding)
      throws IOException {
    List<TOCReference> childTOCReferences = new ArrayList<>();
    processDirectory(rootDir, file, childTOCReferences, resources, inputEncoding);
    if (!childTOCReferences.isEmpty()) {
      String sectionName = file.getName().getBaseName();
      Resource sectionResource =
          ResourceUtil.createResource(sectionName, VFSUtil.calculateHref(rootDir, file));
      resources.add(sectionResource);
      TOCReference section = new TOCReference(sectionName, sectionResource);
      section.setChildren(childTOCReferences);
      sections.add(section);
    }
  }
}
