package com.sopro.nowatermark.util;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 简单日志：写入应用私有目录，同时可通过 TaskManager 在界面查看。 */
public final class AppLogger {

    private AppLogger() {}

    private static File logFile;
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    public static synchronized void init(Context context) {
        if (logFile != null) return;
        File dir = new File(context.getExternalFilesDir(null), "logs");
        if (!dir.exists()) dir.mkdirs();
        logFile = new File(dir, "process.log");
    }

    public static synchronized void log(String message) {
        if (logFile == null) return;
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            writer.write(TIME.format(new Date()) + "  " + message);
            writer.newLine();
            writer.close();
        } catch (Throwable ignored) {
            // 日志失败不影响主流程
        }
    }

    public static File getLogFile(Context context) {
        init(context);
        return logFile;
    }
}
