package com.sopro.nowatermark.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.sopro.nowatermark.core.algo.Region;

import java.util.ArrayList;
import java.util.List;

/** 在图片预览上按归一化坐标绘制检测到的水印红框。 */
public class RegionOverlayView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Region> regions = new ArrayList<Region>();
    private int imageWidth = 1;
    private int imageHeight = 1;

    public RegionOverlayView(Context c) { super(c); init(); }
    public RegionOverlayView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(4f);
    }

    public void setImageSize(int w, int h) {
        this.imageWidth = Math.max(1, w);
        this.imageHeight = Math.max(1, h);
        invalidate();
    }

    public void setRegions(List<Region> regions) {
        this.regions = regions == null ? new ArrayList<Region>() : regions;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min((float) getWidth() / imageWidth, (float) getHeight() / imageHeight);
        float drawW = imageWidth * scale;
        float drawH = imageHeight * scale;
        float left = (getWidth() - drawW) / 2f;
        float top = (getHeight() - drawH) / 2f;

        for (Region r : regions) {
            RectF rect = new RectF(
                    left + r.left * drawW,
                    top + r.top * drawH,
                    left + r.right * drawW,
                    top + r.bottom * drawH);
            canvas.drawRect(rect, paint);
        }
    }
}
