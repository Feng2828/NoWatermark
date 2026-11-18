package com.sopro.nowatermark.task;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.sopro.nowatermark.MainActivity;
import com.sopro.nowatermark.R;
import com.sopro.nowatermark.core.FileScanner;
import com.sopro.nowatermark.core.ExifHelper;
import com.sopro.nowatermark.core.ImageInfo;
import com.sopro.nowatermark.core.ImageProcessor;
import com.sopro.nowatermark.core.WatermarkDetector;
import com.sopro.nowatermark.core.algo.Region;
import com.sopro.nowatermark.core.algo.WatermarkAnalyzer;
import com.sopro.nowatermark.util.AppSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 前台服务：在后台线程执行扫描或去水印批处理，长时间运行不被系统回收。
 * 进度写入 TaskManager，通知栏实时显示；暂停 / 取消通过 TaskManager 协作式标志。
 */
public class TaskService extends Service {

    public static final String ACTION_SCAN = "scan";
    public static final String ACTION_PROCESS = "process";
    public static final String EXTRA_ACTION = "action";

    private static final String CHANNEL_ID = "nowatermark_task";
    private static final int NOTIF_ID = 1001;

    private ExecutorService executor;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getStringExtra(EXTRA_ACTION) : null;
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification initial = buildNotification(
                getString(R.string.notification_channel), getString(R.string.scanning), 0, 0);
        startForegroundCompat(initial);

        final String finalAction = action;
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (ACTION_SCAN.equals(finalAction)) runScan();
                    else if (ACTION_PROCESS.equals(finalAction)) runProcess();
                } finally {
                    stopSelf();
                }
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ---------------- 扫描 ----------------

    private void runScan() {
        final TaskManager tm = TaskManager.get();
        String inputDir = AppSettings.getInputDir(this);
        float sens = AppSettings.getSensitivity(this);
        tm.log(this, getString(R.string.log_scan_start, inputDir));

        List<String> files = FileScanner.scan(
                new File(inputDir),
                new FileScanner.CancelFlag() {
                    @Override public boolean cancelled() { return tm.isCancelled(); }
                },
                null);
        tm.total = files.size();
        tm.notifyState();

        for (int i = 0; i < files.size(); i++) {
            if (!tm.waitIfPaused()) break;
            String path = files.get(i);
            tm.scanned = i + 1;
            tm.currentFile = new File(path).getName();

            WatermarkAnalyzer.Result result = WatermarkDetector.detect(path, sens);
            if (result.hasWatermark) {
                ImageInfo info = new ImageInfo(path);
                info.dateText = ExifHelper.readShotDate(path);
                info.hasWatermark = true;
                info.regions = result.regions;
                tm.watermarked.add(info);
                tm.found = tm.watermarked.size();
                tm.log(this, getString(R.string.log_detected, info.name));
            }

            if (i % 15 == 0 || i == files.size() - 1) {
                updateNotification(getString(R.string.scanning),
                        getString(R.string.scan_progress, tm.scanned, tm.total), tm.scanned, tm.total);
                tm.notifyState();
            }
        }

        if (tm.isCancelled()) {
            tm.log(this, getString(R.string.log_cancelled));
            tm.setPhase(TaskManager.Phase.CANCELLED);
            updateNotification(getString(R.string.cancelled), "", 0, 0);
        } else {
            tm.log(this, getString(R.string.log_scan_done, tm.total, tm.found));
            tm.setPhase(TaskManager.Phase.CONFIRM);
            updateNotification(getString(R.string.scan_done_title),
                    getString(R.string.scan_found, tm.found), tm.total, tm.total);
        }
        tm.notifyState();
    }

    // ---------------- 去水印 ----------------

    private void runProcess() {
        final TaskManager tm = TaskManager.get();
        int radius = AppSettings.getRadius(this);
        List<ImageInfo> selected = new ArrayList<ImageInfo>(tm.selected);

        for (int i = 0; i < selected.size(); i++) {
            if (!tm.waitIfPaused()) break;
            ImageInfo info = selected.get(i);
            tm.currentFile = info.name;

            String outPath = tm.outputPathFor(info.path);
            ImageProcessor.Result result =
                    ImageProcessor.process(info.path, outPath, info.regions, radius);
            if (result.ok) {
                tm.processed++;
                tm.log(this, getString(R.string.log_process_ok, info.name));
            } else {
                tm.failed++;
                tm.log(this, getString(R.string.log_process_fail, info.name, result.message));
            }

            if (i % 5 == 0 || i == selected.size() - 1) {
                updateNotification(getString(R.string.processing),
                        getString(R.string.process_progress, tm.processed + tm.failed, tm.processTotal),
                        tm.processed + tm.failed, tm.processTotal);
                tm.notifyState();
            }
        }

        if (tm.isCancelled()) {
            tm.log(this, getString(R.string.log_cancelled));
            tm.setPhase(TaskManager.Phase.CANCELLED);
            updateNotification(getString(R.string.cancelled), "", 0, 0);
        } else {
            tm.setPhase(TaskManager.Phase.DONE);
            updateNotification(getString(R.string.process_done_title),
                    getString(R.string.process_done_msg, tm.processed, tm.failed, tm.outputDir),
                    tm.processed + tm.failed, tm.processTotal);
        }
        tm.notifyState();
    }

    // ---------------- 通知 ----------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel));
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String title, String text, int progress, int max) {
        Intent open = new Intent(this, MainActivity.class);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0, open, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(pi);
        if (max > 0) builder.setProgress(max, progress, false);
        return builder.build();
    }

    private void updateNotification(String title, String text, int progress, int max) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIF_ID, buildNotification(title, text, progress, max));
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 / API 34
            androidx.core.app.ServiceCompat.startForeground(this, NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    /** 供外部代码启动服务（保持调用点一致）。 */
    public static void start(Context context, String action) {
        Intent intent = new Intent(context, TaskService.class);
        intent.putExtra(EXTRA_ACTION, action);
        ContextCompat.startForegroundService(context, intent);
    }
}
