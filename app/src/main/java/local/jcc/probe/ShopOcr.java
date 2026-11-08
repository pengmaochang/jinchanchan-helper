package local.jcc.probe;

import android.content.Context;
import android.graphics.*;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.util.Arrays;

/** One worker thread owns this recognizer. No network or Google Play services required. */
final class ShopOcr implements AutoCloseable {
    private final TessBaseAPI tess = new TessBaseAPI();
    private final TessBaseAPI digitsApi = new TessBaseAPI();
    private final byte[][] templates = new byte[5][];

    ShopOcr(Context context) throws IOException {
        File root = new File(context.getFilesDir(), "ocr");
        File directory = new File(root, "tessdata");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建模型目录");
        File model = new File(directory, "chi_sim.traineddata");
        if (!model.isFile() || model.length() != 2469156) {
            try (InputStream in = context.getAssets().open("tessdata/chi_sim.traineddata"); OutputStream out = new FileOutputStream(model)) {
                byte[] block = new byte[65536]; int length;
                while ((length = in.read(block)) != -1) out.write(block, 0, length);
            }
        }
        if (!tess.init(root.getAbsolutePath(), "chi_sim")) { tess.recycle(); throw new IOException("中文 OCR 初始化失败"); }
        File eng = new File(directory, "eng.traineddata");
        if (!eng.isFile() || eng.length() < 1000000) {
            try (InputStream in = context.getAssets().open("tessdata/eng.traineddata"); OutputStream out = new FileOutputStream(eng)) {
                byte[] block = new byte[65536]; int length;
                while ((length = in.read(block)) != -1) out.write(block, 0, length);
            }
        }
        if (!digitsApi.init(root.getAbsolutePath(), "eng")) throw new IOException("数字 OCR 初始化失败");
        for (int i = 0; i < 5; i++) try (InputStream in = context.getAssets().open("templates/name" + i + ".bin")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] block = new byte[4096]; int length;
            while ((length = in.read(block)) != -1) bytes.write(block, 0, length);
            templates[i] = bytes.toByteArray();
        }
    }
    static final class Reading {
        final String text; final int confidence;
        Reading(String text, int confidence) { this.text = text; this.confidence = confidence; }
    }
    private Reading read(Bitmap source, int x, int y, int width, int height, boolean digits) {
        int[] out = new int[(width * 2 + 24) * (height * 2 + 24)];
        int targetWidth = width * 2 + 24, targetHeight = height * 2 + 24;
        Arrays.fill(out, Color.WHITE);
        for (int cy = 0; cy < height; cy++) for (int cx = 0; cx < width; cx++) {
            int sx = Math.min(source.getWidth() - 1, (int) ((x + cx + .5) * source.getWidth() / 2048));
            int sy = Math.min(source.getHeight() - 1, (int) ((y + cy + .5) * source.getHeight() / 920));
            int c = source.getPixel(sx, sy), r = Color.red(c), g = Color.green(c), b = Color.blue(c);
            boolean ink = r > 185 && g > 180 && b > 155 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 85;
            int color = ink ? Color.BLACK : Color.WHITE;
            int base = (12 + cy * 2) * targetWidth + 12 + cx * 2;
            out[base] = out[base + 1] = out[base + targetWidth] = out[base + targetWidth + 1] = color;
        }
        Bitmap crop = Bitmap.createBitmap(out, targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        TessBaseAPI api = digits ? digitsApi : tess;
        try {
            api.setVariable("tessedit_char_whitelist", digits ? "0123456789" : "");
            api.setPageSegMode(7);
            api.setImage(crop);
            String text = api.getUTF8Text();
            int confidence = api.meanConfidence();
            api.clear();
            return new Reading(text == null ? "" : text.replaceAll(digits ? "[^0-9]" : "[^\\p{IsHan}·]", ""), confidence);
        } finally { crop.recycle(); }
    }
    private int number(Bitmap image, int x, int y, int w, int h) {
        Reading result = read(image, x, y, w, h, true);
        if (result.confidence < 55 || result.text.isEmpty() || result.text.length() > 3) return -1;
        try { return Integer.parseInt(result.text); } catch (NumberFormatException e) { return -1; }
    }
    Reading readCardName(Bitmap image, int slot) {
        return read(image, ShopVision.LEFT[slot], ShopVision.TOP, ShopVision.WIDTH, ShopVision.HEIGHT, false);
    }
    BuyPolicy.Frame analyze(Bitmap image) {
        BuyPolicy.Frame f = new BuyPolicy.Frame();
        double aspect = (double) image.getWidth() / image.getHeight();
        f.layoutValid = image.getWidth() > image.getHeight() && Math.abs(aspect - 2048.0 / 920) < .035;
        if (!f.layoutValid) return f;
        Reading refresh = read(image, 1875, 608, 82, 44, false);
        f.shopOpen = refresh.text.contains("刷新");
        if (!f.shopOpen) return f;
        f.gold = number(image, 1894, 789, 86, 58);
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        f.readable = f.gold >= 0;
        for (int i = 0; i < 5; i++) {
            byte[] sample = ShopVision.strip(pixels, image.getWidth(), image.getHeight(), i);
            if (ShopVision.ink(sample) < 45) { f.names[i] = ""; continue; }
            double best = 0;
            for (int j = 0; j < 5; j++) {
                double score = ShopVision.similarity(sample, templates[j]);
                if (score >= ShopVision.MATCH_THRESHOLD && score > best) { f.names[i] = ShopVision.NAMES[j]; best = score; }
            }
            if (f.names[i] == null) {
                Reading text = read(image, ShopVision.LEFT[i], ShopVision.TOP, ShopVision.WIDTH, ShopVision.HEIGHT, false);
                f.names[i] = text.text;
                if (text.confidence < 65 || text.text.isEmpty()) f.readable = false;
            }
            f.costs[i] = number(image, ShopVision.LEFT[i] + 254, 375, 42, 44);
        }
        return f;
    }
    @Override public void close() { tess.recycle(); digitsApi.recycle(); }
}
