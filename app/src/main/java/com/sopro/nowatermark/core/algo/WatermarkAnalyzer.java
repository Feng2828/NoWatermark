package com.sopro.nowatermark.core.algo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 水印检测核心：不依赖 Android API，输入输出均为 ARGB_8888 像素数组。
 *
 * 原理：半透明水印相当于在底图上叠加一层近似恒定的颜色。
 * 用大半径箱式模糊估计低频背景，求“叠加残差”；统计全图残差的主方向，
 * 把残差投影到主方向上——背景纹理正负相消，而水印区域持续为正。
 * 阈值化 + 形态学闭运算 + 连通域分析 + 位置/面积/形状打分得到候选水印区域。
 *
 * 说明：这是启发式检测，允许误判，由用户人工复核（符合需求）。
 */
public final class WatermarkAnalyzer {

    private WatermarkAnalyzer() {}

    public static final class Result {
        public final boolean hasWatermark;
        public final List<Region> regions;
        public final float bestScore;

        public Result(boolean hasWatermark, List<Region> regions, float bestScore) {
            this.hasWatermark = hasWatermark;
            this.regions = regions;
            this.bestScore = bestScore;
        }
    }

    /** 工作图最大边长，控制检测耗时与内存。 */
    private static final int WORK_MAX_SIDE = 900;
    private static final float MIN_AREA_RATIO = 0.0004f;
    private static final float MAX_AREA_RATIO = 0.32f;
    private static final float REGION_SCORE_GATE = 0.60f;
    /** 判定“整图为有水印”的基准门槛，实际门槛随灵敏度浮动。 */
    private static final float WATERMARK_SCORE_BASE = 1.60f;
    private static final int MAX_REGIONS = 10;

    public static Result analyze(int[] argb, int w, int h, float sensitivity) {
        int sw, sh;
        int[] work;
        float scale = (float) Math.max(w, h) / WORK_MAX_SIDE;
        if (scale <= 1f) {
            sw = w; sh = h; work = argb;
        } else {
            sw = Math.max(1, Math.round(w / scale));
            sh = Math.max(1, Math.round(h / scale));
            work = new int[sw * sh];
            for (int y = 0; y < sh; y++) {
                int sy = Math.min(h - 1, (int) ((y + 0.5f) * h / sh));
                for (int x = 0; x < sw; x++) {
                    int sx = Math.min(w - 1, (int) ((x + 0.5f) * w / sw));
                    work[y * sw + x] = argb[sy * w + sx];
                }
            }
        }

        int n = sw * sh;
        if (n < 64) return new Result(false, new ArrayList<Region>(), 0f);

        // 轻度降噪：压制高频纹理，使叠加水印的边界更连贯
        float[] smooth = new float[3 * n];
        BoxBlur.blurRGB(work, smooth, sw, sh, 2);
        int[] denoised = new int[n];
        for (int i = 0; i < n; i++) {
            int r = clamp(Math.round(smooth[3 * i]));
            int g = clamp(Math.round(smooth[3 * i + 1]));
            int b = clamp(Math.round(smooth[3 * i + 2]));
            denoised[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        work = denoised;

        int radius = clamp(Math.round(Math.max(sw, sh) / 10f), 10, 100);
        float[] bg = new float[3 * n];
        BoxBlur.blurRGB(work, bg, sw, sh, radius);

        // 1) 全图残差主方向（带符号累加：背景纹理相消，水印同向叠加）
        double sr = 0, sg = 0, sb = 0;
        for (int i = 0; i < n; i++) {
            int p = work[i];
            sr += ((p >> 16) & 0xFF) - bg[3 * i];
            sg += ((p >> 8) & 0xFF) - bg[3 * i + 1];
            sb += (p & 0xFF) - bg[3 * i + 2];
        }
        double dirNorm = Math.sqrt(sr * sr + sg * sg + sb * sb);
        if (dirNorm < 1e-3) {
            return new Result(false, new ArrayList<Region>(), 0f);
        }
        double vx = sr / dirNorm, vy = sg / dirNorm, vz = sb / dirNorm;

        // 2) 残差投影
        float[] proj = new float[n];
        double sum = 0, sumSq = 0;
        for (int i = 0; i < n; i++) {
            int p = work[i];
            double dr = ((p >> 16) & 0xFF) - bg[3 * i];
            double dg = ((p >> 8) & 0xFF) - bg[3 * i + 1];
            double db = (p & 0xFF) - bg[3 * i + 2];
            float v = (float) (dr * vx + dg * vy + db * vz);
            proj[i] = v;
            sum += v;
            sumSq += (double) v * v;
        }
        double mean = sum / n;
        double std = Math.sqrt(Math.max(0, sumSq / n - mean * mean));
        if (std < 1e-4) return new Result(false, new ArrayList<Region>(), 0f);

        float sens = clamp(sensitivity, 0f, 1f);
        float k = 2.8f - 2.2f * sens;
        float threshold = (float) (mean + k * std);

        // 3) 阈值 + 闭运算
        boolean[] mask = new boolean[n];
        for (int i = 0; i < n; i++) mask[i] = proj[i] > threshold;
        ConnectedComponents.close(mask, sw, sh);

        // 4) 连通域
        int[] labels = new int[n];
        int[] sizes = new int[n + 1];
        int comps = ConnectedComponents.label(mask, sw, sh, labels, sizes);
        if (comps <= 0) return new Result(false, new ArrayList<Region>(), 0f);

        int[] minX = new int[comps + 1]; Arrays.fill(minX, sw);
        int[] minY = new int[comps + 1]; Arrays.fill(minY, sh);
        int[] maxX = new int[comps + 1]; Arrays.fill(maxX, -1);
        int[] maxY = new int[comps + 1]; Arrays.fill(maxY, -1);
        float[] sumProj = new float[comps + 1];
        for (int i = 0; i < n; i++) {
            int id = labels[i];
            if (id == 0) continue;
            int x = i % sw, y = i / sw;
            if (x < minX[id]) minX[id] = x;
            if (x > maxX[id]) maxX[id] = x;
            if (y < minY[id]) minY[id] = y;
            if (y > maxY[id]) maxY[id] = y;
            sumProj[id] += proj[i];
        }

        // 5) 打分
        List<Scored> scored = new ArrayList<Scored>();
        for (int id = 1; id <= comps; id++) {
            int area = sizes[id];
            if (area <= 0) continue;
            float rel = area / (float) n;
            if (rel < MIN_AREA_RATIO || rel > MAX_AREA_RATIO) continue;

            float cx = (minX[id] + maxX[id] + 1) / (2f * sw);
            float cy = (minY[id] + maxY[id] + 1) / (2f * sh);
            float edgeDist = Math.min(Math.min(cx, 1f - cx), Math.min(cy, 1f - cy));
            float prior = 1f + 0.35f * (1f - clamp(edgeDist / 0.22f, 0f, 1f));

            float bw = (maxX[id] - minX[id] + 1) / (float) sw;
            float bh = (maxY[id] - minY[id] + 1) / (float) sh;
            float aspect = bw / Math.max(bh, 1e-4f);
            float aspectFactor = (aspect > 25f || aspect < 0.04f) ? 0.6f : 1f;

            float areaFactor = clamp((float) Math.sqrt(rel / 0.003f), 0f, 1f);
            float avgProj = sumProj[id] / area;
            float strength = Math.max(0f, (avgProj - threshold) / ((float) std + 1e-3f));
            float score = (0.4f + strength) * prior * aspectFactor * areaFactor;
            if (score < REGION_SCORE_GATE) continue;

            float padX = 2f / sw, padY = 2f / sh;
            float l = clamp(minX[id] / (float) sw - padX, 0f, 1f);
            float t = clamp(minY[id] / (float) sh - padY, 0f, 1f);
            float r = clamp((maxX[id] + 1) / (float) sw + padX, 0f, 1f);
            float b = clamp((maxY[id] + 1) / (float) sh + padY, 0f, 1f);
            scored.add(new Scored(l, t, r, b, score));
        }

        // 6) 排序 + 非极大值抑制（去掉重叠区域）
        scored.sort(new Comparator<Scored>() {
            @Override public int compare(Scored a, Scored b) {
                return Float.compare(b.score, a.score);
            }
        });
        List<Region> regions = new ArrayList<Region>();
        for (Scored s : scored) {
            if (regions.size() >= MAX_REGIONS) break;
            boolean overlapped = false;
            for (Region kept : regions) {
                if (iou(s, kept) > 0.55f) { overlapped = true; break; }
            }
            if (!overlapped) regions.add(new Region(s.l, s.t, s.r, s.b, s.score));
        }

        float best = regions.isEmpty() ? 0f : regions.get(0).score;
        float decisionGate = WATERMARK_SCORE_BASE - 1.2f * sens;
        boolean has = !regions.isEmpty() && best >= decisionGate;
        return new Result(has, has ? regions : new ArrayList<Region>(), best);
    }

    private static float iou(Scored a, Region b) {
        float ix1 = Math.max(a.l, b.left), iy1 = Math.max(a.t, b.top);
        float ix2 = Math.min(a.r, b.right), iy2 = Math.min(a.b, b.bottom);
        float inter = Math.max(0f, ix2 - ix1) * Math.max(0f, iy2 - iy1);
        float areaA = (a.r - a.l) * (a.b - a.t);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        float union = areaA + areaB - inter;
        return union <= 0f ? 0f : inter / union;
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static final class Scored {
        final float l, t, r, b, score;
        Scored(float l, float t, float r, float b, float score) {
            this.l = l; this.t = t; this.r = r; this.b = b; this.score = score;
        }
    }
}
