package com.example.melodix.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AlbumSearchResponse {
    @SerializedName("data")
    private List<Album> albums;

    public List<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }
}
