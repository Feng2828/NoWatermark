package com.sopro.nowatermark.core.algo;

/**
 * 二值图的连通域标记（4 邻域，迭代实现避免递归栈溢出）。
 */
public final class ConnectedComponents {

    private ConnectedComponents() {}

    /** 返回连通域数量，标签写入 labels（0 表示背景，1..n 表示连通域）。 */
    public static int label(boolean[] mask, int w, int h, int[] labels, int[] sizes) {
        int n = w * h;
        int count = 0;
        int[] stack = new int[n];
        java.util.Arrays.fill(labels, 0);

        for (int i = 0; i < n; i++) {
            if (mask[i] && labels[i] == 0) {
                count++;
                int sp = 0;
                stack[sp++] = i;
                labels[i] = count;
                int size = 0;
                while (sp > 0) {
                    int p = stack[--sp];
                    size++;
                    int px = p % w;
                    int py = p / w;
                    if (px > 0 && mask[p - 1] && labels[p - 1] == 0) {
                        labels[p - 1] = count; stack[sp++] = p - 1;
                    }
                    if (px < w - 1 && mask[p + 1] && labels[p + 1] == 0) {
                        labels[p + 1] = count; stack[sp++] = p + 1;
                    }
                    if (py > 0 && mask[p - w] && labels[p - w] == 0) {
                        labels[p - w] = count; stack[sp++] = p - w;
                    }
                    if (py < h - 1 && mask[p + w] && labels[p + w] == 0) {
                        labels[p + w] = count; stack[sp++] = p + w;
                    }
                }
                if (sizes != null) sizes[count] = size;
            }
        }
        return count;
    }

    /** 3x3 膨胀，结果写入 dst。 */
    public static void dilate(boolean[] src, boolean[] dst, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean on = false;
                for (int dy = -1; dy <= 1 && !on; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        if (src[yy * w + xx]) { on = true; break; }
                    }
                }
                dst[y * w + x] = on;
            }
        }
    }

    /** 3x3 腐蚀，结果写入 dst。 */
    public static void erode(boolean[] src, boolean[] dst, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean on = true;
                for (int dy = -1; dy <= 1 && on; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= w) continue;
                        if (!src[yy * w + xx]) { on = false; break; }
                    }
                }
                dst[y * w + x] = on;
            }
        }
    }

    /** 闭运算：先膨胀后腐蚀，用于连接断裂的笔画。 */
    public static void close(boolean[] mask, int w, int h) {
        boolean[] tmp = new boolean[mask.length];
        boolean[] tmp2 = new boolean[mask.length];
        dilate(mask, tmp, w, h);
        erode(tmp, tmp2, w, h);
        System.arraycopy(tmp2, 0, mask, 0, mask.length);
    }
}
