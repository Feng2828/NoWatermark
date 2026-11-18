package com.sopro.nowatermark.task;

import com.sopro.nowatermark.core.ImageInfo;
import com.sopro.nowatermark.util.AppLogger;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 全局任务状态机。Service 在后台线程更新，Activity 注册监听器刷新 UI。
 * 用 volatile 标志实现协作式暂停 / 取消（后台线程在每个安全点检查）。
 */
public final class TaskManager {

    public enum Phase { IDLE, SCANNING, CONFIRM, PROCESSING, PAUSED, DONE, CANCELLED }

    private static final TaskManager INSTANCE = new TaskManager();

    public volatile Phase phase = Phase.IDLE;
    public volatile int total = 0;
    public volatile int scanned = 0;
    public volatile int found = 0;
    public volatile int processTotal = 0;
    public volatile int processed = 0;
    public volatile int failed = 0;
    public volatile String currentFile = "";
    public volatile String inputDir = "";
    public volatile String outputDir = "";

    /** 扫描出有水印的图片（全部）。 */
    public final List<ImageInfo> watermarked =
            Collections.synchronizedList(new ArrayList<ImageInfo>());
    /** 用户勾选、待处理的图片。 */
    public final List<ImageInfo> selected =
            Collections.synchronizedList(new ArrayList<ImageInfo>());
    /** 界面显示的最近日志（最多 300 条）。 */
    public final List<String> recentLogs =
            Collections.synchronizedList(new ArrayList<String>());

    private volatile boolean paused = false;
    private volatile boolean cancelled = false;
    private volatile boolean scanTask = true;
    private volatile Phase phaseBeforePause = Phase.IDLE;

    private final List<Listener> listeners =
            new java.util.concurrent.CopyOnWriteArrayList<Listener>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable notifyRunnable = new Runnable() {
        @Override public void run() {
            for (Listener l : listeners) l.onUpdate();
        }
    };

    private TaskManager() {}

    public static TaskManager get() { return INSTANCE; }

    public interface Listener {
        void onUpdate();
    }

    public void addListener(Listener l) { if (l != null) listeners.add(l); }
    public void removeListener(Listener l) { if (l != null) listeners.remove(l); }

    /** 合并短时间内的多次更新，避免 UI 刷新过密。 */
    public void notifyState() {
        mainHandler.removeCallbacks(notifyRunnable);
        mainHandler.post(notifyRunnable);
    }

    public void beginScan(String inputDir) {
        this.inputDir = inputDir;
        this.total = this.scanned = this.found = 0;
        this.processTotal = this.processed = this.failed = 0;
        this.currentFile = "";
        this.paused = false;
        this.cancelled = false;
        this.scanTask = true;
        this.watermarked.clear();
        this.selected.clear();
        this.recentLogs.clear();
        setPhase(Phase.SCANNING);
    }

    public void beginProcess(String inputDir, String outputDir, int count) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.processTotal = count;
        this.processed = this.failed = 0;
        this.currentFile = "";
        this.paused = false;
        this.cancelled = false;
        this.scanTask = false;
        setPhase(Phase.PROCESSING);
    }

    public void setPhase(Phase p) { this.phase = p; notifyState(); }

    public void pause() {
        if (paused) return;
        paused = true;
        phaseBeforePause = phase;
        setPhase(Phase.PAUSED);
    }

    public void resume() {
        if (!paused) return;
        paused = false;
        setPhase(scanTask ? Phase.SCANNING : Phase.PROCESSING);
    }

    public void cancel() {
        cancelled = true;
        paused = false;
        setPhase(Phase.CANCELLED);
    }

    public boolean isPaused() { return paused; }
    public boolean isCancelled() { return cancelled; }
    public boolean isScanTask() { return scanTask; }

    /**
     * 后台循环调用：暂停时阻塞等待，取消时返回 false。
     */
    public boolean waitIfPaused() {
        while (paused && !cancelled) {
            try { Thread.sleep(120); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !cancelled;
    }

    public void log(Context context, String message) {
        AppLogger.log(message);
        synchronized (recentLogs) {
            recentLogs.add(message);
            while (recentLogs.size() > 300) recentLogs.remove(0);
        }
        notifyState();
    }

    /** 生成输出路径：保留相对子目录，文件名与原图一致，绝不覆盖原图。 */
    public String outputPathFor(String srcPath) {
        String rel = srcPath;
        if (inputDir != null && srcPath.startsWith(inputDir)) {
            rel = srcPath.substring(inputDir.length());
            while (rel.startsWith("/") || rel.startsWith(java.io.File.separator)) {
                rel = rel.substring(1);
            }
        }
        String base = outputDir == null ? "" : outputDir;
        while (base.endsWith("/") || base.endsWith(java.io.File.separator)) {
            base = base.substring(0, base.length() - 1);
        }
        return base + java.io.File.separator + rel;
    }

}
