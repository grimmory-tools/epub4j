package org.grimmory.epub4j.viewer;

import java.awt.Image;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;

public class ViewerUtil {

  private static final System.Logger log = System.getLogger(ViewerUtil.class.getName());

  /**
   * Creates a button with the given icon. The icon will be loaded from the classpath. If loading
   * the icon is unsuccessful it will use the defaultLabel.
   *
   * @param iconName
   * @param backupLabel
   * @return a button with the given icon.
   */
  // package
  static JButton createButton(String iconName, String backupLabel) {
    ImageIcon icon = createImageIcon(iconName);
    if (icon == null) {
      return new JButton(backupLabel);
    }
    return new JButton(icon);
  }

  static ImageIcon createImageIcon(String iconName) {
    ImageIcon result = null;
    String fullIconPath = "/viewer/icons/" + iconName + ".png";
    try {
      Image image = ImageIO.read(ViewerUtil.class.getResourceAsStream(fullIconPath));
      result = new ImageIcon(image);
    } catch (Exception e) {
      log.log(System.Logger.Level.ERROR, "Icon '" + fullIconPath + "' not found");
    }
    return result;
  }
}
