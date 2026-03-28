package org.grimmory.epub4j;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.grimmory.epub4j.bookprocessor.CoverPageBookProcessor;
import org.grimmory.epub4j.bookprocessor.DefaultBookProcessorPipeline;
import org.grimmory.epub4j.bookprocessor.XslBookProcessor;
import org.grimmory.epub4j.chm.ChmParser;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;
import org.grimmory.epub4j.epub.BookProcessorPipeline;
import org.grimmory.epub4j.epub.EpubReader;
import org.grimmory.epub4j.epub.EpubWriter;
import org.grimmory.epub4j.fileset.FilesetBookCreator;
import org.grimmory.epub4j.util.VFSUtil;

public class Fileset2Epub {

  private static final System.Logger log = System.getLogger(Fileset2Epub.class.getName());

  static void main(String[] args) throws Exception {
    String inputLocation = "";
    String outLocation = "";
    String xslFile = "";
    String coverImage = "";
    String title = "";
    List<String> authorNames = new ArrayList<>();
    String type = "";
    String isbn = "";
    String inputEncoding = Constants.CHARACTER_ENCODING;
    List<String> bookProcessorClassNames = new ArrayList<>();

    for (int i = 0; i < args.length; i++) {
      if (i + 1 >= args.length) {
        break;
      }
      if ("--in".equalsIgnoreCase(args[i])) {
        inputLocation = args[++i];
      } else if ("--out".equalsIgnoreCase(args[i])) {
        outLocation = args[++i];
      } else if ("--input-encoding".equalsIgnoreCase(args[i])) {
        inputEncoding = args[++i];
      } else if ("--xsl".equalsIgnoreCase(args[i])) {
        xslFile = args[++i];
      } else if ("--book-processor-class".equalsIgnoreCase(args[i])) {
        bookProcessorClassNames.add(args[++i]);
      } else if ("--cover-image".equalsIgnoreCase(args[i])) {
        coverImage = args[++i];
      } else if ("--author".equalsIgnoreCase(args[i])) {
        authorNames.add(args[++i]);
      } else if ("--title".equalsIgnoreCase(args[i])) {
        title = args[++i];
      } else if ("--isbn".equalsIgnoreCase(args[i])) {
        isbn = args[++i];
      } else if ("--type".equalsIgnoreCase(args[i])) {
        type = args[++i];
      }
    }
    if (StringUtils.isBlank(inputLocation) || StringUtils.isBlank(outLocation)) {
      usage();
    }
    BookProcessorPipeline epubCleaner = new DefaultBookProcessorPipeline();
    epubCleaner.addBookProcessors(createBookProcessors(bookProcessorClassNames));
    EpubWriter epubWriter = new EpubWriter(epubCleaner);
    if (!StringUtils.isBlank(xslFile)) {
      epubCleaner.addBookProcessor(new XslBookProcessor(xslFile));
    }

    if (StringUtils.isBlank(inputEncoding)) {
      inputEncoding = Constants.CHARACTER_ENCODING;
    }

    Book book;
    if ("chm".equals(type)) {
      book = ChmParser.parseChm(VFSUtil.resolveFileObject(inputLocation), inputEncoding);
    } else if ("epub".equals(type)) {
      try (InputStream in = VFSUtil.resolveInputStream(inputLocation)) {
        book = new EpubReader().readEpub(in, inputEncoding);
      }
    } else {
      book =
          FilesetBookCreator.createBookFromDirectory(
              VFSUtil.resolveFileObject(inputLocation), inputEncoding);
    }

    if (StringUtils.isNotBlank(coverImage)) {
      //			book.getResourceByHref(book.getCoverImage());
      try (InputStream in = VFSUtil.resolveInputStream(coverImage)) {
        book.setCoverImage(new Resource(in, coverImage));
      }
      epubCleaner.getBookProcessors().add(new CoverPageBookProcessor());
    }

    if (StringUtils.isNotBlank(title)) {
      List<String> titles = new ArrayList<>();
      titles.add(title);
      book.getMetadata().setTitles(titles);
    }

    if (StringUtils.isNotBlank(isbn)) {
      book.getMetadata().addIdentifier(new Identifier(Identifier.Scheme.ISBN, isbn));
    }

    initAuthors(authorNames, book);

    try (OutputStream result = resolveOutputStream(outLocation)) {
      epubWriter.write(book, result);
    }
  }

  private static OutputStream resolveOutputStream(String outLocation) throws Exception {
    try {
      return VFS.getManager().resolveFile(outLocation).getContent().getOutputStream();
    } catch (FileSystemException e) {
      return new FileOutputStream(outLocation);
    }
  }

  private static void initAuthors(List<String> authorNames, Book book) {
    if (authorNames == null || authorNames.isEmpty()) {
      return;
    }
    List<Author> authorObjects = new ArrayList<>();
    for (String authorName : authorNames) {
      String[] authorNameParts = authorName.split(",");
      Author authorObject = null;
      if (authorNameParts.length > 1) {
        authorObject = new Author(authorNameParts[1], authorNameParts[0]);
      } else if (authorNameParts.length > 0) {
        authorObject = new Author(authorNameParts[0]);
      }
      authorObjects.add(authorObject);
    }
    book.getMetadata().setAuthors(authorObjects);
  }

  private static List<BookProcessor> createBookProcessors(List<String> bookProcessorNames) {
    List<BookProcessor> result = new ArrayList<>(bookProcessorNames.size());
    for (String bookProcessorName : bookProcessorNames) {
      try {
        Class<?> processorClass = Class.forName(bookProcessorName);
        BookProcessor bookProcessor =
            (BookProcessor) processorClass.getDeclaredConstructor().newInstance();
        result.add(bookProcessor);
      } catch (ReflectiveOperationException e) {
        log.log(
            System.Logger.Level.ERROR,
            "Unable to initialize book processor " + bookProcessorName,
            e);
      }
    }
    return result;
  }

  private static void usage() {
    System.out.println(
        "usage: "
            + Fileset2Epub.class.getName()
            + "\n  --author [lastname,firstname]"
            + "\n  --cover-image [image to use as cover]"
            + "\n  --input-ecoding [text encoding]  # The encoding of the input html files. If funny characters show"
            + "\n                             # up in the result try 'iso-8859-1', 'windows-1252' or 'utf-8'"
            + "\n                             # If that doesn't work try to find an appropriate one from"
            + "\n                             # this list: http://en.wikipedia.org/wiki/Character_encoding"
            + "\n  --in [input directory]"
            + "\n  --isbn [isbn number]"
            + "\n  --out [output epub file]"
            + "\n  --title [book title]"
            + "\n  --type [input type, can be 'epub', 'chm' or empty]"
            + "\n  --xsl [html post processing file]");
    System.exit(0);
  }
}
