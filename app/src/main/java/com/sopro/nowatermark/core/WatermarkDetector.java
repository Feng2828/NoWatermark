package com.sopro.nowatermark.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.sopro.nowatermark.core.algo.WatermarkAnalyzer;

/** Android 层检测封装：负责解码缩略图并交给纯 Java 分析器。 */
public final class WatermarkDetector {

    private WatermarkDetector() {}

    private static final int DETECT_WORK_SIDE = 1000;

    public static WatermarkAnalyzer.Result detect(String path, float sensitivity) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        int sample = calcSample(opts.outWidth, opts.outHeight, DETECT_WORK_SIDE);

        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = null;
        try {
            bmp = BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError ignored) {
            bmp = null;
        }
        if (bmp == null) {
            return new WatermarkAnalyzer.Result(false, new java.util.ArrayList<com.sopro.nowatermark.core.algo.Region>(), 0f);
        }
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        bmp.recycle();
        return WatermarkAnalyzer.analyze(pixels, w, h, sensitivity);
    }

    /** 列表/预览用的缩略图。 */
    public static Bitmap loadThumbnail(String path, int maxSide) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        int sample = calcSample(opts.outWidth, opts.outHeight, maxSide);
        opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static int calcSample(int w, int h, int maxSide) {
        if (w <= 0 || h <= 0) return 1;
        int sample = 1;
        long max = Math.max(w, h);
        while (max / sample > maxSide) sample *= 2;
        return sample;
    }
}
