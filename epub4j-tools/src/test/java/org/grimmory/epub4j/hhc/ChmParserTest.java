package org.grimmory.epub4j.hhc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.NameScope;
import org.apache.commons.vfs2.VFS;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.chm.ChmParser;
import org.grimmory.epub4j.domain.Book;
import org.junit.jupiter.api.Test;

public class ChmParserTest {

  @Test
  public void test1() {
    try {
      FileSystemManager fsManager = VFS.getManager();
      FileObject dir = fsManager.resolveFile("ram://chm_test_dir");
      dir.createFolder();
      String chm1Dir = "/chm1";
      Iterator<String> lineIter =
          IOUtils.lineIterator(
              ChmParserTest.class.getResourceAsStream(chm1Dir + "/filelist.txt"),
              Constants.CHARACTER_ENCODING);
      while (lineIter.hasNext()) {
        String line = lineIter.next();
        FileObject file = dir.resolveFile(line, NameScope.DESCENDENT);
        file.createFile();
        try (InputStream in = this.getClass().getResourceAsStream(chm1Dir + "/" + line);
            OutputStream out = file.getContent().getOutputStream()) {
          IOUtils.copy(in, out);
        }
      }

      Book chmBook = ChmParser.parseChm(dir, Constants.CHARACTER_ENCODING);
      assertEquals(45, chmBook.getResources().size());
      assertEquals(18, chmBook.getSpine().size());
      assertEquals(19, chmBook.getTableOfContents().size());
      assertEquals("chm-example", chmBook.getMetadata().getTitles().getFirst());
    } catch (Exception e) {
      fail("Unexpected exception", e);
    }
  }
}
