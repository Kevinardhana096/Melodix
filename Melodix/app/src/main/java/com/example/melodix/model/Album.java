package com.example.melodix.model;

import com.google.gson.annotations.SerializedName;

public class Album {
    @SerializedName("id")
    private long id;

    @SerializedName("title")
    private String title;

    @SerializedName("cover")
    private String cover;

    @SerializedName("cover_medium")
    private String coverMedium;

    @SerializedName("release_date")
    private String releaseDate;

    @SerializedName("artist")
    private Artist artist;

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCover() {
        return cover;
    }

    public String getCoverMedium() {
        return coverMedium;
    }

    public void setCoverMedium(String coverMedium) {
        this.coverMedium = coverMedium;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public Artist getArtist() {
        return artist;
    }

    public void setArtist(Artist artist) {
        this.artist = artist;
    }
}
