package com.example.melodix.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SearchResponse {
    @SerializedName("data")
    private List<Track> tracks;

    @SerializedName("total")
    private int total;

    @SerializedName("next")
    private String next;

    public List<Track> getTracks() {
        return tracks;
    }

    public int getTotal() {
        return total;
    }

    public String getNext() {
        return next;
    }
}
