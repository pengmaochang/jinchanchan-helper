package local.jcc.probe;

import java.util.Locale;

/** Pure Java name-strip matching, shared by the phone and the desktop fixture check. */
public final class ShopVision {
    public static final int WIDTH = 210, HEIGHT = 42;
    public static final int[] LEFT = {374, 704, 1034, 1363, 1693};
    public static final int TOP = 375;
    public static final String[] NAMES = {"可酷伯", "维迦", "洛", "韦鲁斯", "芸阿娜"};
    public static final double MATCH_THRESHOLD = 0.86;

    public static byte[] strip(int[] pixels, int width, int height, int slot) {
        if (slot < 0 || slot > 4 || pixels.length != width * height) throw new IllegalArgumentException();
        byte[] mask = new byte[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int sx = Math.min(width - 1, (int) ((LEFT[slot] + x + .5) * width / 2048));
            int sy = Math.min(height - 1, (int) ((TOP + y + .5) * height / 920));
            int c = pixels[sy * width + sx];
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            mask[y * WIDTH + x] = (byte) (r > 185 && g > 180 && b > 155 &&
                Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 85 ? 1 : 0);
        }
        return mask;
    }

    public static int ink(byte[] mask) {
        int count = 0;
        for (byte b : mask) count += b;
        return count;
    }

    public static double similarity(byte[] sample, byte[] template) {
        if (sample.length != WIDTH * HEIGHT || template.length != sample.length) return 0;
        int sampleCount = ink(sample), templateCount = ink(template);
        if (sampleCount < 60 || templateCount < 60) return 0;
        double best = 0;
        for (int dy = -2; dy <= 2; dy++) for (int dx = -3; dx <= 3; dx++) {
            int intersection = 0;
            for (int y = 0; y < HEIGHT; y++) {
                int ty = y + dy;
                if (ty < 0 || ty >= HEIGHT) continue;
                for (int x = 0; x < WIDTH; x++) {
                    int tx = x + dx;
                    if (tx >= 0 && tx < WIDTH && sample[y * WIDTH + x] != 0 && template[ty * WIDTH + tx] != 0)
                        intersection++;
                }
            }
            best = Math.max(best, 2.0 * intersection / (sampleCount + templateCount));
        }
        return best;
    }

    public static Result analyze(int[] pixels, int width, int height, byte[][] templates, String[] names, byte[] target) {
        Result result = new Result();
        double aspect = (double) width / height;
        result.layoutValid = width > height && Math.abs(aspect - 2048.0 / 920) < .035;
        if (!result.layoutValid) return result;
        int visible = 0;
        for (int slot = 0; slot < 5; slot++) {
            byte[] sample = strip(pixels, width, height, slot);
            result.ink[slot] = ink(sample);
            if (result.ink[slot] >= 80) visible++;
            double best = 0;
            String name = result.ink[slot] < 45 ? "空位" : "未收录";
            for (int i = 0; i < templates.length; i++) {
                double score = similarity(sample, templates[i]);
                if (score > best) { best = score; if (score >= MATCH_THRESHOLD) name = names[i]; }
            }
            result.names[slot] = name;
            result.scores[slot] = target == null ? 0 : similarity(sample, target);
            result.hits[slot] = result.scores[slot] >= MATCH_THRESHOLD;
        }
        // A layout candidate only: unknown name strips alone never authorize a tap.
        result.shopCandidate = visible >= 2;
        return result;
    }

    public static final class Result {
        public boolean layoutValid, shopCandidate;
        public final String[] names = new String[5];
        public final double[] scores = new double[5];
        public final int[] ink = new int[5];
        public final boolean[] hits = new boolean[5];
        public String summary() {
            if (!layoutValid) return "画面尺寸不匹配：请横屏并使用当前游戏布局";
            if (!shopCandidate) return "未确认商店展开，请先手动打开商店";
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 5; i++) s.append(i + 1).append("：").append(names[i])
                .append(hits[i] ? " ★目标" : "").append(String.format(Locale.ROOT, " %.0f%%", scores[i] * 100)).append('\n');
            return s.toString().trim();
        }
    }
}
