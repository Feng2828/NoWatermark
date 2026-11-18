package com.sopro.nowatermark.core;

import com.sopro.nowatermark.core.algo.Region;

import java.util.List;

/** 把归一化的水印区域转换成原图分辨率的像素 mask。 */
public final class MaskBuilder {

    private MaskBuilder() {}

    public static boolean[] build(int w, int h, List<Region> regions) {
        boolean[] mask = new boolean[w * h];
        if (regions == null) return mask;
        for (Region r : regions) {
            int x0 = clamp((int) Math.floor(r.left * w), 0, w - 1);
            int x1 = clamp((int) Math.ceil(r.right * w), 0, w - 1);
            int y0 = clamp((int) Math.floor(r.top * h), 0, h - 1);
            int y1 = clamp((int) Math.ceil(r.bottom * h), 0, h - 1);
            for (int y = y0; y <= y1; y++) {
                int row = y * w;
                for (int x = x0; x <= x1; x++) mask[row + x] = true;
            }
        }
        return mask;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
