import local.jcc.probe.PhoneVision;
import java.util.Random;

/** Compare the packed matcher to the original pixel definition, including 64-bit row boundaries. */
public class VisionSpeedCheck {
    static double reference(byte[] a, byte[] b, int w, int h) {
        int ca = 0, cb = 0; for (byte v : a) ca += v; for (byte v : b) cb += v;
        if (ca < 60 || cb < 60) return 0;
        double best = 0;
        for (int dy = -2; dy <= 2; dy++) for (int dx = -3; dx <= 3; dx++) {
            int intersection = 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int tx = x + dx, ty = y + dy;
                if (tx >= 0 && tx < w && ty >= 0 && ty < h && a[y*w+x] != 0 && b[ty*w+tx] != 0) intersection++;
            }
            best = Math.max(best, 2d * intersection / (ca + cb));
        }
        return best;
    }
    public static void main(String[] args) {
        Random random = new Random(32);
        for (int w : new int[]{32,64,65,130,274}) for (int sample = 0; sample < 8; sample++) {
            int h = 15; byte[] a = new byte[w*h], b = new byte[w*h];
            for (int i = 0; i < a.length; i++) a[i] = (byte) (random.nextBoolean() ? 1 : 0);
            int dx = random.nextInt(7) - 3, dy = random.nextInt(5) - 2;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int tx = x + dx, ty = y + dy;
                if (tx >= 0 && tx < w && ty >= 0 && ty < h) b[ty*w+tx] = a[y*w+x];
            }
            double expected = reference(a,b,w,h), actual = PhoneVision.score(a,b,w,h);
            if (Math.abs(expected - actual) > 1e-12) throw new AssertionError(w + " expected=" + expected + " actual=" + actual);
        }
        System.out.println("Packed matching is equivalent to pixel matching across row boundaries.");
    }
}
