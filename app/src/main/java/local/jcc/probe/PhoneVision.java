package local.jcc.probe;

/** Exact native-pixel text templates for user-labelled reference names and the shop button. */
public final class PhoneVision {
    public static final String[] NAMES = {"可酷伯", "维迦", "洛", "韦鲁斯", "芸阿娜", "黛安娜"};
    public static byte[] mask(int[] pixels, int width, int[] rect) { return ScreenGuard.crop(pixels, width, rect); }
    public static final class Template {
        final int width, height, stride, ink;
        final long[] rows;
        public Template(byte[] pixels, int width, int height) {
            if (pixels.length != width * height) throw new IllegalArgumentException();
            this.width = width; this.height = height; stride = (width + 63) / 64;
            rows = new long[stride * height]; int count = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if (pixels[y * width + x] != 0) {
                rows[y * stride + x / 64] |= 1L << (x % 64); count++;
            }
            ink = count;
        }
    }
    public static double score(byte[] a, byte[] b, int width, int height) {
        if (a.length != width * height || b.length != a.length) return 0;
        return score(new Template(a, width, height), new Template(b, width, height));
    }
    public static double score(Template a, Template b) {
        if (a.width != b.width || a.height != b.height || a.ink < 60 || b.ink < 60) return 0;
        double best = 0;
        for (int dy = -2; dy <= 2; dy++) for (int dx = -3; dx <= 3; dx++) {
            int count = 0;
            for (int y = 0; y < a.height; y++) {
                int ty = y + dy; if (ty < 0 || ty >= a.height) continue;
                for (int word = 0; word < a.stride; word++) {
                    int index = ty * a.stride + word;
                    long shifted = b.rows[index];
                    if (dx > 0) shifted = (shifted >>> dx) | (word + 1 < a.stride ? b.rows[index + 1] << (64 - dx) : 0);
                    else if (dx < 0) shifted = (shifted << -dx) | (word > 0 ? b.rows[index - 1] >>> (64 + dx) : 0);
                    count += Long.bitCount(a.rows[y * a.stride + word] & shifted);
                }
            }
            best = Math.max(best, 2d * count / (a.ink + b.ink));
            if (best == 1) return best;
        }
        return best;
    }
}
