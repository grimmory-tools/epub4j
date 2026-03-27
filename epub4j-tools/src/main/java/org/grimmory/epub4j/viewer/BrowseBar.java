package org.grimmory.epub4j.viewer;

import java.awt.BorderLayout;
import java.io.Serial;
import javax.swing.JPanel;
import org.grimmory.epub4j.browsersupport.Navigator;

public class BrowseBar extends JPanel {

  @Serial private static final long serialVersionUID = -5745389338067538254L;

  public BrowseBar(Navigator navigator, ContentPane chapterPane) {
    super(new BorderLayout());
    add(new ButtonBar(navigator, chapterPane), BorderLayout.CENTER);
    add(new SpineSlider(navigator), BorderLayout.NORTH);
  }
}
