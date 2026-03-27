package org.grimmory.epub4j.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.grimmory.epub4j.epub.PackageDocumentBase;

/**
 * A Date used by the book's metadata.
 *
 * <p>Examples: creation-date, modification-date, etc
 *
 * @author paul
 */
public class Date implements Serializable {

  @Serial private static final long serialVersionUID = 7533866830395120136L;

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(PackageDocumentBase.dateFormat);

  public enum Event {
    PUBLICATION("publication"),
    MODIFICATION("modification"),
    CREATION("creation");

    private final String value;

    Event(String v) {
      value = v;
    }

    public static Event fromValue(String v) {
      for (Event c : Event.values()) {
        if (c.value.equals(v)) {
          return c;
        }
      }
      return null;
    }

    public String toString() {
      return value;
    }
  }

  private Event event;
  private String dateString;

  public Date(java.util.Date date) {
    this(date, (Event) null);
  }

  public Date(ZonedDateTime date) {
    this(date, (Event) null);
  }

  public Date(Instant instant) {
    this(instant, (Event) null);
  }

  public Date(String dateString) {
    this(dateString, (Event) null);
  }

  public Date(java.util.Date date, Event event) {
    this(ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()), event);
  }

  public Date(ZonedDateTime date, Event event) {
    this(date.format(DATE_TIME_FORMATTER), event);
  }

  public Date(Instant instant, Event event) {
    this(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()), event);
  }

  public Date(String dateString, Event event) {
    this.dateString = dateString;
    this.event = event;
  }

  public Date(java.util.Date date, String event) {
    this(ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()), event);
  }

  public Date(ZonedDateTime date, String event) {
    this(date.format(DATE_TIME_FORMATTER), event);
  }

  public Date(Instant instant, String event) {
    this(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()), event);
  }

  public Date(String dateString, String event) {
    this(checkDate(dateString), Event.fromValue(event));
    this.dateString = dateString;
  }

  private static String checkDate(String dateString) {
    if (dateString == null) {
      throw new IllegalArgumentException("Cannot create a date from a blank string");
    }
    return dateString;
  }

  public String getValue() {
    return dateString;
  }

  public Event getEvent() {
    return event;
  }

  public void setEvent(Event event) {
    this.event = event;
  }

  public String toString() {
    if (event == null) {
      return dateString;
    }
    return event + ":" + dateString;
  }
}
