package local.jcc.probe;

import java.util.Random;

/** Sequential taps, never simultaneous fingers; five taps finish within 198ms. */
public final class TapTiming {
    public final int[] starts, durations;
    public final int totalMs;
    public TapTiming(int count, Random random) {
        if (count < 1 || count > 5) throw new IllegalArgumentException();
        starts = new int[count]; durations = new int[count]; int end = 0;
        for (int i = 0; i < count; i++) {
            starts[i] = end; durations[i] = 20 + random.nextInt(11); end += durations[i];
            if (i + 1 < count) end += 7 + random.nextInt(6);
        }
        totalMs = end;
    }
}
