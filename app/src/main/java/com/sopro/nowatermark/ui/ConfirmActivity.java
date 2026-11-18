package com.sopro.nowatermark.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sopro.nowatermark.R;
import com.sopro.nowatermark.core.ImageInfo;
import com.sopro.nowatermark.core.WatermarkDetector;
import com.sopro.nowatermark.core.algo.Region;
import com.sopro.nowatermark.task.TaskManager;
import com.sopro.nowatermark.task.TaskService;
import com.sopro.nowatermark.util.AppSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 确认界面：只列出判定有水印的图片，显示拍摄日期，必须人工勾选后才处理。 */
public class ConfirmActivity extends AppCompatActivity {

    private static final LruCache<String, Bitmap> THUMB_CACHE = new LruCache<String, Bitmap>(80);
    private static final ExecutorService THUMB_LOADER = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ImageAdapter adapter;
    private TextView countText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TaskManager tm = TaskManager.get();
        List<ImageInfo> items = new ArrayList<ImageInfo>(tm.watermarked);
        countText = findViewById(R.id.countText);
        countText.setText(getString(R.string.watermarked_count, items.size()));

        adapter = new ImageAdapter(items);
        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        findViewById(R.id.selectAll).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adapter.setAll(true); }
        });
        findViewById(R.id.deselectAll).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adapter.setAll(false); }
        });
        findViewById(R.id.invert).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adapter.invert(); }
        });
        findViewById(R.id.startProcess).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<ImageInfo> chosen = adapter.getSelected();
                if (chosen.isEmpty()) {
                    Toast.makeText(ConfirmActivity.this, R.string.nothing_selected, Toast.LENGTH_SHORT).show();
                    return;
                }
                TaskManager task = TaskManager.get();
                task.selected.clear();
                task.selected.addAll(chosen);
                task.beginProcess(task.inputDir, AppSettings.getOutputDir(ConfirmActivity.this), chosen.size());
                TaskService.start(ConfirmActivity.this, TaskService.ACTION_PROCESS);
                finish();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void openPreview(ImageInfo info) {
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra(PreviewActivity.EXTRA_PATH, info.path);
        intent.putExtra(PreviewActivity.EXTRA_REGIONS, new ArrayList<Region>(info.regions));
        startActivity(intent);
    }

    private void loadThumb(final String path, final ImageView view) {
        Bitmap cached = THUMB_CACHE.get(path);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageBitmap(null);
        view.setTag(path);
        THUMB_LOADER.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bitmap = WatermarkDetector.loadThumbnail(path, 240);
                if (bitmap != null) THUMB_CACHE.put(path, bitmap);
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (path.equals(view.getTag())) view.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private final class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
        private final List<ImageInfo> items;
        private final LayoutInflater inflater;

        ImageAdapter(List<ImageInfo> items) {
            this.items = items;
            this.inflater = LayoutInflater.from(ConfirmActivity.this);
        }

        final class ViewHolder extends RecyclerView.ViewHolder {
            final CheckBox checkBox;
            final ImageView thumbnail;
            final TextView name, date, regionInfo;
            ViewHolder(View v) {
                super(v);
                checkBox = v.findViewById(R.id.checkBox);
                thumbnail = v.findViewById(R.id.thumbnail);
                name = v.findViewById(R.id.name);
                date = v.findViewById(R.id.date);
                regionInfo = v.findViewById(R.id.regionInfo);
            }
        }

        @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(inflater.inflate(R.layout.item_image, parent, false));
        }

        @Override public void onBindViewHolder(ViewHolder h, int position) {
            final ImageInfo info = items.get(position);
            h.checkBox.setChecked(info.selected);
            h.checkBox.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    info.selected = ((CheckBox) v).isChecked();
                }
            });
            h.name.setText(info.name);
            h.date.setText(getString(R.string.shot_date, info.dateText));
            h.regionInfo.setText("检测区域 " + (info.regions == null ? 0 : info.regions.size()) + " 处");
            View.OnClickListener preview = new View.OnClickListener() {
                @Override public void onClick(View v) { openPreview(info); }
            };
            h.thumbnail.setOnClickListener(preview);
            h.name.setOnClickListener(preview);
            h.date.setOnClickListener(preview);
            loadThumb(info.path, h.thumbnail);
        }

        @Override public int getItemCount() { return items.size(); }

        List<ImageInfo> getSelected() {
            List<ImageInfo> out = new ArrayList<ImageInfo>();
            for (ImageInfo i : items) if (i.selected) out.add(i);
            return out;
        }

        void setAll(boolean value) {
            for (ImageInfo i : items) i.selected = value;
            notifyDataSetChanged();
        }

        void invert() {
            for (ImageInfo i : items) i.selected = !i.selected;
            notifyDataSetChanged();
        }
    }
}
