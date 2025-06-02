package com.example.melodix;
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

    // Exclude context from serialization
    @Expose(serialize = false, deserialize = false)
    private transient Context context;

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getDuration() {
        return duration;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public Artist getArtist() {
        return artist;
    }

    public Album getAlbum() {
        return album;
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
}
