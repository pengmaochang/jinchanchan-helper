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
    private boolean choosingDeck;
    private String deckName = "默认卡组";
    private long generation;
    private String foreground = "";
    private ScanOverlay overlay;
    private ShopOcr recognizer;
    private BuyPolicy policy;
    private boolean previewOnly;
    private long lastScreenshot;
    private int screenshotIntervalMs = 334;
    private static final int MAX_ACTION_FRAME_AGE_MS = 250;
    private long lastRefreshCompleted;
    private String lastStatus = "";
    private java.util.List<String> targets;
    private final Runnable scanNext = this::scan;
    private final Runnable capture = this::captureNow;
    @Override protected void onServiceConnected() {
        instance = this;
        status("服务已连接。可显示悬浮窗进行商店识别。");
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        // Ignore our overlay; the current application window is rechecked before each gesture.
        if (pkg.equals(getPackageName())) {
            if (event.getClassName() != null && event.getClassName().toString().equals(MainActivity.class.getName())) {
                foreground = pkg;
                if (scanning) waitForGame("已开启 · 等待进入游戏");
            }
            return;
        }
        foreground = pkg;
        if (scanning && !isGamePackage(pkg) && !pkg.equals("com.android.systemui") && !isGameForeground())
            waitForGame("已开启 · 等待返回游戏");
    }
    @Override public void onInterrupt() { stopScanning("无障碍服务被中断"); }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (scanning) waitForGame("已开启 · 等待游戏横屏");
        if (overlay != null) overlay.onRotation();
    }
    void showOverlay() {
        try {
            if (overlay == null) overlay = new ScanOverlay(this);
            overlay.show();
            status("点击开启后，商店打开就自动识别购买");
        } catch (RuntimeException e) { status("显示悬浮窗失败：" + e.getMessage()); }
    }
    void hideOverlay() { stopScanning("悬浮窗已关闭"); if (overlay != null) { overlay.remove(); overlay = null; } }
    void setAutoRefresh(boolean enabled) {
        getSharedPreferences("probe", MODE_PRIVATE).edit().putBoolean("autoRefresh", enabled).apply();
        if (policy != null) policy.setAutoRefresh(enabled);
        status(enabled ? "自动刷新已开启" : "自动刷新已关闭，等待手动刷新");
    }
    int goldReserve() { return getSharedPreferences("probe", MODE_PRIVATE).getInt("reserve", GoldReserve.DEFAULT); }
    void setGoldReserve(int value) {
        if (value < 0 || value > 999) throw new IllegalArgumentException("保留金币应为 0～999");
        generation++; handler.removeCallbacks(scanNext);
        getSharedPreferences("probe", MODE_PRIVATE).edit().putInt("reserve", value).apply();
        if (policy != null) policy.setReserve(value);
        status("保留金币：" + value);
        if (scanning && !choosingDeck) handler.post(scanNext);
    }
    void startScanning() {
        if (scanning) return;
        android.content.SharedPreferences prefs = getSharedPreferences("probe", MODE_PRIVATE);
        try {
            DeckStore.Deck deck = new DeckStore(this).current();
            deckName = deck.name;
            targets = BuyPolicy.parseTargets(deck.targets);
            if (targets.isEmpty()) throw new IllegalArgumentException("请先填写目标名字");
            policy = new BuyPolicy(targets, prefs.getInt("reserve", GoldReserve.DEFAULT), prefs.getBoolean("autoRefresh", false));
        } catch (IllegalArgumentException e) { status(e.getMessage()); return; }
        previewOnly = prefs.getBoolean("previewOnly", false);
        scanning = true;
        lastRefreshCompleted = 0;
        generation++;
        status("正在加载中文 OCR · 目标：" + targets);
        if (overlay != null) overlay.dockBelowShop();
        handler.post(scanNext);
    }
    String currentDeckName() { return new DeckStore(this).current().name; }
    void selectDeck(String id) {
        DeckStore.Deck deck = new DeckStore(this).select(id);
        deckName = deck.name;
        targets = BuyPolicy.parseTargets(deck.targets);
        android.content.SharedPreferences prefs = getSharedPreferences("probe", MODE_PRIVATE);
        generation++;
        handler.removeCallbacks(scanNext);
        policy = new BuyPolicy(targets, prefs.getInt("reserve", GoldReserve.DEFAULT), prefs.getBoolean("autoRefresh", false));
        status("当前卡组：" + deckName);
        if (scanning && !choosingDeck) handler.post(scanNext);
    }
    void beginDeckSelection() {
        choosingDeck = true; generation++;
        lastRefreshCompleted = 0;
        handler.removeCallbacks(scanNext);
        if (policy != null) policy.shopClosed();
    }
    void endDeckSelection() {
        choosingDeck = false;
        if (scanning) { handler.removeCallbacks(scanNext); handler.post(scanNext); }
    }
    void toggleScanning() {
        if (scanning) stopScanning("已手动暂停"); else startScanning();
    }
    private void retryFrame(String message) {
        if (!scanning) return;
        status(message);
        handler.removeCallbacks(scanNext);
        if (!choosingDeck) handler.postDelayed(scanNext, screenshotIntervalMs);
    }
    private void waitForGame(String message) {
        if (!scanning) return;
        generation++;
        if (policy != null) policy.shopClosed();
        retryFrame(message);
    }
    private static boolean isGamePackage(String pkg) { return pkg.equals("com.tencent.jkchess") || pkg.equals("com.tencent.jk"); }
    private boolean isGameForeground() {
        try {
            for (android.view.accessibility.AccessibilityWindowInfo window : getWindows()) {
                if (window.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION ||
                    !(window.isActive() || window.isFocused())) continue;
                android.view.accessibility.AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                try {
                    if (root.getPackageName() != null) {
                        foreground = root.getPackageName().toString();
                        return isGamePackage(foreground);
                    }
                } finally { root.recycle(); }
            }
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) try {
                if (root.getPackageName() != null) { foreground = root.getPackageName().toString(); return isGamePackage(foreground); }
            } finally { root.recycle(); }
        } catch (RuntimeException e) { android.util.Log.w("JccAssist", "读取前台窗口失败", e); }
        // Never authorize a gesture solely from an old event's package name.
        return false;
    }
    void stopScanning(String reason) {
        scanning = false; generation++;
        handler.removeCallbacks(scanNext);
        status(reason);
    }
    private void scan() {
        if (!scanning || choosingDeck) return;
        if (!isGameForeground()) { waitForGame("已开启 · 等待游戏和商店"); return; }
        if (overlay != null && overlay.overlapsShop()) {
            overlay.dockBelowShop();
            status("正在把悬浮窗移到商店下方……");
            handler.postDelayed(scanNext, 16); return;
        }
        if (busy) { handler.postDelayed(scanNext, 8); return; }
        long remaining = screenshotIntervalMs - (SystemClock.elapsedRealtime() - lastScreenshot);
        if (remaining > 0) { handler.postDelayed(scanNext, remaining); return; }
        busy = true;
        lastScreenshot = SystemClock.elapsedRealtime();
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
                        busy = false; if (token == generation) retryFrame("截图暂未成功，继续等待");
                    } finally { if (hardware != null) hardware.recycle(); }
                }
                @Override public void onFailure(int code) {
                    busy = false;
                    if (token == generation) {
                        if (code == 3) screenshotIntervalMs = Math.min(360, screenshotIntervalMs + 4);
                        if (code == 2) stopScanning("无障碍截图权限失效，请重新开启服务");
                        else retryFrame("等待下一帧，截图错误码 " + code);
                    }
                }
            });
        } catch (Exception e) { busy = false; retryFrame("等待截图恢复：" + e.getMessage()); }
    }
    private void analyze(Bitmap image, long token, long started) {
        BuyPolicy.Frame result = null;
        long ocrStarted = SystemClock.elapsedRealtime();
        boolean reused = false;
        String error = null;
        try {
            if (recognizer == null) recognizer = new ShopOcr(this);
            result = recognizer.analyze(image);
            reused = recognizer.cachedLastFrame();
        } catch (Throwable e) { error = e.toString(); }
        finally { image.recycle(); }
        final BuyPolicy.Frame answer = result;
        final long ocrMs = SystemClock.elapsedRealtime() - ocrStarted;
        final boolean reusedFrame = reused;
        final String failure = error;
        handler.post(() -> {
            busy = false;
            if (!scanning || token != generation) return;
            if (answer == null) { retryFrame("本帧识别暂未成功，继续等待：" + failure); return; }
            if (!answer.layoutValid) { waitForGame("已开启 · 等待 2670×1200 游戏横屏"); return; }
            if (previewOnly) {
                status("仅识别 · " + answer.summary() + "\n商店=" + answer.shopOpen + " 可读=" + answer.readable +
                    " · " + (SystemClock.elapsedRealtime() - started) + "ms\n" + answer.problem);
                handler.post(scanNext); return;
            }
            BuyPolicy.Decision action = policy.decide(answer);
            String stats = "\n已买 " + policy.bought + " · 已刷新 " + policy.refreshed;
            if (action.kind == BuyPolicy.Kind.STOP) { stopScanning(action.message + stats); return; }
            status(action.message + "\n" + answer.summary() + stats);
            if (action.kind == BuyPolicy.Kind.WAIT) { handler.post(scanNext); return; }
            long age = SystemClock.elapsedRealtime() - started;
            android.util.Log.i("JccPerf", "frame_ms=" + age + " ocr_ms=" + ocrMs + " cached=" + reusedFrame + " interval_ms=" + screenshotIntervalMs);
            if (age > MAX_ACTION_FRAME_AGE_MS) {
                status("识别预热或耗时较长，等待新帧"); handler.post(scanNext); return;
            }
            tap(token, action, answer, PhoneLayout.WIDTH, PhoneLayout.HEIGHT, started);
        });
    }
    private void tap(long token, BuyPolicy.Decision action, BuyPolicy.Frame frame, int width, int height, long frameStarted) {
        if (!scanning || token != generation) return;
        if (!PhoneLayout.accepts(width, height)) { waitForGame("已开启 · 等待游戏横屏"); return; }
        if (action.kind == BuyPolicy.Kind.REFRESH && !policy.isAutoRefresh()) {
            status("自动刷新已关闭，取消待执行刷新"); handler.post(scanNext); return;
        }
        if (!isGameForeground()) { waitForGame("已开启 · 等待返回游戏"); return; }
        if (SystemClock.elapsedRealtime() - frameStarted > MAX_ACTION_FRAME_AGE_MS) {
            status("等待更新的游戏画面"); handler.post(scanNext); return;
        }
        if (overlay != null && overlay.overlapsShop()) {
            overlay.dockBelowShop(); handler.post(scanNext); return;
        }
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        int count = action.kind == BuyPolicy.Kind.BUY ? action.slots.length : 1;
        TapTiming timing = new TapTiming(count, random);
        android.accessibilityservice.GestureDescription.Builder builder = new android.accessibilityservice.GestureDescription.Builder();
        for (int i = 0; i < count; i++) {
            float x = (action.kind == BuyPolicy.Kind.BUY ? PhoneLayout.NAME_X[action.slots[i]] + 183 : 2497) + random.nextInt(-9, 10);
            float y = (action.kind == BuyPolicy.Kind.BUY ? 313 : 789) + random.nextInt(-12, 13);
            if (overlay != null && overlay.blocksPoint(x, y)) {
                overlay.dockBelowShop(); retryFrame("正在移开悬浮窗"); return;
            }
            android.graphics.Path path = new android.graphics.Path(); path.moveTo(x, y);
            builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, timing.starts[i], timing.durations[i]));
        }
        android.accessibilityservice.GestureDescription gesture = builder.build();
        final long dispatchStarted = SystemClock.elapsedRealtime();
        try {
            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(android.accessibilityservice.GestureDescription g) {
                    if (!scanning || token != generation) return;
                    policy.sent(action, frame);
                    android.util.Log.i("JccAssist", "Gesture completed: " + action.message + " · scheduled=" + timing.totalMs + "ms");
                    long completed = SystemClock.elapsedRealtime();
                    android.util.Log.i("JccPerf", "action=" + action.kind + " count=" + count + " scheduled_ms=" + timing.totalMs + " actual_ms=" + (completed - dispatchStarted));
                    if (action.kind == BuyPolicy.Kind.REFRESH) {
                        if (lastRefreshCompleted != 0) android.util.Log.i("JccPerf", "refresh_gap_ms=" + (completed - lastRefreshCompleted));
                        lastRefreshCompleted = completed;
                    }
                    handler.post(scanNext);
                }
                @Override public void onCancelled(android.accessibilityservice.GestureDescription g) {
                    if (token == generation) retryFrame("上次点击被取消，继续识别");
                }
            }, handler);
            if (!accepted) stopScanning("系统拒绝手势，请确认无障碍服务已重新启用");
        } catch (RuntimeException e) { stopScanning("点击失败：" + e.getMessage()); }
    }
    void scheduleCapture() {
        stopScanning("连续识别已暂停");
        if (busy) return;
        busy = true;
        status("已预约：8 秒后截图，请切换到游戏。");
        handler.postDelayed(capture, 8000);
    }
    private void status(String value) {
        if (value.equals(lastStatus)) return;
        lastStatus = value;
        getSharedPreferences("probe", MODE_PRIVATE).edit().putString("status", value).apply();
        if (overlay != null) overlay.update(value, scanning);
        android.util.Log.i("JccAssist", value);
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
        worker.execute(() -> { if (recognizer != null) { recognizer.close(); recognizer = null; } });
        worker.shutdown();
        super.onDestroy();
    }
}
