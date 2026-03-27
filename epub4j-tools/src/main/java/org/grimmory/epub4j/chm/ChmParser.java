package org.grimmory.epub4j.chm;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileType;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaType;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.domain.Resources;
import org.grimmory.epub4j.domain.TOCReference;
import org.grimmory.epub4j.domain.TableOfContents;
import org.grimmory.epub4j.util.ResourceUtil;

/**
 * Reads the files that are extracted from a windows help ('.chm') file and creates a epub4j Book
 * out of it.
 *
 * @author paul
 */
public class ChmParser {

  public static final String DEFAULT_CHM_HTML_INPUT_ENCODING = "windows-1252";
  public static final int MINIMAL_SYSTEM_TITLE_LENGTH = 4;

  public static Book parseChm(FileObject chmRootDir)
      throws XPathExpressionException, IOException, ParserConfigurationException {
    return parseChm(chmRootDir, DEFAULT_CHM_HTML_INPUT_ENCODING);
  }

  public static Book parseChm(FileObject chmRootDir, String inputHtmlEncoding)
      throws IOException, ParserConfigurationException, XPathExpressionException {
    Book result = new Book();
    result.getMetadata().addTitle(findTitle(chmRootDir));
    FileObject hhcFileObject = findHhcFileObject(chmRootDir);
    if (hhcFileObject == null) {
      throw new IllegalArgumentException(
          "No index file found in directory "
              + chmRootDir
              + ". (Looked for file ending with extension '.hhc'");
    }
    if (inputHtmlEncoding == null) {
      inputHtmlEncoding = DEFAULT_CHM_HTML_INPUT_ENCODING;
    }
    Resources resources = findResources(chmRootDir, inputHtmlEncoding);
    List<TOCReference> tocReferences;
    try (InputStream in = hhcFileObject.getContent().getInputStream()) {
      tocReferences = HHCParser.parseHhc(in, resources);
    }
    result.setTableOfContents(new TableOfContents(tocReferences));
    result.setResources(resources);
    result.generateSpineFromTableOfContents();
    return result;
  }

  /**
   * Finds in the '#SYSTEM' file the 3rd set of characters that have ascii value &gt;= 32 and &gt;=
   * 126 and is more than 3 characters long. Assumes that that is then the title of the book.
   *
   * @param chmRootDir
   * @return Finds in the '#SYSTEM' file the 3rd set of characters that have ascii value &gt;= 32
   *     and &gt;= 126 and is more than 3 characters long.
   * @throws IOException
   */
  protected static String findTitle(FileObject chmRootDir) throws IOException {
    FileObject systemFileObject = chmRootDir.resolveFile("#SYSTEM");
    StringBuilder line = new StringBuilder();
    try (InputStream in = systemFileObject.getContent().getInputStream()) {
      boolean inText = false;
      int lineCounter = 0;
      for (int c = in.read(); c >= 0; c = in.read()) {
        if (c >= 32 && c <= 126) {
          line.append((char) c);
          inText = true;
        } else {
          if (inText) {
            if (line.length() >= 3) {
              lineCounter++;
              if (lineCounter >= MINIMAL_SYSTEM_TITLE_LENGTH) {
                return line.toString();
              }
            }
            line = new StringBuilder();
          }
          inText = false;
        }
      }
    }
    return "<unknown title>";
  }

  private static FileObject findHhcFileObject(FileObject chmRootDir) throws FileSystemException {
    FileObject[] files = chmRootDir.getChildren();
    for (FileObject file : files) {
      if ("hhc".equalsIgnoreCase(file.getName().getExtension())) {
        return file;
      }
    }
    return null;
  }

  private static Resources findResources(FileObject rootDir, String inputEncoding)
      throws IOException {
    Resources result = new Resources();
    FileObject[] allFiles = rootDir.findFiles(new AllFileSelector());
    for (FileObject file : allFiles) {
      if (file.getType() == FileType.FOLDER) {
        continue;
      }
      MediaType mediaType = MediaTypes.determineMediaType(file.getName().getBaseName());
      if (mediaType == null) {
        continue;
      }
      String href = file.getName().toString().substring(rootDir.getName().toString().length() + 1);
      byte[] resourceData;
      try (InputStream in = file.getContent().getInputStream()) {
        resourceData = IOUtils.toByteArray(in);
      }
      if (mediaType == MediaTypes.XHTML
          && !Constants.CHARACTER_ENCODING.equalsIgnoreCase(inputEncoding)) {
        resourceData =
            ResourceUtil.recode(inputEncoding, Constants.CHARACTER_ENCODING, resourceData);
      }
      Resource fileResource = new Resource(null, resourceData, href, mediaType);
      result.add(fileResource);
    }
    return result;
  }
}
