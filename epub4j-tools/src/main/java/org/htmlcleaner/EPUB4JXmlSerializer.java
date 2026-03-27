package org.htmlcleaner;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class EPUB4JXmlSerializer extends SimpleXmlSerializer {

  private final String outputEncoding;

  public EPUB4JXmlSerializer(CleanerProperties paramCleanerProperties, String outputEncoding) {
    super(paramCleanerProperties);
    this.outputEncoding = outputEncoding;
  }

  protected String escapeXml(String xmlContent) {
    return xmlContent;
  }

  /**
   * Differs from the super.serializeOpenTag in that it:
   *
   * <ul>
   *   <li>skips the xmlns:xml="xml" attribute
   *   <li>if the tagNode is a meta tag setting the contentType then it sets the encoding to the
   *       actual encoding
   * </ul>
   */
  protected void serializeOpenTag(TagNode tagNode, Writer writer, boolean newLine)
      throws IOException {
    String tagName = tagNode.getName();

    if (Utils.isEmptyString(tagName)) {
      return;
    }

    boolean nsAware = props.isNamespacesAware();

    Set<String> definedNSPrefixes = null;
    Set<String> additionalNSDeclNeeded = null;

    String tagPrefix = Utils.getXmlNSPrefix(tagName);
    if (tagPrefix != null) {
      if (nsAware) {
        definedNSPrefixes = new HashSet<>();
        tagNode.collectNamespacePrefixesOnPath(definedNSPrefixes);
        if (!definedNSPrefixes.contains(tagPrefix)) {
          additionalNSDeclNeeded = new TreeSet<>();
          additionalNSDeclNeeded.add(tagPrefix);
        }
      } else {
        tagName = Utils.getXmlName(tagName);
      }
    }

    writer.write("<" + tagName);

    if (isMetaContentTypeTag(tagNode)) {
      tagNode.getAttributes().put("content", "text/html; charset=" + outputEncoding);
    }

    // write attributes
    for (Map.Entry<String, String> entry : tagNode.getAttributes().entrySet()) {
      String attName = entry.getKey();
      if ("xmlns:xml".equalsIgnoreCase(attName)) {
        continue;
      }
      if ("xmlns".equalsIgnoreCase(attName)) {
        writer.write(" xmlns=\"" + escapeXml(entry.getValue()) + "\"");
        continue;
      }
      String attPrefix = Utils.getXmlNSPrefix(attName);
      if (attPrefix != null) {
        if (nsAware) {
          if ("xml".equalsIgnoreCase(attPrefix) || "xmlns".equalsIgnoreCase(attPrefix)) {
            continue;
          }
          // collect used namespace prefixes in attributes in order to explicitly define
          // ns declaration if needed; otherwise it would be ill-formed xml
          if (definedNSPrefixes == null) {
            definedNSPrefixes = new HashSet<>();
            tagNode.collectNamespacePrefixesOnPath(definedNSPrefixes);
          }
          if (!definedNSPrefixes.contains(attPrefix)) {
            if (additionalNSDeclNeeded == null) {
              additionalNSDeclNeeded = new TreeSet<>();
            }
            additionalNSDeclNeeded.add(attPrefix);
          }
        } else {
          attName = Utils.getXmlName(attName);
        }
      }
      writer.write(" " + attName + "=\"" + escapeXml(entry.getValue()) + "\"");
    }

    // write namespace declarations
    if (nsAware) {
      Map<String, String> nsDeclarations = tagNode.getNamespaceDeclarations();
      if (nsDeclarations != null) {
        for (Map.Entry<String, String> entry : nsDeclarations.entrySet()) {
          String prefix = entry.getKey();
          String att = "xmlns";
          if (!prefix.isEmpty()) {
            att += ":" + prefix;
          }
          writer.write(" " + att + "=\"" + escapeXml(entry.getValue()) + "\"");
        }
      }
    }

    // write additional namespace declarations needed for this tag in order xml to be well-formed
    if (additionalNSDeclNeeded != null) {
      for (String prefix : additionalNSDeclNeeded) {
        // skip the xmlns:xml="xml" attribute
        if ("xml".equalsIgnoreCase(prefix)) {
          continue;
        }
        writer.write(" xmlns:" + prefix + "=\"" + prefix + "\"");
      }
    }

    if (isMinimizedTagSyntax(tagNode)) {
      writer.write(" />");
      if (newLine) {
        writer.write("\n");
      }
    } else if (dontEscape(tagNode)) {
      writer.write("><![CDATA[");
    } else {
      writer.write(">");
    }
  }

  private static boolean isMetaContentTypeTag(TagNode tagNode) {
    String httpEquiv = tagNode.getAttributesInLowerCase().get("http-equiv");
    return "meta".equalsIgnoreCase(tagNode.getName()) && "content-type".equalsIgnoreCase(httpEquiv);
  }
}
