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
}
