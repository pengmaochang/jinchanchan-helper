package local.jcc.probe;

/** Native white-text masks; signature comparison remains available for reference diagnostics. */
final class ScreenGuard {
    static byte[][] capture(int[] pixels, int width, int height) {
        if (!PhoneLayout.accepts(width, height) || pixels.length != width * height)
            throw new IllegalArgumentException("仅支持 2670×1200 原始截图");
        byte[][] result = new byte[7][];
        for (int i = 0; i < 5; i++) result[i] = crop(pixels, width, PhoneLayout.name(i));
        result[5] = crop(pixels, width, PhoneLayout.GOLD);
        result[6] = crop(pixels, width, PhoneLayout.REFRESH);
        return result;
    }
    static byte[] crop(int[] p, int w, int[] rect) {
        int left = rect[0], top = rect[1], width = rect[2], height = rect[3];
        byte[] out = new byte[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int c = p[(top + y) * w + left + x], r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
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
