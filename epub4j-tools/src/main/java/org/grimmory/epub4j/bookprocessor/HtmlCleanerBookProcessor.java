package org.grimmory.epub4j.bookprocessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.Constants;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.BookProcessor;
import org.grimmory.epub4j.util.NoCloseWriter;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.DoctypeToken;
import org.htmlcleaner.EPUB4JXmlSerializer;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;

/**
 * Cleans up regular html into xhtml. Uses HtmlCleaner to do this.
 *
 * @author paul
 */
public class HtmlCleanerBookProcessor extends HtmlBookProcessor implements BookProcessor {

  private static final Pattern HTML_TAG_PATTERN =
      Pattern.compile("<html(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);

  private static final Pattern META_CONTENT_TYPE_PATTERN =
      Pattern.compile(
          "(?i)(<meta\\s+[^>]*http-equiv\\s*=\\s*\"Content-Type\"[^>]*content\\s*=\\s*\")([^\"]+)(\"[^>]*>)");
  private static final Pattern XHTML_NAMESPACE_PATTERN = Pattern.compile("(?is).*\\sxmlns\\s*=.*");
  private static final Pattern XMLNS_XMLNS_ATTRIBUTE_PATTERN =
      Pattern.compile("\\s+xmlns:xmlns=\"xmlns\"");
  private static final Pattern XMLNS_XML_ATTRIBUTE_PATTERN =
      Pattern.compile("\\s+xmlns:xml=\"xml\"");
  private static final Pattern DOCTYPE_HTML_PATTERN = Pattern.compile("(?i)<!DOCTYPE\\s+html");

  private final HtmlCleaner htmlCleaner;

  public HtmlCleanerBookProcessor() {
    this.htmlCleaner = createHtmlCleaner();
  }

  private static HtmlCleaner createHtmlCleaner() {
    HtmlCleaner result = new HtmlCleaner();
    CleanerProperties cleanerProperties = result.getProperties();
    cleanerProperties.setOmitXmlDeclaration(true);
    cleanerProperties.setOmitDoctypeDeclaration(false);
    cleanerProperties.setRecognizeUnicodeChars(true);
    cleanerProperties.setTranslateSpecialEntities(false);
    cleanerProperties.setIgnoreQuestAndExclam(true);
    cleanerProperties.setUseEmptyElementTags(false);
    return result;
  }

  public byte[] processHtml(Resource resource, Book book) throws IOException {

    // clean html
    TagNode node = htmlCleaner.clean(resource.getReader());

    // post-process cleaned html
    node.removeAttribute("xmlns");
    node.addNamespaceDeclaration("", Constants.NAMESPACE_XHTML);
    node.setDocType(createXHTMLDoctypeToken());

    // write result to output
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Writer writer = new OutputStreamWriter(out, Constants.CHARACTER_ENCODING);
    writer = new NoCloseWriter(writer);
    EPUB4JXmlSerializer xmlSerializer =
        new EPUB4JXmlSerializer(htmlCleaner.getProperties(), Constants.CHARACTER_ENCODING);
    xmlSerializer.write(node, writer, Constants.CHARACTER_ENCODING);
    writer.flush();

    String serialized = out.toString(Constants.CHARACTER_ENCODING);
    String normalized = normalizeSerializedHtml(serialized, Constants.CHARACTER_ENCODING);
    return normalized.getBytes(Constants.CHARACTER_ENCODING);
  }

  private static String normalizeSerializedHtml(String html, String outputEncoding) {
    String normalized = DOCTYPE_HTML_PATTERN.matcher(html).replaceFirst("<!DOCTYPE HTML");
    normalized = XMLNS_XML_ATTRIBUTE_PATTERN.matcher(normalized).replaceAll("");
    normalized = XMLNS_XMLNS_ATTRIBUTE_PATTERN.matcher(normalized).replaceAll("");

    Matcher htmlTagMatcher = HTML_TAG_PATTERN.matcher(normalized);
    if (htmlTagMatcher.find()) {
      String attrs = htmlTagMatcher.group(1) == null ? "" : htmlTagMatcher.group(1);
      if (!XHTML_NAMESPACE_PATTERN.matcher(attrs).matches()) {
        attrs = attrs + " xmlns=\"" + Constants.NAMESPACE_XHTML + "\"";
      }
      String replacement = "<html" + attrs + ">";
      normalized = htmlTagMatcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    Matcher metaMatcher = META_CONTENT_TYPE_PATTERN.matcher(normalized);
    StringBuilder rewritten = new StringBuilder();
    while (metaMatcher.find()) {
      String replacement =
          metaMatcher.group(1) + "text/html; charset=" + outputEncoding + metaMatcher.group(3);
      metaMatcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
    }
    metaMatcher.appendTail(rewritten);
    return rewritten.toString();
  }

  private static DoctypeToken createXHTMLDoctypeToken() {
    return new DoctypeToken(
        "HTML",
        "PUBLIC",
        "-//W3C//DTD XHTML 1.1//EN",
        "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd");
  }
}
