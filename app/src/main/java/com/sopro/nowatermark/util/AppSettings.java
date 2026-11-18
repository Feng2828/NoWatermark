package com.sopro.nowatermark.util;

import android.content.Context;
import android.content.SharedPreferences;

/** 用户设置持久化。命名为 AppSettings 以避免与 android.provider.Settings 冲突。 */
public final class AppSettings {

    private AppSettings() {}

    private static final String PREF = "nowatermark_settings";
    private static final String KEY_INPUT = "input_dir";
    private static final String KEY_OUTPUT = "output_dir";
    private static final String KEY_SENS = "sensitivity";
    private static final String KEY_RADIUS = "radius";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String getInputDir(Context c) {
        return prefs(c).getString(KEY_INPUT, "");
    }

    public static void setInputDir(Context c, String v) {
        prefs(c).edit().putString(KEY_INPUT, v == null ? "" : v).apply();
    }

    public static String getOutputDir(Context c) {
        return prefs(c).getString(KEY_OUTPUT, "");
    }

    public static void setOutputDir(Context c, String v) {
        prefs(c).edit().putString(KEY_OUTPUT, v == null ? "" : v).apply();
    }

    /** 0..1，默认 0.5。 */
    public static float getSensitivity(Context c) {
        return prefs(c).getFloat(KEY_SENS, 0.5f);
    }

    public static void setSensitivity(Context c, float v) {
        prefs(c).edit().putFloat(KEY_SENS, v).apply();
    }

    /** inpaint 半径，默认 5。 */
    public static int getRadius(Context c) {
        return prefs(c).getInt(KEY_RADIUS, 5);
    }

    public static void setRadius(Context c, int v) {
        prefs(c).edit().putInt(KEY_RADIUS, v).apply();
    }
}
