package local.jcc.probe;

/** Exact native-pixel text templates, used only for the five user-labelled reference names and shop button. */
public final class PhoneVision {
    public static final String[] NAMES = {"可酷伯", "维迦", "洛", "韦鲁斯", "芸阿娜"};
    public static byte[] mask(int[] pixels, int width, int[] rect) { return ScreenGuard.crop(pixels, width, rect); }
    public static double score(byte[] a, byte[] b, int width, int height) {
        if (a.length != width * height || b.length != a.length) return 0;
        int ca = 0, cb = 0;
        for (byte v : a) ca += v;
        for (byte v : b) cb += v;
        if (ca < 60 || cb < 60) return 0;
        double best = 0;
        for (int dy = -2; dy <= 2; dy++) for (int dx = -3; dx <= 3; dx++) {
            int count = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int tx = x + dx, ty = y + dy;
                if (tx >= 0 && tx < width && ty >= 0 && ty < height && a[y * width + x] != 0 && b[ty * width + tx] != 0) count++;
            }
            best = Math.max(best, 2d * count / (ca + cb));
        }
        return best;
    }
}
