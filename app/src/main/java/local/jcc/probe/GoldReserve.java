package local.jcc.probe;

final class GoldReserve {
    static final int DEFAULT = 50;
    private static final int[] PRESETS = {0, 15, 25, 35, 45, 55, 85, 105};
    static int step(int current, boolean up) {
        if (up) { for (int value : PRESETS) if (value > current) return value; }
        else { for (int i = PRESETS.length - 1; i >= 0; i--) if (PRESETS[i] < current) return PRESETS[i]; }
        return current;
    }
}
