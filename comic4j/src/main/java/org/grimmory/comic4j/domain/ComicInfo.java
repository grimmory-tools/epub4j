/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Grimmory contributors
 * Copyright (C) 2025-2026 Booklore contributors
 */
package org.grimmory.comic4j.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive ComicInfo model representing the superset of all fields from the ComicInfo.xml
 * specification and common extensions.
 *
 * <p>Standard fields follow the ComicInfo.xml specification. Extension fields (localizedSeries,
 * seriesSort, titleSort) are widely used by modern comic managers.
 *
 * <p>All string creator fields (writer, penciller, etc.) use comma-separated values for multiple
 * entries.
 */
public class ComicInfo {

  // --- Core identification ---
  private String title;
  private String series;
  private String number;
  private Integer count;
  private Integer volume;

  // --- Alternate series ---
  private String alternateSeries;
  private String alternateNumber;
  private Integer alternateCount;

  // --- Description ---
  private String summary;
  private String notes;
  private String review;

  // --- Publication date ---
  private Integer year;
  private Integer month;
  private Integer day;

  // --- Creators (comma-separated) ---
  private String writer;
  private String penciller;
  private String inker;
  private String colorist;
  private String letterer;
  private String coverArtist;
  private String editor;
  private String translator;

  // --- Publisher ---
  private String publisher;
  private String imprint;

  // --- Classification ---
  private String genre;
  private String tags;
  private String web;
  private Integer pageCount;
  private String languageISO;
  private String format;
  private YesNo blackAndWhite;
  private ReadingDirection manga;
  private AgeRating ageRating;
  private Float communityRating;

  // --- Content details ---
  private String characters;
  private String teams;
  private String locations;
  private String mainCharacterOrTeam;
  private String scanInformation;

  // --- Story arcs ---
  private String storyArc;
  private String storyArcNumber;
  private String seriesGroup;

  // --- Identifiers ---
  private String gtin;

  // --- Pages ---
  private List<ComicPage> pages = new ArrayList<>();

  // --- Extensions (non-standard but widely used) ---
  private String localizedSeries;
  private String seriesSort;
  private String titleSort;

  // --- Getters and setters ---

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSeries() {
    return series;
  }

  public void setSeries(String series) {
    this.series = series;
  }

  public String getNumber() {
    return number;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public Integer getVolume() {
    return volume;
  }

  public void setVolume(Integer volume) {
    this.volume = volume;
  }

  public String getAlternateSeries() {
    return alternateSeries;
  }

  public void setAlternateSeries(String alternateSeries) {
    this.alternateSeries = alternateSeries;
  }

  public String getAlternateNumber() {
    return alternateNumber;
  }

  public void setAlternateNumber(String alternateNumber) {
    this.alternateNumber = alternateNumber;
  }

  public Integer getAlternateCount() {
    return alternateCount;
  }

  public void setAlternateCount(Integer alternateCount) {
    this.alternateCount = alternateCount;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getReview() {
    return review;
  }

  public void setReview(String review) {
    this.review = review;
  }

  public Integer getYear() {
    return year;
  }

  public void setYear(Integer year) {
    this.year = year;
  }

  public Integer getMonth() {
    return month;
  }

  public void setMonth(Integer month) {
    this.month = month;
  }

  public Integer getDay() {
    return day;
  }

  public void setDay(Integer day) {
    this.day = day;
  }

  public String getWriter() {
    return writer;
  }

  public void setWriter(String writer) {
    this.writer = writer;
  }

  public String getPenciller() {
    return penciller;
  }

  public void setPenciller(String penciller) {
    this.penciller = penciller;
  }

  public String getInker() {
    return inker;
  }

  public void setInker(String inker) {
    this.inker = inker;
  }

  public String getColorist() {
    return colorist;
  }

  public void setColorist(String colorist) {
    this.colorist = colorist;
  }

  public String getLetterer() {
    return letterer;
  }

  public void setLetterer(String letterer) {
    this.letterer = letterer;
  }

  public String getCoverArtist() {
    return coverArtist;
  }

  public void setCoverArtist(String coverArtist) {
    this.coverArtist = coverArtist;
  }

  public String getEditor() {
    return editor;
  }

  public void setEditor(String editor) {
    this.editor = editor;
  }

  public String getTranslator() {
    return translator;
  }

  public void setTranslator(String translator) {
    this.translator = translator;
  }

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String publisher) {
    this.publisher = publisher;
  }

  public String getImprint() {
    return imprint;
  }

  public void setImprint(String imprint) {
    this.imprint = imprint;
  }

  public String getGenre() {
    return genre;
  }

  public void setGenre(String genre) {
    this.genre = genre;
  }

  public String getTags() {
    return tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  public String getWeb() {
    return web;
  }

  public void setWeb(String web) {
    this.web = web;
  }

  public Integer getPageCount() {
    return pageCount;
  }

  public void setPageCount(Integer pageCount) {
    this.pageCount = pageCount;
  }

  public String getLanguageISO() {
    return languageISO;
  }

  public void setLanguageISO(String languageISO) {
    this.languageISO = languageISO;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public YesNo getBlackAndWhite() {
    return blackAndWhite;
  }

  public void setBlackAndWhite(YesNo blackAndWhite) {
    this.blackAndWhite = blackAndWhite;
  }

  public ReadingDirection getManga() {
    return manga;
  }

  public void setManga(ReadingDirection manga) {
    this.manga = manga;
  }

  public AgeRating getAgeRating() {
    return ageRating;
  }

  public void setAgeRating(AgeRating ageRating) {
    this.ageRating = ageRating;
  }

  public Float getCommunityRating() {
    return communityRating;
  }

  public void setCommunityRating(Float communityRating) {
    this.communityRating = communityRating;
  }

  public String getCharacters() {
    return characters;
  }

  public void setCharacters(String characters) {
    this.characters = characters;
  }

  public String getTeams() {
    return teams;
  }

  public void setTeams(String teams) {
    this.teams = teams;
  }

  public String getLocations() {
    return locations;
  }

  public void setLocations(String locations) {
    this.locations = locations;
  }

  public String getMainCharacterOrTeam() {
    return mainCharacterOrTeam;
  }

  public void setMainCharacterOrTeam(String mainCharacterOrTeam) {
    this.mainCharacterOrTeam = mainCharacterOrTeam;
  }

  public String getScanInformation() {
    return scanInformation;
  }

  public void setScanInformation(String scanInformation) {
    this.scanInformation = scanInformation;
  }

  public String getStoryArc() {
    return storyArc;
  }

  public void setStoryArc(String storyArc) {
    this.storyArc = storyArc;
  }

  public String getStoryArcNumber() {
    return storyArcNumber;
  }

  public void setStoryArcNumber(String storyArcNumber) {
    this.storyArcNumber = storyArcNumber;
  }

  public String getSeriesGroup() {
    return seriesGroup;
  }

  public void setSeriesGroup(String seriesGroup) {
    this.seriesGroup = seriesGroup;
  }

  public String getGtin() {
    return gtin;
  }

  public void setGtin(String gtin) {
    this.gtin = gtin;
  }

  public List<ComicPage> getPages() {
    return List.copyOf(pages);
  }

  public void setPages(List<ComicPage> pages) {
    this.pages = new ArrayList<>(pages);
  }

  public void addPage(ComicPage page) {
    this.pages.add(page);
  }

  public String getLocalizedSeries() {
    return localizedSeries;
  }

  public void setLocalizedSeries(String localizedSeries) {
    this.localizedSeries = localizedSeries;
  }

  public String getSeriesSort() {
    return seriesSort;
  }

  public void setSeriesSort(String seriesSort) {
    this.seriesSort = seriesSort;
  }

  public String getTitleSort() {
    return titleSort;
  }

  public void setTitleSort(String titleSort) {
    this.titleSort = titleSort;
  }
}
