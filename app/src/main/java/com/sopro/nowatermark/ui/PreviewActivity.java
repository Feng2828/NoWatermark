package com.sopro.nowatermark.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.sopro.nowatermark.R;
import com.sopro.nowatermark.core.WatermarkDetector;
import com.sopro.nowatermark.core.algo.Region;

import java.util.ArrayList;

/** 放大预览：显示图片与检测到的水印区域红框，供人工核对。 */
public class PreviewActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_REGIONS = "regions";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        ArrayList<Region> regions = (ArrayList<Region>) getIntent().getSerializableExtra(EXTRA_REGIONS);

        ImageView image = findViewById(R.id.image);
        RegionOverlayView overlay = findViewById(R.id.overlay);
        Bitmap bitmap = WatermarkDetector.loadThumbnail(path, 1600);
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
            overlay.setImageSize(bitmap.getWidth(), bitmap.getHeight());
        }
        overlay.setRegions(regions);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
