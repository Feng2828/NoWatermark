package com.sopro.nowatermark.core.algo;

/**
 * 基于滑动窗口的箱式模糊（三次叠加近似高斯）。纯 Java，无 Android 依赖。
 * 用于估计图像的低频背景，进而求出“叠加层残差”。
 */
public final class BoxBlur {

    private BoxBlur() {}

    /**
     * 对 ARGB 数组按通道做箱式模糊。
     *
     * @param src    ARGB_8888 像素，getPixels 输出格式
     * @param dst    输出 RGB（每通道 0..255，浮点）
     * @param w      宽
     * @param h      高
     * @param radius 模糊半径（像素）
     */
    public static void blurRGB(int[] src, float[] dst, int w, int h, int radius) {
        blurChannel(src, dst, w, h, radius, 16, 0); // R 写入 dst offsets 0
        blurChannel(src, dst, w, h, radius, 8, 1);  // G 写入 dst offsets 1
        blurChannel(src, dst, w, h, radius, 0, 2);  // B 写入 dst offsets 2
    }

    /**
     * 单通道可分离箱式模糊。src 为 ARGB，结果写入 dst[3*i + channelOffset]。
     */
    public static void blurChannel(int[] src, float[] dst, int w, int h, int radius,
                                   int shift, int channelOffset) {
        int n = w * h;
        float[] tmp = new float[n];
        int window = 2 * radius + 1;

        // 水平方向
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float sum = 0f;
            for (int k = -radius; k <= radius; k++) {
                int x = clamp(k, 0, w - 1);
                sum += ((src[row + x] >> shift) & 0xFF);
            }
            for (int x = 0; x < w; x++) {
                tmp[row + x] = sum / window;
                int outX = clamp(x - radius, 0, w - 1);
                int inX = clamp(x + radius + 1, 0, w - 1);
                sum += ((src[row + inX] >> shift) & 0xFF) - ((src[row + outX] >> shift) & 0xFF);
            }
        }
        // 垂直方向
        for (int x = 0; x < w; x++) {
            float sum = 0f;
            for (int k = -radius; k <= radius; k++) {
                int y = clamp(k, 0, h - 1);
                sum += tmp[y * w + x];
            }
            for (int y = 0; y < h; y++) {
                dst[3 * (y * w + x) + channelOffset] = sum / window;
                int outY = clamp(y - radius, 0, h - 1);
                int inY = clamp(y + radius + 1, 0, h - 1);
                sum += tmp[inY * w + x] - tmp[outY * w + x];
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
