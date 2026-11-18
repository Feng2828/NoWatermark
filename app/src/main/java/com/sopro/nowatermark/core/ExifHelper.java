package com.sopro.nowatermark.core;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** 读取图片拍摄日期：优先 DateTimeOriginal，回退到文件修改时间。 */
public final class ExifHelper {

    private ExifHelper() {}

    public static String readShotDate(String path) {
        try {
            ExifInterface exif = new ExifInterface(path);
            String raw = firstNonEmpty(
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                    exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED),
                    exif.getAttribute(ExifInterface.TAG_DATETIME));
            if (raw != null) {
                String formatted = parseExifDate(raw);
                if (formatted != null) return formatted;
            }
        } catch (Throwable ignored) {
            // 损坏文件或不支持的格式：回退文件时间
        }
        long ms = new File(path).lastModified();
        if (ms > 0) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new java.util.Date(ms));
        }
        return "日期未知";
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && v.trim().length() > 0) return v.trim();
        return null;
    }

    /** EXIF 格式 yyyy:MM:dd HH:mm:ss -> yyyy-MM-dd HH:mm */
    private static String parseExifDate(String raw) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
            java.util.Date d = in.parse(raw);
            if (d == null) return null;
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(d);
        } catch (ParseException e) {
            return null;
        }
    }
}
