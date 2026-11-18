package com.sopro.nowatermark.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.sopro.nowatermark.core.algo.Region;
import com.sopro.nowatermark.core.algo.TeleaInpainter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 单张图片去水印：按原分辨率解码 -> 仅在 mask 区域修复 -> 高质量重新编码到输出文件。
 * 不覆盖原图；原图分辨率与尺寸保持不变；未勾选、未检出水印的图片不会进入这里。
 */
public final class ImageProcessor {

    private ImageProcessor() {}

    public static final class Result {
        public final boolean ok;
        public final String message;
        Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    public static Result process(String srcPath, String outPath, List<Region> regions, int radius) {
        try {
            File srcFile = new File(srcPath);
            File outFile = new File(outPath);
            if (srcFile.getCanonicalPath().equalsIgnoreCase(outFile.getCanonicalPath())) {
                return new Result(false, "输出路径与原图相同，已拒绝");
            }
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return new Result(false, "无法创建输出目录");
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap;
            try {
                bitmap = BitmapFactory.decodeFile(srcPath, opts);
            } catch (OutOfMemoryError e) {
                return new Result(false, "内存不足，图片过大");
            }
            if (bitmap == null) return new Result(false, "无法解码图片");

            int w = bitmap.getWidth(), h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

            boolean[] mask = MaskBuilder.build(w, h, regions);
            TeleaInpainter.inpaint(pixels, w, h, mask, radius);

            // 复用已解码的位图写回，避免同时存在两张全尺寸位图，降低大图 OOM 风险
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
            boolean saved = compress(bitmap, outPath, srcPath);
            bitmap.recycle();
            return saved ? new Result(true, "OK") : new Result(false, "保存失败");
        } catch (Throwable t) {
            return new Result(false, String.valueOf(t.getMessage()));
        }
    }

    private static boolean compress(Bitmap bitmap, String outPath, String srcPath) {
        String lower = srcPath.toLowerCase(Locale.US);
        Bitmap.CompressFormat fmt;
        int quality = 100;
        if (lower.endsWith(".png")) {
            fmt = Bitmap.CompressFormat.PNG; // PNG 无损，其余像素逐位不变
        } else if (lower.endsWith(".webp")) {
            fmt = Bitmap.CompressFormat.WEBP;
        } else {
            fmt = Bitmap.CompressFormat.JPEG; // 保持原扩展名，最高质量重编码
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(outPath);
            return bitmap.compress(fmt, quality, fos);
        } catch (IOException e) {
            return false;
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }
    }
}
