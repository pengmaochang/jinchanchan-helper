package local.jcc.probe;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.Display;
import android.os.*;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.SystemClock;

public class CaptureService extends AccessibilityService {
    static volatile CaptureService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean busy;
    private boolean scanning;
    private long generation;
    private String foreground = "";
    private ScanOverlay overlay;
    private byte[][] templates;
    private int targetIndex;
    private final Runnable scanNext = this::scan;
    private final Runnable capture = this::captureNow;
    @Override protected void onServiceConnected() {
        instance = this;
        status("服务已连接。可显示悬浮窗进行商店识别。");
        try {
            templates = new byte[5][];
            for (int i = 0; i < 5; i++) try (InputStream stream = getAssets().open("templates/name" + i + ".bin")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[4096];
                int length;
                while ((length = stream.read(block)) != -1) bytes.write(block, 0, length);
                templates[i] = bytes.toByteArray();
                if (templates[i].length != ShopVision.WIDTH * ShopVision.HEIGHT) throw new IOException("模板尺寸不正确");
            }
        } catch (IOException e) { templates = null; status("模板加载失败：" + e.getMessage()); }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        // Our overlay is not a navigation change. Any other app, including System UI, pauses the loop.
        if (pkg.equals(getPackageName())) {
            if (event.getClassName() != null && event.getClassName().toString().equals(MainActivity.class.getName())) {
                foreground = pkg;
                if (scanning) stopScanning("已返回设置，识别暂停");
            }
            return;
        }
        foreground = pkg;
        if (scanning && !pkg.equals("com.tencent.jk")) stopScanning("已离开游戏，识别暂停");
    }
    @Override public void onInterrupt() { stopScanning("无障碍服务被中断"); }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (scanning) stopScanning("画面方向已变化，请确认横屏后重新开始");
        if (overlay != null) overlay.onRotation();
    }
    void showOverlay() {
        try {
            if (overlay == null) overlay = new ScanOverlay(this);
            overlay.show();
            status("悬浮窗已显示；切到游戏并手动展开商店，再点击开始。");
        } catch (RuntimeException e) { status("显示悬浮窗失败：" + e.getMessage()); }
    }
    void hideOverlay() { stopScanning("悬浮窗已关闭"); if (overlay != null) { overlay.remove(); overlay = null; } }
    void startScanning() {
        if (scanning) return;
        if (templates == null) { status("模板未加载，无法开始"); return; }
        if (!foreground.equals("com.tencent.jk")) { status("请先切到金铲铲游戏，再在悬浮窗点开始"); return; }
        targetIndex = getSharedPreferences("probe", MODE_PRIVATE).getInt("target", 0);
        if (targetIndex < 0 || targetIndex > 4) targetIndex = 0;
        scanning = true;
        generation++;
        status("识别中 · 目标：" + ShopVision.NAMES[targetIndex] + "（不自动点击）");
        scan();
    }
    void stopScanning(String reason) {
        scanning = false; generation++;
        handler.removeCallbacks(scanNext);
        status(reason);
    }
    private void scan() {
        if (!scanning) return;
        if (!foreground.equals("com.tencent.jk")) { stopScanning("前台游戏状态未知，请重新进入游戏"); return; }
        if (overlay != null && overlay.overlapsShop()) {
            stopScanning("悬浮窗遮住商店：请拖到画面下半部再开始"); return;
        }
        if (busy) { handler.postDelayed(scanNext, 400); return; }
        busy = true;
        final long token = generation, started = SystemClock.elapsedRealtime();
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    Bitmap image = null, hardware = null;
                    try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                        hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (hardware == null) throw new IOException("截图缓冲区为空");
                        image = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        if (image == null) throw new IOException("截图复制失败");
                        if (!scanning || token != generation) { image.recycle(); busy = false; return; }
                        final Bitmap captured = image;
                        worker.execute(() -> analyze(captured, token, started));
                        image = null;
                    } catch (Exception e) {
                        if (image != null && !image.isRecycled()) image.recycle();
                        busy = false; if (token == generation) stopScanning("识别截图失败：" + e.getMessage());
                    } finally { if (hardware != null) hardware.recycle(); }
                }
                @Override public void onFailure(int code) {
                    busy = false;
                    if (token == generation) stopScanning("截图失败，错误码 " + code);
                }
            });
        } catch (Exception e) { busy = false; stopScanning("截图调用失败：" + e.getMessage()); }
    }
    private void analyze(Bitmap image, long token, long started) {
        ShopVision.Result result = null;
        String error = null;
        try {
            int w = image.getWidth(), h = image.getHeight();
            int[] pixels = new int[w * h]; image.getPixels(pixels, 0, w, 0, 0, w, h);
            result = ShopVision.analyze(pixels, w, h, templates, ShopVision.NAMES, templates[targetIndex]);
        } catch (Exception e) { error = e.getMessage(); }
        finally { image.recycle(); }
        final ShopVision.Result answer = result;
        final String failure = error;
        handler.post(() -> {
            busy = false;
            if (!scanning || token != generation) return;
            if (answer == null) { stopScanning("识别失败：" + failure); return; }
            if (!answer.layoutValid) { stopScanning(answer.summary()); return; }
            status("目标：" + ShopVision.NAMES[targetIndex] + " · 仅识别\n" + answer.summary() +
                "\n单次耗时 " + (SystemClock.elapsedRealtime() - started) + " ms");
            handler.postDelayed(scanNext, 400);
        });
    }
    void scheduleCapture() {
        stopScanning("连续识别已暂停");
        if (busy) return;
        busy = true;
        status("已预约：8 秒后截图，请切换到游戏。");
        handler.postDelayed(capture, 8000);
    }
    private void status(String value) {
        getSharedPreferences("probe", MODE_PRIVATE).edit().putString("status", value).apply();
        if (overlay != null) overlay.update(value, scanning);
    }
    private void captureNow() {
        status("正在截取并保存……");
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer buffer = result.getHardwareBuffer();
                    Bitmap hardware = null;
                    Bitmap software = null;
                    try {
                        hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (hardware == null) throw new IOException("无法读取截图缓冲区");
                        software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        if (software == null) throw new IOException("无法复制截图");
                        final Bitmap image = software;
                        worker.execute(() -> save(image));
                        software = null;
                    } catch (Exception e) { status("截图处理失败：" + e); busy = false; }
                    finally {
                        if (software != null) software.recycle();
                        if (hardware != null) hardware.recycle();
                        buffer.close();
                    }
                }
                @Override public void onFailure(int code) {
                    String reason;
                    switch (code) {
                        case 2: reason = "无障碍权限不可用"; break;
                        case 3: reason = "截图间隔过短"; break;
                        case 4: reason = "显示屏无效"; break;
                        case 5: reason = "窗口无效"; break;
                        case 6: reason = "窗口含受保护内容，系统拒绝截图"; break;
                        default: reason = "系统内部错误";
                    }
                    status("截图失败，错误码 " + code + "：" + reason);
                    busy = false;
                }
            });
        } catch (Exception e) { status("调用截图失败：" + e); busy = false; }
    }
    private void save(Bitmap image) {
        Uri uri = null;
        try {
            File temp = new File(getFilesDir(), "pending.png");
            try (OutputStream stream = new FileOutputStream(temp)) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("PNG 编码失败");
            }
            File latest = new File(getFilesDir(), "latest.png");
            if (!temp.renameTo(latest)) throw new IOException("预览文件保存失败");
            String name = "JCC_" + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ".png";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JccProbe");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("无法创建相册图片");
            try (InputStream in = new FileInputStream(latest); OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("无法写入相册");
                byte[] bytes = new byte[65536];
                int count;
                while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, values, null, null);
            final String message = "截图成功：" + image.getWidth() + " × " + image.getHeight() + "\nPictures/JccProbe/" + name + "\n请检查原图中游戏内容是否正常；成功返回不代表画面非黑屏。";
            handler.post(() -> status(message));
        } catch (Exception e) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            final String message = "保存失败：" + e + "\n可尝试查看本地预览。";
            handler.post(() -> status(message));
        } finally { image.recycle(); handler.post(() -> busy = false); }
    }
    @Override public void onDestroy() {
        scanning = false; generation++;
        handler.removeCallbacks(scanNext);
        if (overlay != null) { overlay.remove(); overlay = null; }
        handler.removeCallbacks(capture);
        if (instance == this) instance = null;
        worker.shutdown();
        super.onDestroy();
    }
}
