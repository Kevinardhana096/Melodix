package com.example.melodix;

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

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCover() {
        return cover;
    }

    public String getCoverMedium() {
        return coverMedium;
    }

    public String getReleaseDate() {
        return releaseDate;
    }
}
