package local.jcc.probe;

import android.content.Context;
import android.graphics.*;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.concurrent.TimeUnit;

/** Bundled Chinese OCR; crops native phone pixels, without resizing or binarizing. */
final class ShopOcr implements AutoCloseable {
    private final TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    private final PhoneVision.Template[] templates = new PhoneVision.Template[PhoneVision.NAMES.length];
    private final PhoneVision.Template refreshTemplate;
    private final PhoneVision.Template[] feeTemplates = new PhoneVision.Template[3];
    private PhoneVision.Template observedRefresh;
    private byte[][] previousSignature;
    private BuyPolicy.Frame previousFrame;
    private boolean cached;
    private volatile boolean recoveryRequested;
    private long previousFrameAt;
    private final java.util.Map<String, Integer> catalogCosts = new java.util.HashMap<>();
    void requestRecovery() { recoveryRequested = true; }

    boolean cachedLastFrame() { return cached; }

    ShopOcr(Context context) {
        try {
            org.json.JSONArray rows = new org.json.JSONObject(new String(asset(context, "champions_s18.json"), java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("champions");
            for (int i = 0; i < rows.length(); i++) { org.json.JSONObject c = rows.getJSONObject(i); catalogCosts.put(c.getString("name"), c.getInt("price")); }
        } catch (org.json.JSONException e) { throw new IllegalStateException("棋子目录读取失败", e); }

        for (int i = 0; i < templates.length; i++) templates[i] = new PhoneVision.Template(asset(context, "templates/name" + i + ".bin"), PhoneLayout.NAME_W, PhoneLayout.NAME_H);
        refreshTemplate = new PhoneVision.Template(asset(context, "templates/refresh.bin"), PhoneLayout.REFRESH[2], PhoneLayout.REFRESH[3]);
        for (int i = 0; i < 3; i++) feeTemplates[i] = new PhoneVision.Template(asset(context, "templates/fee" + (i + 1) + ".bin"), 35, 57);
    }
    private byte[] asset(Context context, String name) {
        try (java.io.InputStream in = context.getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); byte[] block = new byte[4096]; int n;
            while ((n = in.read(block)) != -1) out.write(block, 0, n);
            return out.toByteArray();
        } catch (java.io.IOException e) { throw new IllegalStateException("缺少原生模板 " + name, e); }
    }
    static final class Reading {
        final String text;
        final int confidence;
        Reading(String text, int confidence) { this.text = text; this.confidence = confidence; }
    }
    private Reading read(Bitmap image, int[] rect, boolean digits) {
        Bitmap crop = Bitmap.createBitmap(rect[2] + 16, rect[3] + 16, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(crop);
        canvas.drawColor(Color.rgb(25, 30, 35));
        canvas.drawBitmap(image, new Rect(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3]),
            new Rect(8, 8, rect[2] + 8, rect[3] + 8), null);
        try {
            Text result = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)), 5, TimeUnit.SECONDS);
            int count = 0;
            float confidence = 1;
            for (Text.TextBlock block : result.getTextBlocks()) for (Text.Line line : block.getLines())
                for (Text.Element element : line.getElements()) {
                    confidence = Math.min(confidence, element.getConfidence()); count++;
                }
            String text = result.getText().replaceAll(digits ? "[^0-9]" : "[^\\p{IsHan}·]", "");
            return new Reading(text, count == 0 ? 0 : Math.round(confidence * 100));
        } catch (Exception e) { throw new IllegalStateException("OCR 处理失败：" + e.getMessage(), e); }
        finally { crop.recycle(); }
    }
    Reading readCardName(Bitmap image, int slot) { return read(image, PhoneLayout.name(slot), false); }
    private Reading[] batch(Bitmap image) {
        final int rowHeight = 110;
        Bitmap crop = Bitmap.createBitmap(440, rowHeight * 7, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(crop); canvas.drawColor(Color.rgb(25, 30, 35));
        Paint paint = new Paint();
        for (int i = 0; i < 7; i++) {
            int[] rect = i < 5 ? PhoneLayout.name(i) : i == 5 ? PhoneLayout.GOLD : PhoneLayout.REFRESH;
            canvas.drawBitmap(image, new Rect(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3]),
                new Rect(16, i * rowHeight + 16, 16 + rect[2], i * rowHeight + 16 + rect[3]), paint);
            if (i < 5) {
                rect = PhoneLayout.cost(i);
                canvas.drawBitmap(image, new Rect(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3]),
                    new Rect(340, i * rowHeight + 16, 340 + rect[2], i * rowHeight + 16 + rect[3]), paint);
            }
        }
        try {
            Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)), 5, TimeUnit.SECONDS);
            @SuppressWarnings("unchecked") java.util.List<Text.Element>[] bins = new java.util.List[12];
            for (int i = 0; i < bins.length; i++) bins[i] = new java.util.ArrayList<>();
            for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines()) for (Text.Element e : line.getElements()) {
                Rect box = e.getBoundingBox(); if (box == null) continue;
                int row = box.centerY() / rowHeight;
                if (row < 0 || row > 6) continue;
                int bin = row < 5 && box.centerX() >= 320 ? row + 5 : row == 5 ? 10 : row == 6 ? 11 : row;
                bins[bin].add(e);
            }
            Reading[] out = new Reading[12];
            for (int i = 0; i < bins.length; i++) {
                bins[i].sort(java.util.Comparator.comparingInt(e -> e.getBoundingBox().left));
                StringBuilder raw = new StringBuilder(); float confidence = 1;
                for (Text.Element e : bins[i]) { raw.append(e.getText()); confidence = Math.min(confidence, e.getConfidence()); }
                String value = raw.toString().replaceAll(i >= 5 && i <= 10 ? "[^0-9]" : "[^\\p{IsHan}·]", "");
                out[i] = new Reading(value, bins[i].isEmpty() ? 0 : Math.round(confidence * 100));
            }
            return out;
        } catch (Exception e) { throw new IllegalStateException("批量 OCR 失败", e); }
        finally { crop.recycle(); }
    }
    private boolean empty(int[] pixels, int width, int[] rect) {
        int bright = 0;
        for (int y = rect[1]; y < rect[1] + rect[3]; y++) for (int x = rect[0]; x < rect[0] + rect[2]; x++) {
            int c = pixels[y * width + x];
            if (Color.red(c) > 185 && Color.green(c) > 180 && Color.blue(c) > 155) bright++;
        }
        return bright < 70;
    }
    BuyPolicy.Frame analyze(Bitmap image) {
        cached = false;
        boolean recover = recoveryRequested; recoveryRequested = false;
        BuyPolicy.Frame f = new BuyPolicy.Frame();
        f.layoutValid = PhoneLayout.accepts(image.getWidth(), image.getHeight());
        if (!f.layoutValid) { f.problem = "仅适配小米 14 的 2670×1200 横屏截图"; return f; }
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        byte[][] signature = new byte[12][];
        for (int i = 0; i < 5; i++) {
            signature[i] = PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.name(i));
            signature[i + 5] = PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.cost(i));
        }
        signature[10] = PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.GOLD);
        signature[11] = PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.REFRESH);
        if (!recover && previousFrame != null && previousFrame.readable &&
            android.os.SystemClock.elapsedRealtime() - previousFrameAt < 1200 && java.util.Arrays.deepEquals(signature, previousSignature)) {
            cached = true; return previousFrame;
        }
        PhoneVision.Template refreshSample = new PhoneVision.Template(signature[11], PhoneLayout.REFRESH[2], PhoneLayout.REFRESH[3]);
        f.shopOpen = PhoneVision.score(refreshSample, refreshTemplate) >= .88 ||
            (observedRefresh != null && PhoneVision.score(refreshSample, observedRefresh) >= .90);
        if (!f.shopOpen) {
            f.shopOpen = read(image, PhoneLayout.REFRESH, false).text.contains("刷新");
            if (f.shopOpen) observedRefresh = refreshSample;
        }
        if (!f.shopOpen) return f;
        Reading[] readings = batch(image);
        f.gold = number(readings[10]);
        if (f.gold < 0) {
            int repaired = number(read(image, PhoneLayout.GOLD, true));
            if (repaired >= 0) f.gold = repaired;
        }
        f.readable = f.gold >= 0;
        if (!f.readable) f.problem = "金币区域读取失败";
        for (int i = 0; i < 5; i++) {
            if (empty(pixels, image.getWidth(), PhoneLayout.name(i))) { f.names[i] = ""; continue; }
            String known = null;
            byte[] mask = signature[i];
            PhoneVision.Template sample = new PhoneVision.Template(mask, PhoneLayout.NAME_W, PhoneLayout.NAME_H);
            double best = .90;
            for (int j = 0; j < templates.length; j++) {
                double score = PhoneVision.score(sample, templates[j]);
                if (score >= best) { best = score; known = PhoneVision.NAMES[j]; }
            }
            Reading name = readings[i];
            if (recover && known == null && name.text.isEmpty()) name = read(image, PhoneLayout.name(i), false);
            f.names[i] = known != null ? known : name.text.isEmpty() ? null : name.text;
            if (known == null && name.text.isEmpty()) {
                f.readable = false;
                f.problem += (f.problem.isEmpty() ? "" : "；") + "第 " + (i + 1) + " 格暂未读到卡名";
            }
            f.costs[i] = number(readings[i + 5]);
            byte[] feeMask = signature[i + 5];
            PhoneVision.Template feeSample = new PhoneVision.Template(feeMask, 35, 57);
            double feeBest = .88;
            for (int n = 0; n < feeTemplates.length; n++) {
                double score = PhoneVision.score(feeSample, feeTemplates[n]);
                if (score >= feeBest) { feeBest = score; f.costs[i] = n + 1; }
            }
            if (f.costs[i] < 1 || f.costs[i] > 5) {
                Integer expected = catalogCosts.get(f.names[i]);
                if (expected != null) f.costs[i] = expected;
                else if (recover) f.costs[i] = number(read(image, PhoneLayout.cost(i), true));
            }
        }
        if (f.readable) { previousFrame = f; previousSignature = signature; previousFrameAt = android.os.SystemClock.elapsedRealtime(); }
        return f;
    }
    private int number(Reading reading) {
        if (reading.text.isEmpty() || reading.text.length() > 3) return -1;
        try { return Integer.parseInt(reading.text); } catch (NumberFormatException e) { return -1; }
    }
    @Override public void close() { recognizer.close(); }
}
