package com.sopro.nowatermark.core.algo;

/**
 * 水印区域：归一化坐标 (0..1)，便于从缩略图映射回原图分辨率。
 */
public final class Region implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public final float left;   // 0..1
    public final float top;    // 0..1
    public final float right;  // 0..1
    public final float bottom; // 0..1
    public final float score;  // 置信度

    public Region(float left, float top, float right, float bottom, float score) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.score = score;
    }

    @Override
    public String toString() {
        return "Region[" + left + "," + top + "," + right + "," + bottom + "] score=" + score;
    }
}
