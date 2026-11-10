package com.example.spotifyclone.data.model;

import com.google.gson.annotations.SerializedName;

public class Genre {
    @SerializedName("_id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("slug")
    private String slug;

    @SerializedName("colorCode")
    private String colorCode;

    @SerializedName("iconUrl")
    private String iconUrl;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getColorCode() { return colorCode; }
    public String getIconUrl() { return iconUrl; }
}
