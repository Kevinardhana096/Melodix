package com.example.melodix;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ChartResponse {
    @SerializedName("tracks")
    private Data<Track> tracks;

    @SerializedName("albums")
    private Data<Album> albums;

    @SerializedName("artists")
    private Data<Artist> artists;

    public Data<Track> getTracks() {
        return tracks;
    }

    public Data<Album> getAlbums() {
        return albums;
    }

    public Data<Artist> getArtists() {
        return artists;
    }

    public static class Data<T> {
        @SerializedName("data")
        private List<T> items;

        public List<T> getItems() {
            return items;
        }
    }
}
