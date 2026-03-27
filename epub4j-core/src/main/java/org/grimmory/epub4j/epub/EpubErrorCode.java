package org.grimmory.epub4j.epub;

import java.util.Locale;

/**
 * Error codes for EPUB validation. Each error code has a unique identifier, default message, and
 * help text.
 *
 * <p>Error codes are organized by category:
 *
 * <ul>
 *   <li>OPF - Package document errors
 *   <li>PARSE - XML/HTML parsing errors
 *   <li>LINK - Internal reference errors
 *   <li>CSS - Stylesheet errors
 *   <li>IMAGE - Image resource errors
 *   <li>FONT - Font resource errors
 *   <li>FILE - File system errors
 *   <li>ENCODING - Character encoding errors
 * </ul>
 *
 * @author Grimmory
 */
public enum EpubErrorCode {

  // OPF (Package Document) Errors

  /** The &lt;package&gt; element is missing from the OPF file. */
  OPF_MISSING_PACKAGE(
      "OPF_MISSING_PACKAGE",
      "The <package> element is missing from the OPF file",
      "The OPF file must have a <package> root element. This is required by the EPUB specification."),

  /** The &lt;metadata&gt; section is missing from the OPF. */
  OPF_MISSING_METADATA(
      "OPF_MISSING_METADATA",
      "The <metadata> section is missing from the OPF",
      "The <metadata> section is required in the OPF file. It must contain at least a title, identifier, and language."),

  /** The &lt;manifest&gt; section is missing from the OPF. */
  OPF_MISSING_MANIFEST(
      "OPF_MISSING_MANIFEST",
      "The <manifest> section is missing from the OPF",
      "The <manifest> section is required in the OPF file. It lists all resources in the EPUB."),

  /** The &lt;spine&gt; section is missing from the OPF. */
  OPF_MISSING_SPINE(
      "OPF_MISSING_SPINE",
      "The <spine> section is missing from the OPF",
      "The <spine> section is required in the OPF file. It defines the reading order."),

  /** Empty id attributes are invalid. */
  OPF_EMPTY_ID(
      "OPF_EMPTY_ID",
      "Empty id attributes are invalid",
      "Empty ID attributes are invalid in OPF files. All id attributes must have a non-empty value."),

  /** idref points to unknown id in manifest. */
  OPF_INCORRECT_IDREF(
      "OPF_INCORRECT_IDREF",
      "idref=\"%s\" points to unknown id",
      "The idref points to an id that does not exist in the manifest. Check the spelling or add the missing item."),

  /** The meta cover tag points to a non-existent item. */
  OPF_INCORRECT_COVER(
      "OPF_INCORRECT_COVER",
      "The meta cover tag points to a non-existent item",
      "The meta cover tag points to an item id that does not exist in the manifest."),

  /** Missing reference to the NCX Table of Contents. */
  OPF_MISSING_TOC_REF(
      "OPF_MISSING_TOC_REF",
      "Missing reference to the NCX Table of Contents",
      "The <spine> tag has no reference to the NCX table of contents file. Add toc=\"ncx-id\" to the spine element."),

  /** Missing navigation document (EPUB 3). */
  OPF_MISSING_NAV(
      "OPF_MISSING_NAV",
      "Missing navigation document",
      "This EPUB 3 book has no Navigation document. The nav element with epub:type=\"toc\" is required."),

  /** Manifest item file is missing from the EPUB. */
  OPF_MISSING_HREF(
      "OPF_MISSING_HREF",
      "Item (%s) in manifest is missing",
      "A file listed in the manifest is missing from the EPUB. Either add the file or remove the manifest entry."),

  /** Duplicate item in manifest. */
  OPF_DUPLICATE_HREF_MANIFEST(
      "OPF_DUPLICATE_HREF_MANIFEST",
      "Duplicate item in manifest: %s",
      "The same href appears multiple times in the manifest. Each href must be unique."),

  /** Duplicate item in spine. */
  OPF_DUPLICATE_IDREF_SPINE(
      "OPF_DUPLICATE_IDREF_SPINE",
      "Duplicate itemref in spine: %s",
      "The same idref appears multiple times in the spine. This is allowed but may indicate an error."),

  /** Manifest entry has no href attribute. */
  OPF_NO_HREF(
      "OPF_NO_HREF",
      "Item in manifest has no href attribute",
      "This manifest entry has no href attribute. Either add the href attribute or remove the entry."),

  /** Non-linear items in the spine. */
  OPF_NON_LINEAR_ITEMS(
      "OPF_NON_LINEAR_ITEMS",
      "Non-linear items in the spine",
      "Items marked as non-linear (linear=\"no\") will be displayed in random order by different readers. Consider placing items in the desired order instead."),

  /** Missing dc:identifier in metadata. */
  OPF_MISSING_IDENTIFIER(
      "OPF_MISSING_IDENTIFIER",
      "Missing dc:identifier in metadata",
      "The EPUB must have at least one dc:identifier element in the metadata section."),

  /** Missing dc:title in metadata. */
  OPF_MISSING_TITLE(
      "OPF_MISSING_TITLE",
      "Missing dc:title in metadata",
      "The EPUB must have at least one dc:title element in the metadata section."),

  /** Missing dc:language in metadata. */
  OPF_MISSING_LANGUAGE(
      "OPF_MISSING_LANGUAGE",
      "Missing dc:language in metadata",
      "The EPUB should have a dc:language element in the metadata section."),

  // Parsing Errors

  /** File has zero bytes. */
  EMPTY_FILE(
      "EMPTY_FILE",
      "File has zero bytes: %s",
      "This file is empty. Either add content or remove the file from the manifest."),

  /** XML parsing failed. */
  XML_PARSE_ERROR(
      "XML_PARSE_ERROR",
      "XML parsing failed: %s",
      "The XML file could not be parsed. Check for well-formedness errors."),

  /** HTML parsing failed. */
  HTML_PARSE_ERROR(
      "HTML_PARSE_ERROR",
      "HTML parsing failed: %s",
      "The HTML file could not be parsed. Check for syntax errors."),

  /** XML encoding declaration doesn't match actual encoding. */
  ENCODING_MISMATCH(
      "ENCODING_MISMATCH",
      "XML encoding declaration (%s) doesn't match actual encoding (%s)",
      "The encoding declared in the XML prolog doesn't match the actual byte encoding. Update the declaration or re-encode the file."),

  /** Filename has invalid characters. */
  INVALID_FILENAME(
      "INVALID_FILENAME",
      "Filename has invalid characters: %s",
      "The filename contains characters that may cause compatibility issues. Use only ASCII letters, digits, hyphens, underscores, and periods."),

  /** Duplicate id attribute in document. */
  DUPLICATE_ID(
      "DUPLICATE_ID",
      "Duplicate id attribute: %s",
      "The same id attribute appears multiple times in the document. All id values must be unique within a document."),

  /** HTML file exceeds size limit. */
  LARGE_HTML_FILE(
      "LARGE_HTML_FILE",
      "HTML file exceeds size limit (%.1f KB > %.1f KB)",
      "Large HTML files may cause performance issues on some readers. Consider splitting the file."),

  // Link Errors

  /** Link points to non-existent file. */
  BROKEN_LINK(
      "BROKEN_LINK",
      "Link points to non-existent file: %s",
      "This link points to a file that doesn't exist in the EPUB. Fix or remove the link."),

  /** Fragment ID doesn't exist in target document. */
  BROKEN_FRAGMENT(
      "BROKEN_FRAGMENT",
      "Fragment ID #%s doesn't exist in %s",
      "The link points to a fragment ID that doesn't exist in the target document."),

  /** Link to external resource. */
  EXTERNAL_LINK(
      "EXTERNAL_LINK",
      "Link to external resource: %s",
      "EPUBs should not link to external resources as they may not be available when the EPUB is read."),

  /** Resource has incorrect mimetype. */
  INCORRECT_MIMETYPE(
      "INCORRECT_MIMETYPE",
      "Resource %s has incorrect media type: %s (expected %s)",
      "The media type declared in the manifest doesn't match the actual file content."),

  // CSS Errors

  /** CSS syntax error. */
  CSS_SYNTAX_ERROR(
      "CSS_SYNTAX_ERROR",
      "CSS syntax error: %s",
      "The CSS file contains syntax errors. Fix the CSS to ensure proper rendering."),

  /** Invalid CSS property. */
  CSS_INVALID_PROPERTY(
      "CSS_INVALID_PROPERTY",
      "Invalid CSS property: %s",
      "This CSS property is not recognized. Check the spelling or remove the property."),

  /** Invalid CSS value. */
  CSS_INVALID_VALUE(
      "CSS_INVALID_VALUE",
      "Invalid CSS value for property %s: %s",
      "The value for this CSS property is invalid. Check the CSS specification for valid values."),

  /** Referenced font file is missing. */
  CSS_MISSING_FONT(
      "CSS_MISSING_FONT",
      "Referenced font file is missing: %s",
      "The @font-face rule references a font file that doesn't exist in the EPUB."),

  /** Referenced image in CSS is missing. */
  CSS_MISSING_IMAGE(
      "CSS_MISSING_IMAGE",
      "Referenced image is missing: %s",
      "The CSS references an image file that doesn't exist in the EPUB."),

  // Image Errors

  /** Image file is corrupted. */
  INVALID_IMAGE(
      "INVALID_IMAGE",
      "Image file is corrupted: %s",
      "The image file could not be read. The file may be corrupted or in an unsupported format."),

  /** Image format not supported. */
  UNSUPPORTED_IMAGE_FORMAT(
      "UNSUPPORTED_IMAGE_FORMAT",
      "Image format not supported: %s",
      "This image format may not be supported by all EPUB readers. Use JPEG, PNG, GIF, or SVG for maximum compatibility."),

  /** Image file is too large. */
  LARGE_IMAGE(
      "LARGE_IMAGE",
      "Image file is too large: %.1f KB",
      "Large images may cause memory issues on some readers. Consider resizing or compressing the image."),

  // Font Errors

  /** Font file is corrupted. */
  INVALID_FONT(
      "INVALID_FONT",
      "Font file is corrupted: %s",
      "The font file could not be read. The file may be corrupted."),

  /** Font format not supported. */
  UNSUPPORTED_FONT_FORMAT(
      "UNSUPPORTED_FONT_FORMAT",
      "Font format not supported: %s",
      "This font format may not be supported by all EPUB readers. Use WOFF, WOFF2, TTF, or OTF for maximum compatibility."),

  /** Font file is not referenced. */
  UNREFERENCED_FONT(
      "UNREFERENCED_FONT",
      "Font file is not referenced: %s",
      "This font file is in the manifest but not referenced by any CSS. Consider removing it to reduce file size."),

  // File System Errors

  /** File path is too long. */
  PATH_TOO_LONG(
      "PATH_TOO_LONG",
      "File path is too long: %s (%d characters)",
      "Some systems have limits on file path length. Keep paths under 255 characters for maximum compatibility."),

  /** Invalid characters in path. */
  INVALID_PATH_CHARS(
      "INVALID_PATH_CHARS",
      "Invalid characters in path: %s",
      "The path contains characters that may cause issues on some systems. Use only ASCII letters, digits, hyphens, underscores, periods, and forward slashes."),

  /** Mimetype file is missing or invalid. */
  INVALID_MIMETYPE_FILE(
      "INVALID_MIMETYPE_FILE",
      "Mimetype file is missing or invalid",
      "The mimetype file must be the first file in the EPUB and contain exactly 'application/epub+zip'."),

  /** Container.xml is missing or invalid. */
  INVALID_CONTAINER(
      "INVALID_CONTAINER",
      "Container.xml is missing or invalid",
      "The META-INF/container.xml file is required and must point to the OPF file."),

  // Table of Contents Errors

  /** NCX file is missing. */
  MISSING_NCX(
      "MISSING_NCX",
      "NCX file is missing",
      "The EPUB 2 NCX table of contents file is missing. Create an NCX file or upgrade to EPUB 3 with nav document."),

  /** Nav document is missing. */
  MISSING_NAV_DOCUMENT(
      "MISSING_NAV_DOCUMENT",
      "Navigation document is missing",
      "The EPUB 3 navigation document (nav.xhtml) is missing. Create a nav element with epub:type=\"toc\"."),

  /** TOC has no entries. */
  EMPTY_TOC(
      "EMPTY_TOC",
      "Table of contents has no entries",
      "The table of contents is empty. Add entries for each chapter or section."),

  /** TOC entry points to non-existent file. */
  BROKEN_TOC_ENTRY(
      "BROKEN_TOC_ENTRY",
      "TOC entry points to non-existent file: %s",
      "This table of contents entry points to a file that doesn't exist."),

  // Accessibility Errors

  /** Image missing alt text. */
  MISSING_ALT_TEXT(
      "MISSING_ALT_TEXT",
      "Image missing alt text: %s",
      "Images should have alt text for accessibility. Add an alt attribute to the img element."),

  /** Document missing language attribute. */
  MISSING_LANG_ATTRIBUTE(
      "MISSING_LANG_ATTRIBUTE",
      "Document missing lang attribute",
      "The html element should have a lang attribute to specify the document language."),

  /** Heading levels skip. */
  HEADING_SKIP(
      "HEADING_SKIP",
      "Heading levels skip from h%d to h%d",
      "Heading levels should not skip (e.g., h1 to h3). Use sequential heading levels for proper document structure."),

  // EPUB Version Errors

  /** Unknown EPUB version. */
  UNKNOWN_VERSION(
      "UNKNOWN_VERSION",
      "Unknown EPUB version: %s",
      "The EPUB version could not be determined. Ensure the package element has a valid version attribute."),

  /** EPUB version mismatch. */
  VERSION_MISMATCH(
      "VERSION_MISMATCH",
      "EPUB version mismatch: declared %s, but uses %s features",
      "The EPUB uses features from a different version than declared. Update the version attribute or remove incompatible features."),
  ;

  private final String code;
  private final String defaultMessage;
  private final String help;

  EpubErrorCode(String code, String defaultMessage, String help) {
    this.code = code;
    this.defaultMessage = defaultMessage;
    this.help = help;
  }

  /**
   * Get the error code string.
   *
   * @return the error code
   */
  public String getCode() {
    return code;
  }

  /**
   * Get the default error message.
   *
   * @return the default message
   */
  public String getDefaultMessage() {
    return defaultMessage;
  }

  /**
   * Get the help text for this error.
   *
   * @return the help text
   */
  public String getHelp() {
    return help;
  }

  /**
   * Get the error message with arguments formatted.
   *
   * @param args arguments to format into the message
   * @return formatted error message
   */
  public String formatMessage(Object... args) {
    if (args == null || args.length == 0) {
      return defaultMessage;
    }
    if (defaultMessage.indexOf('%') < 0) {
      return defaultMessage;
    }
    return String.format(Locale.ROOT, defaultMessage, args);
  }
}
