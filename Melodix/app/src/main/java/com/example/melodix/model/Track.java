package com.example.melodix.model;
import android.content.Context;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Expose;

public class Track {
    @SerializedName("id")
    private long id;

    @SerializedName("title")
    private String title;

    @SerializedName("duration")
    private int duration;

    @SerializedName("preview")
    private String previewUrl;

    @SerializedName("artist")
    private Artist artist;

    @SerializedName("album")
    private Album album;

    // Type field to distinguish between tracks, artists, and albums
    private String type;

    // Exclude context from serialization
    @Expose(serialize = false, deserialize = false)
    private transient Context context;

    /**
     * Create a deep copy of the track
     */
    public Track copy() {
        Track copy = new Track();
        copy.id = this.id;
        copy.title = this.title;
        copy.duration = this.duration;
        copy.previewUrl = this.previewUrl;
        copy.artist = this.artist;
        copy.album = this.album;
        copy.type = this.type;
        copy.context = this.context;
        return copy;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getDuration() {
        return duration;
    }

    // Add setter for duration
    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    // Add setter for previewUrl
    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public Artist getArtist() {
        return artist;
    }

    public Album getAlbum() {
        return album;
    }

    // Getter and setter for type
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    // Add setter for id
    public void setId(long id) {
        this.id = id;
    }

    // Add setter for title
    public void setTitle(String title) {
        this.title = title;
    }

    // Add setter for artist
    public void setArtist(Artist artist) {
        this.artist = artist;
    }

    // Add setter for album to allow updating album details
    public void setAlbum(Album album) {
        this.album = album;
    }

    // Getters and setters for context
    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public boolean isDownloaded() {
        return false; // Default tracks are not downloaded
    }
}
