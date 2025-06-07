package com.example.melodix.database;

import com.example.melodix.model.Artist;

public class DownloadedArtist extends Artist {
    private long id;
    private String name;
    private String pictureMedium;

    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getPictureMedium() {
        return pictureMedium;
    }
}