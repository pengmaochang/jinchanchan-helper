package local.jcc.probe;

import java.util.Random;

/** Experimental fast sequential taps; five taps finish within 72ms of scheduled input. */
public final class TapTiming {
    public final int[] starts, durations;
    public final int totalMs;
    public TapTiming(int count, Random random) {
        if (count < 1 || count > 5) throw new IllegalArgumentException();
        starts = new int[count]; durations = new int[count]; int end = 0;
        for (int i = 0; i < count; i++) {
            starts[i] = end; durations[i] = 8 + random.nextInt(5); end += durations[i];
            if (i + 1 < count) end += 1 + random.nextInt(3);
        }
        totalMs = end;
    }
}
