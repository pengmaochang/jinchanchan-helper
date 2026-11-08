package local.jcc.probe;

/** Localized white-text signatures used to reject a stale OCR frame before a gesture. */
final class ScreenGuard {
    static byte[][] capture(int[] pixels, int width, int height) {
        byte[][] result = new byte[7][];
        for (int i = 0; i < 5; i++) result[i] = ShopVision.strip(pixels, width, height, i);
        result[5] = crop(pixels, width, height, 1894, 789, 86, 58);
        result[6] = crop(pixels, width, height, 1875, 608, 82, 44);
        return result;
    }
    private static byte[] crop(int[] p, int w, int h, int left, int top, int width, int height) {
        byte[] out = new byte[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int sx = Math.min(w - 1, (int) ((left + x + .5) * w / 2048));
            int sy = Math.min(h - 1, (int) ((top + y + .5) * h / 920));
            int c = p[sy * w + sx], r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
            out[y * width + x] = (byte) (r > 185 && g > 180 && b > 155 &&
                Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 85 ? 1 : 0);
        }
        return out;
    }
    static boolean same(byte[][] before, byte[][] after) {
        for (int i = 0; i < before.length; i++) {
            int a = 0, b = 0, both = 0;
            for (int j = 0; j < before[i].length; j++) {
                a += before[i][j]; b += after[i][j];
                if (before[i][j] != 0 && after[i][j] != 0) both++;
            }
            if (a + b < 30) continue;
            if (2.0 * both / (a + b) < .94) return false;
        }
        return true;
    }
}
