package org.grimmory.epub4j.html.htmlcleaner;

import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.CommentNode;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.EndTagToken;
import org.htmlcleaner.TagNode;

public class XmlSerializer {

  protected final CleanerProperties props;

  public XmlSerializer(CleanerProperties props) {
    this.props = props;
  }

  public void writeXml(TagNode tagNode, XMLStreamWriter writer) throws XMLStreamException {
    //        if ( !props.isOmitXmlDeclaration() ) {
    //            String declaration = "<?xml version=\"1.0\"";
    //            if (charset != null) {
    //                declaration += " encoding=\"" + charset + "\"";
    //            }
    //            declaration += "?>";
    //            writer.write(declaration + "\n");
    //		}

    //		if ( !props.isOmitDoctypeDeclaration() ) {
    //			DoctypeToken doctypeToken = tagNode.getDocType();
    //			if ( doctypeToken != null ) {
    //				doctypeToken.serialize(this, writer);
    //			}
    //		}
    //
    serialize(tagNode, writer);

    writer.flush();
  }

  protected void serializeOpenTag(TagNode tagNode, XMLStreamWriter writer)
      throws XMLStreamException {
    String tagName = tagNode.getName();

    writer.writeStartElement(tagName);
    Map<String, String> tagAtttributes = tagNode.getAttributes();
    for (Map.Entry<String, String> entry : tagAtttributes.entrySet()) {
      String attName = entry.getKey();
      String attValue = entry.getValue();

      if (!props.isNamespacesAware() && ("xmlns".equals(attName) || attName.startsWith("xmlns:"))) {
        continue;
      }
      writer.writeAttribute(attName, attValue);
    }
  }

  protected void serializeEmptyTag(TagNode tagNode, XMLStreamWriter writer)
      throws XMLStreamException {
    String tagName = tagNode.getName();

    writer.writeEmptyElement(tagName);
    Map<String, String> tagAtttributes = tagNode.getAttributes();
    for (Map.Entry<String, String> entry : tagAtttributes.entrySet()) {
      String attName = entry.getKey();
      String attValue = entry.getValue();

      if (!props.isNamespacesAware() && ("xmlns".equals(attName) || attName.startsWith("xmlns:"))) {
        continue;
      }
      writer.writeAttribute(attName, attValue);
    }
  }

  protected static void serializeEndTag(TagNode tagNode, XMLStreamWriter writer)
      throws XMLStreamException {
    writer.writeEndElement();
  }

  protected void serialize(TagNode tagNode, XMLStreamWriter writer) throws XMLStreamException {
    if (tagNode.getChildTagList().isEmpty()) {
      serializeEmptyTag(tagNode, writer);
    } else {
      serializeOpenTag(tagNode, writer);

      List<?> tagChildren = tagNode.getChildTagList();
      for (Object item : tagChildren) {
        if (item != null) {
          serializeToken(item, writer);
        }
      }
      serializeEndTag(tagNode, writer);
    }
  }

  private void serializeToken(Object item, XMLStreamWriter writer) throws XMLStreamException {
    if (item instanceof ContentNode contentNode) {
      writer.writeCharacters(contentNode.getContent());
    } else if (item instanceof CommentNode commentNode) {
      writer.writeComment(commentNode.getContent());
    } else if (item instanceof EndTagToken) {
      return;
    } else if (item instanceof TagNode tagNode) {
      serialize(tagNode, writer);
    }
  }
}
