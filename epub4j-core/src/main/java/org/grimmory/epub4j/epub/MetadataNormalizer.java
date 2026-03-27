package org.grimmory.epub4j.epub;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Identifier;
import org.grimmory.epub4j.domain.Metadata;

/**
 * Normalizes book metadata: author names, ISBNs, language codes, dates. Multi-strategy
 * normalization pipeline for book metadata.
 */
public class MetadataNormalizer {

  private static final Pattern DIACRITICAL_MARKS =
      Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern LAST_FIRST = Pattern.compile("^(.+?),\\s*(.+)$");

  // Date formats to try in order (most specific first)
  private static final List<DateTimeFormatter> DATE_FORMATTERS =
      List.of(
          DateTimeFormatter.ofPattern("yyyy-MM-dd"),
          DateTimeFormatter.ofPattern("yyyy/MM/dd"),
          DateTimeFormatter.ofPattern("dd-MM-yyyy"),
          DateTimeFormatter.ofPattern("dd/MM/yyyy"),
          DateTimeFormatter.ofPattern("MM/dd/yyyy"),
          DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("yyyy-MM"),
          DateTimeFormatter.ofPattern("yyyy"));

  // ISO 639-1 two-letter language codes (common subset)
  private static final Set<String> ISO_639_1 =
      Set.of(
          "aa", "ab", "af", "ak", "am", "an", "ar", "as", "av", "ay", "az", "ba", "be", "bg", "bh",
          "bi", "bm", "bn", "bo", "br", "bs", "ca", "ce", "ch", "co", "cr", "cs", "cu", "cv", "cy",
          "da", "de", "dv", "dz", "ee", "el", "en", "eo", "es", "et", "eu", "fa", "ff", "fi", "fj",
          "fo", "fr", "fy", "ga", "gd", "gl", "gn", "gu", "gv", "ha", "he", "hi", "ho", "hr", "ht",
          "hu", "hy", "hz", "ia", "id", "ie", "ig", "ii", "ik", "in", "io", "is", "it", "iu", "ja",
          "jv", "ka", "kg", "ki", "kj", "kk", "kl", "km", "kn", "ko", "kr", "ks", "ku", "kv", "kw",
          "ky", "la", "lb", "lg", "li", "ln", "lo", "lt", "lu", "lv", "mg", "mh", "mi", "mk", "ml",
          "mn", "mr", "ms", "mt", "my", "na", "nb", "nd", "ne", "ng", "nl", "nn", "no", "nr", "nv",
          "ny", "oc", "oj", "om", "or", "os", "pa", "pi", "pl", "ps", "pt", "qu", "rm", "rn", "ro",
          "ru", "rw", "sa", "sc", "sd", "se", "sg", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr",
          "ss", "st", "su", "sv", "sw", "ta", "te", "tg", "th", "ti", "tk", "tl", "tn", "to", "tr",
          "ts", "tt", "tw", "ty", "ug", "uk", "ur", "uz", "ve", "vi", "vo", "wa", "wo", "xh", "yi",
          "yo", "za", "zh", "zu");

  // Common language name → code mappings
  private static final Map<String, String> LANGUAGE_NAMES;
  private static final Pattern YEAR_PATTERN = Pattern.compile("^\\d{4}$");
  private static final Pattern LOCALE_SEPARATOR_PATTERN = Pattern.compile("[-_]");
  private static final Pattern UNIFORM_DIGIT_PATTERN = Pattern.compile("^(\\d)\\1{9,12}$");
  private static final Pattern NON_ISBN_CHAR_PATTERN = Pattern.compile("[^0-9Xx]");

  static {
    LANGUAGE_NAMES =
        Map.<String, String>ofEntries(
            Map.entry("english", "en"),
            Map.entry("eng", "en"),
            Map.entry("french", "fr"),
            Map.entry("français", "fr"),
            Map.entry("fre", "fr"),
            Map.entry("fra", "fr"),
            Map.entry("german", "de"),
            Map.entry("deutsch", "de"),
            Map.entry("ger", "de"),
            Map.entry("deu", "de"),
            Map.entry("spanish", "es"),
            Map.entry("español", "es"),
            Map.entry("spa", "es"),
            Map.entry("italian", "it"),
            Map.entry("italiano", "it"),
            Map.entry("ita", "it"),
            Map.entry("portuguese", "pt"),
            Map.entry("português", "pt"),
            Map.entry("por", "pt"),
            Map.entry("russian", "ru"),
            Map.entry("русский", "ru"),
            Map.entry("rus", "ru"),
            Map.entry("chinese", "zh"),
            Map.entry("中文", "zh"),
            Map.entry("chi", "zh"),
            Map.entry("zho", "zh"),
            Map.entry("japanese", "ja"),
            Map.entry("日本語", "ja"),
            Map.entry("jpn", "ja"),
            Map.entry("korean", "ko"),
            Map.entry("한국어", "ko"),
            Map.entry("kor", "ko"),
            Map.entry("dutch", "nl"),
            Map.entry("nederlands", "nl"),
            Map.entry("nld", "nl"),
            Map.entry("dut", "nl"),
            Map.entry("polish", "pl"),
            Map.entry("polski", "pl"),
            Map.entry("pol", "pl"),
            Map.entry("swedish", "sv"),
            Map.entry("svenska", "sv"),
            Map.entry("swe", "sv"),
            Map.entry("danish", "da"),
            Map.entry("dansk", "da"),
            Map.entry("dan", "da"),
            Map.entry("norwegian", "no"),
            Map.entry("norsk", "no"),
            Map.entry("nor", "no"),
            Map.entry("finnish", "fi"),
            Map.entry("suomi", "fi"),
            Map.entry("fin", "fi"),
            Map.entry("hungarian", "hu"),
            Map.entry("magyar", "hu"),
            Map.entry("hun", "hu"),
            Map.entry("czech", "cs"),
            Map.entry("čeština", "cs"),
            Map.entry("cze", "cs"),
            Map.entry("ces", "cs"),
            Map.entry("turkish", "tr"),
            Map.entry("türkçe", "tr"),
            Map.entry("tur", "tr"),
            Map.entry("arabic", "ar"),
            Map.entry("العربية", "ar"),
            Map.entry("ara", "ar"),
            Map.entry("hebrew", "he"),
            Map.entry("עברית", "he"),
            Map.entry("heb", "he"),
            Map.entry("greek", "el"),
            Map.entry("ελληνικά", "el"),
            Map.entry("gre", "el"),
            Map.entry("ell", "el"),
            Map.entry("romanian", "ro"),
            Map.entry("română", "ro"),
            Map.entry("ron", "ro"),
            Map.entry("rum", "ro"),
            Map.entry("thai", "th"),
            Map.entry("tha", "th"),
            Map.entry("vietnamese", "vi"),
            Map.entry("vie", "vi"),
            Map.entry("ukrainian", "uk"),
            Map.entry("ukr", "uk"),
            Map.entry("hindi", "hi"),
            Map.entry("hin", "hi"),
            Map.entry("latin", "la"),
            Map.entry("lat", "la"));
  }

  /**
   * Normalize all metadata fields on a book in-place. Normalizes author names, language code, and
   * identifiers (ISBN cleanup).
   *
   * @param book the book to normalize
   * @return number of fields that were modified
   */
  public static int normalizeAll(Book book) {
    Metadata meta = book.getMetadata();
    int changes = 0;
    changes += normalizeAuthors(meta);
    changes += normalizeLanguage(meta);
    changes += normalizeIdentifiers(meta);
    return changes;
  }

  /**
   * Normalize author names: "Last, First" → "First Last", trim whitespace.
   *
   * @return number of authors modified
   */
  public static int normalizeAuthors(Metadata metadata) {
    int changes = 0;
    for (Author author : metadata.getAuthors()) {
      String first = author.getFirstname();
      String last = author.getLastname();

      // If firstname is blank but lastname has "Last, First" format, split it
      if ((first == null || first.isBlank()) && last != null) {
        Matcher m = LAST_FIRST.matcher(last);
        if (m.matches()) {
          author.setLastname(m.group(1).trim());
          author.setFirstname(m.group(2).trim());
          changes++;
        }
      }

      // Trim whitespace
      if (author.getFirstname() != null) {
        String trimmed = WHITESPACE.matcher(author.getFirstname().trim()).replaceAll(" ");
        if (!trimmed.equals(author.getFirstname())) {
          author.setFirstname(trimmed);
          changes++;
        }
      }
      if (author.getLastname() != null) {
        String trimmed = WHITESPACE.matcher(author.getLastname().trim()).replaceAll(" ");
        if (!trimmed.equals(author.getLastname())) {
          author.setLastname(trimmed);
          changes++;
        }
      }
    }
    return changes;
  }

  /** Convert author name from "First Last" to "Last, First" format. */
  public static String toLastFirst(String name) {
    if (name == null || name.isBlank()) return name;
    name = name.trim();
    // Already in "Last, First" format
    if (name.contains(",")) return name;
    int lastSpace = name.lastIndexOf(' ');
    if (lastSpace <= 0) return name;
    return name.substring(lastSpace + 1) + ", " + name.substring(0, lastSpace);
  }

  /** Convert author name from "Last, First" to "First Last" format. */
  public static String toFirstLast(String name) {
    if (name == null || name.isBlank()) return name;
    Matcher m = LAST_FIRST.matcher(name.trim());
    if (m.matches()) {
      return m.group(2).trim() + " " + m.group(1).trim();
    }
    return name.trim();
  }

  /**
   * Clean an ISBN string by removing dashes, spaces, and validating format.
   *
   * @return cleaned ISBN or null if invalid
   */
  public static String cleanIsbn(String isbn) {
    if (isbn == null) return null;
    String cleaned = NON_ISBN_CHAR_PATTERN.matcher(isbn).replaceAll("").toUpperCase();

    // Reject uniform sequences (e.g. 0000000000)
    if (UNIFORM_DIGIT_PATTERN.matcher(cleaned).matches()) return null;

    if (cleaned.length() == 13 && checkIsbn13(cleaned)) return cleaned;
    if (cleaned.length() == 10 && checkIsbn10(cleaned)) return cleaned;
    return null;
  }

  /** Validate an ISBN-10 check digit. */
  public static boolean isValidIsbn10(String isbn) {
    String cleaned = cleanIsbn(isbn);
    return cleaned != null && cleaned.length() == 10;
  }

  private static boolean checkIsbn10(String isbn) {
    int sum = 0;
    for (int i = 0; i < 9; i++) {
      sum += (isbn.charAt(i) - '0') * (10 - i);
    }
    char check = isbn.charAt(9);
    int checkVal = (check == 'X') ? 10 : (check - '0');
    sum += checkVal;
    return sum % 11 == 0;
  }

  /** Validate an ISBN-13 check digit. */
  public static boolean isValidIsbn13(String isbn) {
    String cleaned = cleanIsbn(isbn);
    return cleaned != null && cleaned.length() == 13;
  }

  private static boolean checkIsbn13(String isbn) {
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      sum += (isbn.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
    }
    int check = (10 - (sum % 10)) % 10;
    return check == (isbn.charAt(12) - '0');
  }

  /** Convert ISBN-10 to ISBN-13. */
  public static String isbn10to13(String isbn10) {
    String cleaned = cleanIsbn(isbn10);
    if (cleaned == null || cleaned.length() != 10) return null;
    String prefix = "978" + cleaned.substring(0, 9);
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      sum += (prefix.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
    }
    int check = (10 - (sum % 10)) % 10;
    return prefix + check;
  }

  /** Convert ISBN-13 to ISBN-10 (only works for 978-prefix ISBNs). */
  public static String isbn13to10(String isbn13) {
    String cleaned = cleanIsbn(isbn13);
    if (cleaned == null || cleaned.length() != 13 || !cleaned.startsWith("978")) return null;
    String core = cleaned.substring(3, 12);
    int sum = 0;
    int mult = 10;
    for (char c : core.toCharArray()) {
      sum += (c - '0') * mult;
      mult--;
    }
    int check = (11 - (sum % 11)) % 11;
    return core + (check == 10 ? "X" : String.valueOf(check));
  }

  /**
   * Normalize identifiers on metadata: clean ISBNs, ensure consistent format.
   *
   * @return number of identifiers modified
   */
  public static int normalizeIdentifiers(Metadata metadata) {
    int changes = 0;
    for (Identifier id : metadata.getIdentifiers()) {
      String value = id.getValue();
      if (value == null) continue;

      // Clean ISBN-like values
      String scheme = id.getScheme();
      if ("isbn".equalsIgnoreCase(scheme) || "ISBN".equals(scheme)) {
        String cleaned = cleanIsbn(value);
        if (cleaned != null && !cleaned.equals(value)) {
          id.setValue(cleaned);
          changes++;
        }
      }
    }
    return changes;
  }

  /**
   * Normalize a language string to ISO 639-1 two-letter code. Handles full language names
   * ("English" → "en"), ISO 639-2/3 codes ("eng" → "en"), and locale strings ("en-US" → "en").
   *
   * @return normalized two-letter code, or the input if unrecognized
   */
  public static String normalizeLanguageCode(String language) {
    if (language == null || language.isBlank()) return null;

    String lower = language.trim().toLowerCase();

    // Already a valid 2-letter code
    if (lower.length() == 2 && ISO_639_1.contains(lower)) return lower;

    // Strip locale suffix (en-US → en, en_GB → en)
    String base = LOCALE_SEPARATOR_PATTERN.split(lower)[0];
    if (base.length() == 2 && ISO_639_1.contains(base)) return base;

    // Check language name map (handles full names and 3-letter codes)
    String mapped = LANGUAGE_NAMES.get(lower);
    if (mapped != null) return mapped;
    mapped = LANGUAGE_NAMES.get(base);
    if (mapped != null) return mapped;

    return language.trim();
  }

  /**
   * Normalize the language field on metadata.
   *
   * @return 1 if language was modified, 0 otherwise
   */
  public static int normalizeLanguage(Metadata metadata) {
    String lang = metadata.getLanguage();
    if (lang == null || lang.isBlank()) return 0;
    String normalized = normalizeLanguageCode(lang);
    if (!normalized.equals(lang)) {
      metadata.setLanguage(normalized);
      return 1;
    }
    return 0;
  }

  /**
   * Parse a date string in any common format to ISO 8601 (yyyy-MM-dd). Tries 10+ date formats.
   * Returns null if unparseable.
   */
  public static String parseDate(String dateStr) {
    if (dateStr == null || dateStr.isBlank()) return null;
    String trimmed = dateStr.trim();

    for (DateTimeFormatter fmt : DATE_FORMATTERS) {
      try {
        LocalDate date = LocalDate.parse(trimmed, fmt);
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
      } catch (DateTimeParseException ignored) {
      }
    }

    // Try year-only as last resort
    if (YEAR_PATTERN.matcher(trimmed).matches()) {
      return trimmed + "-01-01";
    }

    return null;
  }

  /**
   * Normalize text for search: strip diacriticals, handle special characters, lowercase. Ported
   * from Grimmory's BookUtils.normalizeForSearch.
   */
  public static String normalizeForSearch(String text) {
    if (text == null) return null;
    String s = Normalizer.normalize(text, Normalizer.Form.NFD);
    s = DIACRITICAL_MARKS.matcher(s).replaceAll("");
    s =
        s.replace("ø", "o")
            .replace("Ø", "O")
            .replace("ł", "l")
            .replace("Ł", "L")
            .replace("æ", "ae")
            .replace("Æ", "AE")
            .replace("œ", "oe")
            .replace("Œ", "OE")
            .replace("ß", "ss");
    s = WHITESPACE.matcher(s.trim()).replaceAll(" ");
    return s.toLowerCase();
  }
}
