package com.sopro.nowatermark.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 递归扫描文件夹内的图片，支持几万张规模（只收集路径，不加载图片）。 */
public final class FileScanner {

    private FileScanner() {}

    private static final String[] EXT = {
        ".jpg", ".jpeg", ".png", ".webp", ".bmp", ".heic", ".heif"
    };

    public static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        for (String e : EXT) if (lower.endsWith(e)) return true;
        return false;
    }

    /** 取消标志接口，便于对接任务状态机。 */
    public interface CancelFlag {
        boolean cancelled();
    }

    /**
     * @param cancelled 协作式取消标志，可为 null
     * @param onFile    每找到一个图片回调一次（返回 false 终止），可为 null
     */
    public static List<String> scan(File root, CancelFlag cancelled, FileCallback onFile) {
        List<String> result = new ArrayList<String>();
        walk(root, result, cancelled, onFile);
        return result;
    }

    private static boolean isCancelled(CancelFlag f) {
        return f != null && f.cancelled();
    }

    private static void walk(File dir, List<String> out, CancelFlag cancelled, FileCallback cb) {
        if (isCancelled(cancelled)) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        // 目录优先，保证顺序稳定
        java.util.Arrays.sort(children);
        for (File f : children) {
            if (isCancelled(cancelled)) return;
            if (f.isDirectory()) {
                walk(f, out, cancelled, cb);
            } else if (f.isFile() && isImageFile(f.getName())) {
                String path = f.getAbsolutePath();
                out.add(path);
                if (cb != null && !cb.onFile(path)) return;
            }
        }
    }

    public interface FileCallback {
        boolean onFile(String path);
    }
}
