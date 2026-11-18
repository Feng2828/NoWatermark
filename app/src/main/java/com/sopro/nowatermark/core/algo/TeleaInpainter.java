package com.sopro.nowatermark.core.algo;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Telea (2004) 快速行进法图像修复，纯 Java 实现，无 Android / OpenCV 依赖。
 *
 * 仅在 mask 指定的区域（及其扩展边界）内计算，原图其它像素的 RGB 完全不改动。
 * 边界向内按 Eikonal 距离顺序填充，权重 = 方向因子 × 距离因子 × 灰度相近因子，
 * 能把水印笔画周围的纹理自然延续进来。
 */
public final class TeleaInpainter {

    private static final byte KNOWN = 0;
    private static final byte BAND = 1;
    private static final byte UNKNOWN = 2;
    private static final float INF = Float.MAX_VALUE / 4f;

    private TeleaInpainter() {}

    public static void inpaint(int[] argb, int w, int h, boolean[] mask, int radius) {
        // 1) 计算 mask 边界，只在局部工作区处理，避免整图内存开销
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask[y * w + x]) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) return;

        int pad = radius + 2;
        int gx0 = Math.max(0, minX - pad);
        int gy0 = Math.max(0, minY - pad);
        int gx1 = Math.min(w - 1, maxX + pad);
        int gy1 = Math.min(h - 1, maxY + pad);
        int lw = gx1 - gx0 + 1;
        int lh = gy1 - gy0 + 1;
        int n = lw * lh;

        byte[] flag = new byte[n];
        float[] t = new float[n];
        float[] gray = new float[n];
        java.util.Arrays.fill(t, INF);

        for (int ly = 0; ly < lh; ly++) {
            for (int lx = 0; lx < lw; lx++) {
                int idx = ly * lw + lx;
                int gx = gx0 + lx, gy = gy0 + ly;
                int gi = gy * w + gx;
                int p = argb[gi];
                gray[idx] = 0.299f * ((p >> 16) & 0xFF)
                          + 0.587f * ((p >> 8) & 0xFF)
                          + 0.114f * (p & 0xFF);
                if (mask[gi]) {
                    flag[idx] = UNKNOWN;
                } else {
                    flag[idx] = KNOWN;
                    t[idx] = 0f;
                }
            }
        }

        PriorityQueue<Node> heap = new PriorityQueue<Node>(n, new Comparator<Node>() {
            @Override public int compare(Node a, Node b) {
                return Float.compare(a.t, b.t);
            }
        });

        // 2) 初始化窄带：与已知区域相邻的未知像素
        for (int ly = 0; ly < lh; ly++) {
            for (int lx = 0; lx < lw; lx++) {
                int idx = ly * lw + lx;
                if (flag[idx] != UNKNOWN) continue;
                if (hasKnownNeighbor(lx, ly, lw, lh, flag)) {
                    flag[idx] = BAND;
                    t[idx] = 0f;
                    heap.add(new Node(idx, 0f));
                }
            }
        }

        int[] dxs = {-1, 0, 1, 0, -1, 1, -1, 1};
        int[] dys = {0, -1, 0, 1, -1, -1, 1, 1};

        // 3) 快速行进：由外向内逐像素填充
        while (!heap.isEmpty()) {
            Node node = heap.poll();
            int idx = node.idx;
            if (flag[idx] != BAND || Math.abs(node.t - t[idx]) > 1e-5f) continue;

            int lx = idx % lw, ly = idx / lw;
            int gx = gx0 + lx, gy = gy0 + ly;
            int gi = gy * w + gx;

            int newColor = estimateColor(lx, ly, lw, lh, flag, gray, argb, gx0, gy0, w, radius);
            int alpha = argb[gi] & 0xFF000000;
            argb[gi] = alpha | (newColor & 0x00FFFFFF);
            gray[idx] = 0.299f * ((newColor >> 16) & 0xFF)
                      + 0.587f * ((newColor >> 8) & 0xFF)
                      + 0.114f * (newColor & 0xFF);
            flag[idx] = KNOWN;

            // 更新未知邻居的到达时间
            for (int d = 0; d < 8; d++) {
                int nx = lx + dxs[d], ny = ly + dys[d];
                if (nx < 0 || nx >= lw || ny < 0 || ny >= lh) continue;
                int nidx = ny * lw + nx;
                if (flag[nidx] != UNKNOWN) continue;
                float newT = solveNeighbor(nx, ny, lw, lh, flag, t);
                if (newT < t[nidx]) {
                    t[nidx] = newT;
                    flag[nidx] = BAND;
                    heap.add(new Node(nidx, newT));
                }
            }
        }
    }

    /** 用半径邻域内的已知像素加权求当前像素颜色（三通道共用权重）。 */
    private static int estimateColor(int qx, int qy, int lw, int lh, byte[] flag,
                                     float[] gray, int[] argb, int gx0, int gy0,
                                     int gw, int radius) {
        float sumW = 0f, sumR = 0f, sumG = 0f, sumB = 0f;
        float qGray = gray[qy * lw + qx];
        int x0 = Math.max(0, qx - radius), x1 = Math.min(lw - 1, qx + radius);
        int y0 = Math.max(0, qy - radius), y1 = Math.min(lh - 1, qy + radius);

        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                if (px == qx && py == qy) continue;
                int pidx = py * lw + px;
                if (flag[pidx] != KNOWN) continue;

                int ddx = px - qx, ddy = py - qy;
                float distSq = ddx * ddx + ddy * ddy;
                if (distSq < 1e-6f || distSq > radius * radius) continue;
                float dist = (float) Math.sqrt(distSq);

                // p 点梯度（中心差分，仅使用已知像素）
                float gxGrad = gradientX(px, py, lw, lh, flag, gray);
                float gyGrad = gradientY(px, py, lw, lh, flag, gray);
                float gm = (float) Math.sqrt(gxGrad * gxGrad + gyGrad * gyGrad);

                float dirW;
                if (gm < 1e-3f) {
                    dirW = 1f; // 平坦区域各向同性
                } else {
                    float dir = (ddx * gxGrad + ddy * gyGrad) / (dist * gm);
                    dirW = Math.abs(dir);
                    if (dirW < 0.01f) dirW = 0.01f;
                }

                float dstW = 1f / distSq;
                float levW = 1f / (1f + Math.abs(gray[pidx] - qGray));
                float w = dirW * dstW * levW;

                int pGlobal = (gy0 + py) * gw + (gx0 + px);
                int c = argb[pGlobal];
                sumR += ((c >> 16) & 0xFF) * w;
                sumG += ((c >> 8) & 0xFF) * w;
                sumB += (c & 0xFF) * w;
                sumW += w;
            }
        }
        if (sumW < 1e-6f) return argb[(gy0 + qy) * gw + (gx0 + qx)] & 0x00FFFFFF;

        int r = clamp(Math.round(sumR / sumW));
        int g = clamp(Math.round(sumG / sumW));
        int b = clamp(Math.round(sumB / sumW));
        return (r << 16) | (g << 8) | b;
    }

    private static float gradientX(int x, int y, int lw, int lh, byte[] flag, float[] gray) {
        int i = y * lw + x;
        if (x > 0 && x < lw - 1 && flag[i - 1] == KNOWN && flag[i + 1] == KNOWN) {
            return (gray[i + 1] - gray[i - 1]) * 0.5f;
        }
        if (x > 0 && flag[i - 1] == KNOWN) return gray[i] - gray[i - 1];
        if (x < lw - 1 && flag[i + 1] == KNOWN) return gray[i + 1] - gray[i];
        return 0f;
    }

    private static float gradientY(int x, int y, int lw, int lh, byte[] flag, float[] gray) {
        int i = y * lw + x;
        if (y > 0 && y < lh - 1 && flag[i - lw] == KNOWN && flag[i + lw] == KNOWN) {
            return (gray[i + lw] - gray[i - lw]) * 0.5f;
        }
        if (y > 0 && flag[i - lw] == KNOWN) return gray[i] - gray[i - lw];
        if (y < lh - 1 && flag[i + lw] == KNOWN) return gray[i + lw] - gray[i];
        return 0f;
    }

    /** 用四个象限分别解 Eikonal 方程，取最小到达时间。 */
    private static float solveNeighbor(int x, int y, int lw, int lh, byte[] flag, float[] t) {
        float best = INF;
        int i = y * lw + x;
        float tl = (x > 0 && flag[i - 1] != UNKNOWN) ? t[i - 1] : INF;
        float tr = (x < lw - 1 && flag[i + 1] != UNKNOWN) ? t[i + 1] : INF;
        float tu = (y > 0 && flag[i - lw] != UNKNOWN) ? t[i - lw] : INF;
        float td = (y < lh - 1 && flag[i + lw] != UNKNOWN) ? t[i + lw] : INF;

        if (tl < INF && tu < INF) best = Math.min(best, solveEikonal(tl, tu));
        if (tr < INF && tu < INF) best = Math.min(best, solveEikonal(tr, tu));
        if (tr < INF && td < INF) best = Math.min(best, solveEikonal(tr, td));
        if (tl < INF && td < INF) best = Math.min(best, solveEikonal(tl, td));
        // 只有一个方向可用
        if (tl < INF) best = Math.min(best, tl + 1f);
        if (tr < INF) best = Math.min(best, tr + 1f);
        if (tu < INF) best = Math.min(best, tu + 1f);
        if (td < INF) best = Math.min(best, td + 1f);
        return best;
    }

    private static float solveEikonal(float a, float b) {
        if (a > b) { float tmp = a; a = b; b = tmp; }
        if (b - a >= 1f) return a + 1f;
        float v = 2f - (b - a) * (b - a);
        return (a + b + (float) Math.sqrt(Math.max(0f, v))) * 0.5f;
    }

    private static boolean hasKnownNeighbor(int x, int y, int lw, int lh, byte[] flag) {
        int i = y * lw + x;
        if (x > 0 && flag[i - 1] == KNOWN) return true;
        if (x < lw - 1 && flag[i + 1] == KNOWN) return true;
        if (y > 0 && flag[i - lw] == KNOWN) return true;
        return y < lh - 1 && flag[i + lw] == KNOWN;
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private static final class Node {
        final int idx;
        final float t;
        Node(int idx, float t) { this.idx = idx; this.t = t; }
    }
}
