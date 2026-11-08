import local.jcc.probe.TapTiming;
import java.util.Random;

public class TimingCheck {
    public static void main(String[] args) {
        Random random = new Random(92);
        for (int count = 1; count <= 5; count++) for (int sample = 0; sample < 1000; sample++) {
            TapTiming timing = new TapTiming(count, random);
            if (timing.starts[0] != 0 || timing.totalMs > 150) throw new AssertionError("Latency budget");
            for (int i = 0; i < count; i++) {
                if (timing.durations[i] < 20 || timing.durations[i] > 26) throw new AssertionError("Tap length");
                if (i > 0 && timing.starts[i] <= timing.starts[i-1] + timing.durations[i-1]) throw new AssertionError("Overlapping taps");
            }
        }
        System.out.println("Fast sequential batch timing checks passed; <=150ms for up to five taps.");
    }
}
