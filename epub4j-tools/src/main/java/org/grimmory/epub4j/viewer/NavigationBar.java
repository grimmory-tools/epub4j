package org.grimmory.epub4j.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.Serial;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import org.grimmory.epub4j.browsersupport.NavigationEvent;
import org.grimmory.epub4j.browsersupport.NavigationEventListener;
import org.grimmory.epub4j.browsersupport.NavigationHistory;
import org.grimmory.epub4j.browsersupport.Navigator;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.search.SearchIndex;
import org.grimmory.epub4j.search.SearchResult;
import org.grimmory.epub4j.search.SearchResults;
import org.grimmory.epub4j.util.ToolsResourceUtil;

/**
 * A toolbar that contains the history back and forward buttons and the page title.
 *
 * @author paul.siegmann
 */
public class NavigationBar extends JToolBar implements NavigationEventListener {

  /** */
  @Serial private static final long serialVersionUID = 1166410773448311544L;

  private final JTextField titleField;
  private JTextField searchField;
  private final NavigationHistory navigationHistory;
  private final Navigator navigator;
  private final SearchIndex searchIndex = new SearchIndex();
  private String previousSearchTerm = null;
  private int searchResultIndex = -1;
  private SearchResults searchResults;

  public NavigationBar(Navigator navigator) {
    this.navigationHistory = new NavigationHistory(navigator);
    this.navigator = navigator;
    navigator.addNavigationEventListener(this);
    addHistoryButtons();
    titleField = (JTextField) add(new JTextField());
    addSearchButtons();
    initBook(navigator.getBook());
  }

  private void initBook(Book book) {
    if (book == null) {
      return;
    }
    searchIndex.initBook(book);
  }

  private void addHistoryButtons() {
    Font historyButtonFont = new Font("SansSerif", Font.BOLD, 24);
    JButton previousButton = ViewerUtil.createButton("history-previous", "<=");
    previousButton.setFont(historyButtonFont);
    //		previousButton.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, Event.CTRL_MASK));

    previousButton.addActionListener(e -> navigationHistory.move(-1));

    add(previousButton);

    JButton nextButton = ViewerUtil.createButton("history-next", "=>");
    nextButton.setFont(historyButtonFont);
    nextButton.addActionListener(e -> navigationHistory.move(1));
    add(nextButton);
  }

  private void doSearch(int move) {
    String searchTerm = searchField.getText();
    if (searchTerm.equals(previousSearchTerm)) {
      searchResultIndex += move;
    } else {
      searchResults = searchIndex.doSearch(searchTerm);
      previousSearchTerm = searchTerm;
      searchResultIndex = 0;
    }
    if (searchResultIndex < 0) {
      searchResultIndex = searchResults.size() - 1;
    } else if (searchResultIndex >= searchResults.size()) {
      searchResultIndex = 0;
    }
    if (!searchResults.isEmpty()) {
      SearchResult searchResult = searchResults.getHits().get(searchResultIndex);
      navigator.gotoResource(
          searchResult.getResource(), searchResult.getPagePos(), NavigationBar.this);
    }
  }

  private void addSearchButtons() {
    JPanel searchForm = new JPanel(new BorderLayout());
    searchForm.setPreferredSize(new Dimension(200, 28));
    Font historyButtonFont = new Font("SansSerif", Font.BOLD, 20);
    JButton previousButton = ViewerUtil.createButton("search-previous", "<");
    previousButton.setFont(historyButtonFont);
    previousButton.addActionListener(e -> doSearch(-1));

    searchForm.add(previousButton, BorderLayout.WEST);

    searchField = new JTextField();
    searchField.setMinimumSize(new Dimension(100, 20));
    searchField.addKeyListener(
        new KeyListener() {

          @Override
          public void keyTyped(KeyEvent keyEvent) {}

          @Override
          public void keyPressed(KeyEvent e) {}

          @Override
          public void keyReleased(KeyEvent keyEvent) {
            if (keyEvent.getKeyCode() == KeyEvent.VK_ENTER) {
              doSearch(1);
            }
          }
        });
    searchForm.add(searchField, BorderLayout.CENTER);
    JButton nextButton = ViewerUtil.createButton("search-next", ">");
    nextButton.setFont(historyButtonFont);
    nextButton.addActionListener(e -> doSearch(1));
    searchForm.add(nextButton, BorderLayout.EAST);
    add(searchForm);
  }

  @Override
  public void navigationPerformed(NavigationEvent navigationEvent) {
    if (navigationEvent.isBookChanged()) {
      initBook(navigationEvent.getCurrentBook());
    }
    if (navigationEvent.getCurrentResource() != null) {
      String title = ToolsResourceUtil.getTitle(navigationEvent.getCurrentResource());
      titleField.setText(title);
    }
  }
}
