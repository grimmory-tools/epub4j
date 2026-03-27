package org.grimmory.epub4j.viewer;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.apache.commons.lang3.StringUtils;
import org.grimmory.epub4j.browsersupport.NavigationEvent;
import org.grimmory.epub4j.browsersupport.NavigationEventListener;
import org.grimmory.epub4j.browsersupport.Navigator;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Metadata;
import org.grimmory.epub4j.domain.Resource;

public class MetadataPane extends JPanel implements NavigationEventListener {

  private static final System.Logger log = System.getLogger(MetadataPane.class.getName());

  @Serial private static final long serialVersionUID = -2810193923996466948L;
  private final JScrollPane scrollPane;

  public MetadataPane(Navigator navigator) {
    super(new GridLayout(1, 0));
    this.scrollPane = (JScrollPane) add(new JScrollPane());
    navigator.addNavigationEventListener(this);
    initBook(navigator.getBook());
  }

  private void initBook(Book book) {
    if (book == null) {
      return;
    }
    JTable table = new JTable(createTableData(book.getMetadata()), new String[] {"", ""});
    table.setEnabled(false);
    table.setFillsViewportHeight(true);
    JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
    contentPanel.add(table, BorderLayout.CENTER);
    setCoverImage(contentPanel, book);

    scrollPane.getViewport().removeAll();
    scrollPane.getViewport().add(contentPanel);
  }

  private static void setCoverImage(JPanel contentPanel, Book book) {
    if (book == null) {
      return;
    }
    Resource coverImageResource = book.getCoverImage();
    if (coverImageResource == null) {
      return;
    }
    try (InputStream inputStream = coverImageResource.getInputStream()) {
      Image image = ImageIO.read(inputStream);
      if (image == null) {
        log.log(System.Logger.Level.ERROR, "Unable to load cover image from book");
        return;
      }
      image = image.getScaledInstance(200, -1, Image.SCALE_SMOOTH);
      JLabel label = new JLabel(new ImageIcon(image));
      //			label.setSize(100, 100);
      contentPanel.add(label, BorderLayout.NORTH);
    } catch (IOException e) {
      log.log(System.Logger.Level.ERROR, "Unable to load cover image from book", e);
    }
  }

  private static Object[][] createTableData(Metadata metadata) {
    List<String[]> result = new ArrayList<>();
    addStrings(metadata.getIdentifiers(), "Identifier", result);
    addStrings(metadata.getTitles(), "Title", result);
    addStrings(metadata.getAuthors(), "Author", result);
    result.add(new String[] {"Language", metadata.getLanguage()});
    addStrings(metadata.getContributors(), "Contributor", result);
    addStrings(metadata.getDescriptions(), "Description", result);
    addStrings(metadata.getPublishers(), "Publisher", result);
    addStrings(metadata.getDates(), "Date", result);
    addStrings(metadata.getSubjects(), "Subject", result);
    addStrings(metadata.getTypes(), "Type", result);
    addStrings(metadata.getRights(), "Rights", result);
    result.add(new String[] {"Format", metadata.getFormat()});
    return result.toArray(new Object[result.size()][2]);
  }

  private static void addStrings(List<?> values, String label, List<String[]> result) {
    boolean labelWritten = false;
    for (Object value : values) {
      if (value == null) {
        continue;
      }
      String valueString = String.valueOf(value);
      if (StringUtils.isBlank(valueString)) {
        continue;
      }

      String currentLabel = "";
      if (!labelWritten) {
        currentLabel = label;
        labelWritten = true;
      }
      result.add(new String[] {currentLabel, valueString});
    }
  }

  @Override
  public void navigationPerformed(NavigationEvent navigationEvent) {
    if (navigationEvent.isBookChanged()) {
      initBook(navigationEvent.getCurrentBook());
    }
  }
}
