package com.example.melodix.database;

import com.example.melodix.model.Album;

public class DownloadedAlbum extends Album {
    private long id;
    private String title;
    private String cover;
    private String coverMedium;

    @Override
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    @Override
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    @Override
    public String getCoverMedium() {
        return coverMedium;
    }
    public void setCoverMedium(String coverMedium) {
        this.coverMedium = coverMedium;
    }
}