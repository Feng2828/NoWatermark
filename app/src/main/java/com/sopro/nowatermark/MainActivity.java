package com.sopro.nowatermark;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sopro.nowatermark.task.TaskManager;
import com.sopro.nowatermark.task.TaskService;
import com.sopro.nowatermark.ui.ConfirmActivity;
import com.sopro.nowatermark.ui.FolderPickerActivity;
import com.sopro.nowatermark.util.AppLogger;
import com.sopro.nowatermark.util.AppSettings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 主界面：授权、选目录、参数、启动扫描、进度与暂停/取消、日志。 */
public class MainActivity extends AppCompatActivity implements TaskManager.Listener {

    private static final int REQ_INPUT = 101;
    private static final int REQ_OUTPUT = 102;

    private TextView inputPath, outputPath, permissionStatus;
    private TextView sensitivityValue, progressText, logText;
    private SeekBar sensitivityBar;
    private View progressArea;
    private ProgressBar progressBar;
    private Button pauseButton, cancelButton, startScan, grantPermission;
    private ScrollView logScroll;

    private boolean confirmLaunched = false;
    private boolean resultShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        inputPath = findViewById(R.id.inputPath);
        outputPath = findViewById(R.id.outputPath);
        permissionStatus = findViewById(R.id.permissionStatus);
        sensitivityValue = findViewById(R.id.sensitivityValue);
        progressText = findViewById(R.id.progressText);
        logText = findViewById(R.id.logText);
        sensitivityBar = findViewById(R.id.sensitivityBar);
        progressArea = findViewById(R.id.progressArea);
        progressBar = findViewById(R.id.progressBar);
        pauseButton = findViewById(R.id.pauseButton);
        cancelButton = findViewById(R.id.cancelButton);
        startScan = findViewById(R.id.startScan);
        grantPermission = findViewById(R.id.grantPermission);
        logScroll = findViewById(R.id.logScroll);

        final float sens = AppSettings.getSensitivity(this);
        sensitivityBar.setProgress(Math.round(sens * 100));
        updateSensitivityLabel(sens);
        sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = progress / 100f;
                AppSettings.setSensitivity(MainActivity.this, value);
                updateSensitivityLabel(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        grantPermission.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestAllFilesAccess(); }
        });
        findViewById(R.id.chooseInput).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openPicker(AppSettings.getInputDir(MainActivity.this), REQ_INPUT);
            }
        });
        findViewById(R.id.chooseOutput).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openPicker(AppSettings.getOutputDir(MainActivity.this), REQ_OUTPUT);
            }
        });
        startScan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { beginScan(); }
        });
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                TaskManager tm = TaskManager.get();
                if (tm.isPaused()) tm.resume(); else tm.pause();
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { TaskManager.get().cancel(); }
        });
        findViewById(R.id.clearLog).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                TaskManager.get().recentLogs.clear();
                refresh();
            }
        });

        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onStart() {
        super.onStart();
        TaskManager.get().addListener(this);
        refreshPermission();
        refresh();
    }

    @Override
    protected void onStop() {
        TaskManager.get().removeListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        String path = data.getStringExtra(FolderPickerActivity.RESULT_PATH);
        if (path == null) return;
        if (requestCode == REQ_INPUT) {
            AppSettings.setInputDir(this, path);
        } else if (requestCode == REQ_OUTPUT) {
            AppSettings.setOutputDir(this, path);
        }
        refresh();
    }

    @Override public void onUpdate() { refresh(); }

    private void openPicker(String start, int requestCode) {
        Intent intent = new Intent(this, FolderPickerActivity.class);
        intent.putExtra(FolderPickerActivity.EXTRA_START_PATH,
                start != null && start.length() > 0 ? start : "");
        startActivityForResult(intent, requestCode);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 201);
        }
    }

    private boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshPermission() {
        if (hasAllFilesAccess()) {
            permissionStatus.setText(R.string.permission_granted);
            permissionStatus.setTextColor(ContextCompat.getColor(this, R.color.ok));
            grantPermission.setVisibility(View.GONE);
        } else {
            permissionStatus.setText(R.string.permission_not_granted);
            permissionStatus.setTextColor(ContextCompat.getColor(this, R.color.danger));
            grantPermission.setVisibility(View.VISIBLE);
        }
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 200);
        }
    }

    private void beginScan() {
        String in = AppSettings.getInputDir(this);
        String out = AppSettings.getOutputDir(this);
        if (in.isEmpty() || !new File(in).isDirectory()) {
            Toast.makeText(this, R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (out.isEmpty()) {
            Toast.makeText(this, R.string.output_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (outputOverlapsInput(in, out)) {
            Toast.makeText(this, R.string.same_folder_warning, Toast.LENGTH_LONG).show();
            return;
        }
        if (!hasAllFilesAccess()) {
            Toast.makeText(this, R.string.permission_not_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        TaskManager.get().beginScan(in);
        TaskService.start(this, TaskService.ACTION_SCAN);
    }

    private boolean outputOverlapsInput(String in, String out) {
        try {
            String a = new File(in).getCanonicalPath();
            String b = new File(out).getCanonicalPath();
            return a.equalsIgnoreCase(b)
                    || b.startsWith(a + File.separator)
                    || a.startsWith(b + File.separator);
        } catch (IOException e) {
            return in.equals(out);
        }
    }

    private void updateSensitivityLabel(float value) {
        String level = value < 0.34f ? getString(R.string.sensitivity_low)
                : (value > 0.66f ? getString(R.string.sensitivity_high) : "中");
        sensitivityValue.setText(level + "  (" + Math.round(value * 100) + "%)");
    }

    private void refresh() {
        TaskManager tm = TaskManager.get();
        inputPath.setText(valueOrPlaceholder(AppSettings.getInputDir(this)));
        outputPath.setText(valueOrPlaceholder(AppSettings.getOutputDir(this)));

        TaskManager.Phase phase = tm.phase;

        if (phase == TaskManager.Phase.CONFIRM) {
            if (!confirmLaunched) {
                confirmLaunched = true;
                startActivity(new Intent(this, ConfirmActivity.class));
            }
        } else {
            confirmLaunched = false;
        }

        if (phase == TaskManager.Phase.DONE || phase == TaskManager.Phase.CANCELLED) {
            if (!resultShown) {
                resultShown = true;
                if (phase == TaskManager.Phase.DONE) {
                    Toast.makeText(this, getString(R.string.process_done_msg,
                            tm.processed, tm.failed, tm.outputDir), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.cancelled, Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            resultShown = false;
        }

        boolean busy = phase == TaskManager.Phase.SCANNING
                || phase == TaskManager.Phase.PROCESSING
                || phase == TaskManager.Phase.PAUSED;
        progressArea.setVisibility(busy ? View.VISIBLE : View.GONE);
        startScan.setEnabled(!busy);

        if (busy) {
            if (tm.isScanTask()) {
                int max = Math.max(1, tm.total);
                progressBar.setMax(max);
                progressBar.setProgress(Math.min(tm.scanned, max));
                progressText.setText(getString(R.string.scan_progress, tm.scanned, tm.total)
                        + "  ·  " + getString(R.string.scan_found, tm.found));
            } else {
                int done = tm.processed + tm.failed;
                int max = Math.max(1, tm.processTotal);
                progressBar.setMax(max);
                progressBar.setProgress(Math.min(done, max));
                progressText.setText(getString(R.string.process_progress, done, tm.processTotal)
                        + "  ·  成功 " + tm.processed);
            }
            if (phase == TaskManager.Phase.PAUSED) {
                progressText.setText(progressText.getText() + "（" + getString(R.string.paused) + "）");
            }
            pauseButton.setText(phase == TaskManager.Phase.PAUSED
                    ? R.string.resume : R.string.pause);
        }

        List<String> lines;
        synchronized (tm.recentLogs) {
            lines = new ArrayList<String>(tm.recentLogs);
        }
        if (lines.isEmpty()) {
            logText.setText(R.string.empty_log);
        } else {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) sb.append(line).append('\n');
            logText.setText(sb.toString());
            logScroll.post(new Runnable() {
                @Override public void run() { logScroll.fullScroll(View.FOCUS_DOWN); }
            });
        }
    }

    private String valueOrPlaceholder(String value) {
        return value == null || value.isEmpty() ? getString(R.string.folder_not_set) : value;
    }
}
