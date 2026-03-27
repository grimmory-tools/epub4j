package org.grimmory.epub4j.viewer;

import java.io.Serial;
import javax.swing.JSlider;
import org.grimmory.epub4j.browsersupport.NavigationEvent;
import org.grimmory.epub4j.browsersupport.NavigationEventListener;
import org.grimmory.epub4j.browsersupport.Navigator;
import org.grimmory.epub4j.domain.Book;

// package
class SpineSlider extends JSlider implements NavigationEventListener {

  /** */
  @Serial private static final long serialVersionUID = 8436441824668551056L;

  private final Navigator navigator;

  public SpineSlider(Navigator navigator) {
    super(JSlider.HORIZONTAL);
    this.navigator = navigator;
    navigator.addNavigationEventListener(this);
    setPaintLabels(false);
    addChangeListener(
        evt -> {
          JSlider slider = (JSlider) evt.getSource();
          int value = slider.getValue();
          SpineSlider.this.navigator.gotoSpineSection(value, SpineSlider.this);
        });
    initBook(navigator.getBook());
  }

  private void initBook(Book book) {
    if (book == null) {
      return;
    }
    super.setMinimum(0);
    super.setMaximum(book.getSpine().size() - 1);
    super.setValue(0);
    //			setPaintTicks(true);
    updateToolTip();
  }

  private void updateToolTip() {
    String tooltip = "";
    if (navigator.getCurrentSpinePos() >= 0 && navigator.getBook() != null) {
      tooltip = navigator.getCurrentSpinePos() + 1 + " / " + navigator.getBook().getSpine().size();
    }
    setToolTipText(tooltip);
  }

  @Override
  public void navigationPerformed(NavigationEvent navigationEvent) {
    updateToolTip();
    if (this == navigationEvent.getSource()) {
      return;
    }

    if (navigationEvent.isBookChanged()) {
      initBook(navigationEvent.getCurrentBook());
    } else if (navigationEvent.isResourceChanged()) {
      setValue(navigationEvent.getCurrentSpinePos());
    }
  }
}
