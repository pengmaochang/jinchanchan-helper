package local.jcc.probe;

/** Xiaomi 14, landscape, screenshots at their original 2670 x 1200 pixels. */
public final class PhoneLayout {
    public static final int WIDTH = 2670, HEIGHT = 1200;
    public static final int[] NAME_X = {488, 918, 1348, 1777, 2207};
    public static final int NAME_Y = 489, NAME_W = 274, NAME_H = 55;
    public static final int[] GOLD = {2470, 1029, 112, 76};
    public static final int[] REFRESH = {2444, 793, 107, 57};
    public static boolean accepts(int width, int height) { return width == WIDTH && height == HEIGHT; }
    public static int[] name(int slot) { return new int[]{NAME_X[slot], NAME_Y, NAME_W, NAME_H}; }
    public static int[] cost(int slot) { return new int[]{NAME_X[slot] + 350, NAME_Y, 35, 57}; }
    public static int[] shift(int[] rect, int offsetX) { return new int[]{rect[0]+offsetX, rect[1], rect[2], rect[3]}; }
    /** Locate the existing refresh text in native pixels; either landscape direction can shift the game UI. */
    public static int locateOffset(int[] pixels, int width, byte[] refreshMask) {
        if (anchorScore(pixels, width, refreshMask, 0) >= .88) return 0;
        double best = 0; int offset = 0;
        for (int dx = -96; dx <= 96; dx += 2) {
            double score = anchorScore(pixels, width, refreshMask, dx);
            if (score > best) { best = score; offset = dx; }
        }
        int coarse = offset;
        for (int dx = coarse-1; dx <= coarse+1; dx++) {
            double score = anchorScore(pixels, width, refreshMask, dx);
            if (score > best) { best = score; offset = dx; }
        }
        // This only selects a crop origin. The word/template is still checked by ShopOcr before buying.
        return best >= .78 ? offset : 0;
    }
    private static double anchorScore(int[] pixels, int width, byte[] reference, int dx) {
        byte[] sample = ScreenGuard.crop(pixels, width, shift(REFRESH, dx));
        int ink = 0, both = 0;
        for (int i = 0; i < sample.length; i++) { ink += sample[i] + reference[i]; both += sample[i] * reference[i]; }
        return ink < 120 ? 0 : 2d * both / ink;
    }
}
