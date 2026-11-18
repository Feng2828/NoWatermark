package com.sopro.nowatermark.core;

import com.sopro.nowatermark.core.algo.Region;

import java.util.List;

/** 一张图片在扫描/确认流程中的信息。 */
public final class ImageInfo {
    public final String path;
    public final String name;
    public String dateText;
    public boolean hasWatermark;
    public List<Region> regions;
    public boolean selected;

    public ImageInfo(String path) {
        this.path = path;
        this.name = new java.io.File(path).getName();
        this.dateText = "";
        this.hasWatermark = false;
        this.selected = false;
    }
}
