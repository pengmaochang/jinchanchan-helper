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
    private final byte[][] templates = new byte[5][];
    private final byte[] refreshTemplate;
    private final byte[][] feeTemplates = new byte[3][];

    ShopOcr(Context context) {
        for (int i = 0; i < 5; i++) templates[i] = asset(context, "templates/name" + i + ".bin");
        refreshTemplate = asset(context, "templates/refresh.bin");
        for (int i = 0; i < 3; i++) feeTemplates[i] = asset(context, "templates/fee" + (i + 1) + ".bin");
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
    private Reading[] nameBand(Bitmap image, boolean gray) {
        final int left = 468, top = 475;
        Bitmap crop = Bitmap.createBitmap(2130, 90, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        if (gray) { ColorMatrix cm = new ColorMatrix(); cm.setSaturation(0); paint.setColorFilter(new ColorMatrixColorFilter(cm)); }
        new Canvas(crop).drawBitmap(image, new Rect(left, top, left + 2130, top + 90), new Rect(0, 0, 2130, 90), paint);
        try {
            Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)), 5, TimeUnit.SECONDS);
            @SuppressWarnings("unchecked") java.util.List<Text.Element>[] bins = new java.util.List[5];
            for (int i = 0; i < 5; i++) bins[i] = new java.util.ArrayList<>();
            for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines()) for (Text.Element e : line.getElements()) {
                Rect box = e.getBoundingBox(); if (box == null || e.getText().replaceAll("[^\\p{IsHan}·]", "").isEmpty()) continue;
                int x = box.centerX() + left;
                for (int slot = 0; slot < 5; slot++) if (x >= PhoneLayout.NAME_X[slot] - 8 && x <= PhoneLayout.NAME_X[slot] + PhoneLayout.NAME_W + 8)
                    bins[slot].add(e);
            }
            Reading[] out = new Reading[5];
            for (int i = 0; i < 5; i++) {
                bins[i].sort(java.util.Comparator.comparingInt(e -> e.getBoundingBox().left));
                StringBuilder value = new StringBuilder(); float confidence = 1;
                for (Text.Element e : bins[i]) { value.append(e.getText().replaceAll("[^\\p{IsHan}·]", "")); confidence = Math.min(confidence, e.getConfidence()); }
                out[i] = new Reading(value.toString(), bins[i].isEmpty() ? 0 : Math.round(confidence * 100));
            }
            return out;
        } catch (Exception e) { throw new IllegalStateException(e); }
        finally { crop.recycle(); }
    }
    private Reading[] batch(Bitmap image, boolean gray) {
        final int rowHeight = 110;
        Bitmap crop = Bitmap.createBitmap(440, rowHeight * 7, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(crop); canvas.drawColor(Color.rgb(25, 30, 35));
        Paint paint = new Paint();
        if (gray) {
            ColorMatrix matrix = new ColorMatrix(); matrix.setSaturation(0);
            paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        }
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
    private boolean empty(Bitmap image, int[] rect) {
        int bright = 0;
        for (int y = rect[1]; y < rect[1] + rect[3]; y++) for (int x = rect[0]; x < rect[0] + rect[2]; x++) {
            int c = image.getPixel(x, y);
            if (Color.red(c) > 185 && Color.green(c) > 180 && Color.blue(c) > 155) bright++;
        }
        return bright < 70;
    }
    BuyPolicy.Frame analyze(Bitmap image) {
        BuyPolicy.Frame f = new BuyPolicy.Frame();
        f.layoutValid = PhoneLayout.accepts(image.getWidth(), image.getHeight());
        if (!f.layoutValid) { f.problem = "仅适配小米 14 的 2670×1200 横屏截图"; return f; }
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        f.shopOpen = PhoneVision.score(PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.REFRESH), refreshTemplate,
            PhoneLayout.REFRESH[2], PhoneLayout.REFRESH[3]) >= .88;
        if (!f.shopOpen) f.shopOpen = read(image, PhoneLayout.REFRESH, false).text.contains("刷新");
        if (!f.shopOpen) return f;
        Reading[] readings = batch(image, false);
        Reading[] grayReadings = batch(image, true);
        Reading[] band = nameBand(image, false);
        Reading[] bandGray = nameBand(image, true);
        f.gold = pairedNumber(readings[10], grayReadings[10]);
        f.readable = f.gold >= 0;
        if (!f.readable) f.problem = "金币区域读取失败";
        for (int i = 0; i < 5; i++) {
            if (empty(image, PhoneLayout.name(i))) { f.names[i] = ""; continue; }
            String known = null;
            byte[] mask = PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.name(i));
            double best = .90;
            for (int j = 0; j < templates.length; j++) {
                double score = PhoneVision.score(mask, templates[j], PhoneLayout.NAME_W, PhoneLayout.NAME_H);
                if (score >= best) { best = score; known = PhoneVision.NAMES[j]; }
            }
            Reading name = band[i];
            f.names[i] = known != null ? known : name.text;
            if (known == null && !agrees(name, bandGray[i])) {
                f.readable = false;
                f.problem += (f.problem.isEmpty() ? "" : "；") + "第 " + (i + 1) + " 格卡名未交叉确认（" + name.text + " / " + bandGray[i].text + "）";
            }
            f.costs[i] = pairedNumber(readings[i + 5], grayReadings[i + 5]);
            byte[] feeMask = PhoneVision.mask(pixels, image.getWidth(), PhoneLayout.cost(i));
            double feeBest = .88;
            for (int n = 0; n < feeTemplates.length; n++) {
                double score = PhoneVision.score(feeMask, feeTemplates[n], 35, 57);
                if (score >= feeBest) { feeBest = score; f.costs[i] = n + 1; }
            }
        }
        return f;
    }
    private boolean agrees(Reading a, Reading b) {
        return !a.text.isEmpty() && a.text.equals(b.text) && Math.min(a.confidence, b.confidence) >= 40;
    }
    private int pairedNumber(Reading a, Reading b) {
        if (!agrees(a, b) || a.text.length() > 3) return -1;
        try { return Integer.parseInt(a.text); } catch (NumberFormatException e) { return -1; }
    }
    @Override public void close() { recognizer.close(); }
}
