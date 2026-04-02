package org.grimmory.epub4j.cfi;

import java.io.Serial;
import java.io.Serializable;

/**
 * Represents the result of a CFI to XPointer conversion. Contains the XPointer string and optional
 * start/end positions for range-based references.
 */
public class XPointerResult implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private final String xpointer;
  private final String pos0;
  private final String pos1;

  public XPointerResult(String xpointer, String pos0, String pos1) {
    this.xpointer = xpointer;
    this.pos0 = pos0;
    this.pos1 = pos1;
  }

  public XPointerResult(String xpointer) {
    this(xpointer, null, null);
  }

  /** The full XPointer expression. */
  public String getXpointer() {
    return xpointer;
  }

  /** Start position for range references. */
  public String getPos0() {
    return pos0;
  }

  /** End position for range references. */
  public String getPos1() {
    return pos1;
  }

  @Override
  public String toString() {
    return xpointer;
  }
}
