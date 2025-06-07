package com.example.melodix.model;

import com.google.gson.annotations.SerializedName;

public class Artist {
    @SerializedName("id")
    private long id;
    @SerializedName("name")
    private String name;
    @SerializedName("picture")
    private String picture;
    @SerializedName("picture_medium")
    private String pictureMedium;
    public long getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getPictureMedium() {
        return pictureMedium;
    }
}
