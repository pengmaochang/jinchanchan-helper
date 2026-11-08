package local.jcc.probe;

import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.*;
import java.io.File;

/** Read-only fixture harness; never invokes gestures or starts an automation session. */
public class OcrChecks extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try (ShopOcr ocr = new ShopOcr(getTargetContext())) {
            StringBuilder log = new StringBuilder();
            StringBuilder failures = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                File file = new File(getTargetContext().getExternalFilesDir(null), "fixture" + i + (i == 3 ? ".png" : ".jpg"));
                Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (image == null) throw new IllegalStateException("样本不存在：" + file);
                long start = android.os.SystemClock.elapsedRealtime();
                BuyPolicy.Frame f;
                long analyzeElapsed;
                try {
                    f = ocr.analyze(image);
                    analyzeElapsed = android.os.SystemClock.elapsedRealtime() - start;
                    if (i == 1 || i == 3) for (int slot = 0; slot < 5; slot++) {
                        ShopOcr.Reading raw = ocr.readCardName(image, slot);
                        log.append("raw name ").append(slot).append(": ").append(raw.text).append(" / ").append(raw.confidence).append('\n');
                    }
                } finally { image.recycle(); }
                log.append(i).append(": open=").append(f.shopOpen).append(" readable=").append(f.readable)
                    .append(" ").append(f.summary()).append(" costs=").append(java.util.Arrays.toString(f.costs))
                    .append(" analyze_ms=").append(analyzeElapsed).append('\n');
                result.putString("details", log.toString());
                if (i == 0 && f.shopOpen) failures.append("收起商店误判；");
                if (i > 0 && (!f.shopOpen || !f.readable || f.gold != (i == 1 ? 74 : i == 2 ? 73 : 31)))
                    failures.append(i).append("商店或金币识别失败：").append(f.problem).append('；');
                if (i == 1 && (!"可酷伯".equals(f.names[0]) || f.costs[0] != 1)) failures.append("目标识别失败；");
                if (i == 2 && !"".equals(f.names[0])) failures.append("空位识别失败；");
                if (i == 3 && !java.util.Arrays.equals(f.names, new String[]{"卡蜜尔", "凯特琳", "沃里克", "阿利斯塔", "远古石甲虫"}))
                    failures.append("现场卡名不匹配；");
                int[][] expectedCosts = {{}, {1,1,1,1,2}, {0,1,1,1,2}, {1,2,2,2,3}};
                if (i > 0 && !java.util.Arrays.equals(f.costs, expectedCosts[i])) failures.append(i).append("费用不匹配；");
            }
            if (failures.length() > 0) throw new AssertionError(failures.toString());
            File extra = new File(getTargetContext().getExternalFilesDir(null), "fixture4.jpg");
            if (extra.isFile()) {
                Bitmap image = BitmapFactory.decodeFile(extra.getAbsolutePath());
                if (image == null) throw new AssertionError("新样本读取失败");
                try {
                    BuyPolicy.Frame f = ocr.analyze(image);
                    ShopOcr.Reading direct = ocr.readCardName(image, 2);
                    result.putString("new_sample", f.summary() + " readable=" + f.readable + " shop=" + f.shopOpen +
                        " costs=" + java.util.Arrays.toString(f.costs) + " problem=" + f.problem + " direct_slot3=" + direct.text);
                    if (!"黛安娜".equals(f.names[2])) throw new AssertionError("新样本第三格未识别为黛安娜");
                    BuyPolicy.Decision action = new BuyPolicy(java.util.List.of("黛安娜"), 0, false).decide(f);
                    if (action.kind != BuyPolicy.Kind.BUY || !java.util.Arrays.equals(action.slots, new int[]{2}))
                        throw new AssertionError("黛安娜未产生第三格购买决策");
                    int[] source = PhoneLayout.name(2);
                    Bitmap nameCrop = Bitmap.createBitmap(image, source[0], source[1], source[2], source[3]);
                    try {
                        for (int slot = 0; slot < 5; slot++) {
                            Bitmap shifted = image.copy(Bitmap.Config.ARGB_8888, true);
                            try {
                                new Canvas(shifted).drawBitmap(nameCrop, PhoneLayout.NAME_X[slot], PhoneLayout.NAME_Y, null);
                                if (!"黛安娜".equals(ocr.analyze(shifted).names[slot])) throw new AssertionError("黛安娜卡位迁移失败：" + slot);
                            } finally { shifted.recycle(); }
                        }
                    } finally { nameCrop.recycle(); }
                } finally { image.recycle(); }
            }
            Bitmap a = BitmapFactory.decodeFile(new File(getTargetContext().getExternalFilesDir(null), "fixture1.jpg").getAbsolutePath());
            Bitmap b = BitmapFactory.decodeFile(new File(getTargetContext().getExternalFilesDir(null), "fixture3.png").getAbsolutePath());
            if (a == null || b == null) throw new AssertionError("测速样本缺失");
            try {
                Bitmap damaged = a.copy(Bitmap.Config.ARGB_8888, true);
                try {
                    Canvas canvas = new Canvas(damaged); Paint paint = new Paint(); paint.setColor(Color.BLACK);
                    int[] fee = PhoneLayout.cost(0);
                    canvas.drawRect(fee[0], fee[1], fee[0]+fee[2], fee[1]+fee[3], paint);
                    BuyPolicy.Frame f = ocr.analyze(damaged);
                    if (f.costs[0] != 1) throw new AssertionError("已知目标费用缺失时未使用离线目录恢复");
                    int[] name = PhoneLayout.name(4);
                    canvas.drawRect(name[0], name[1], name[0]+name[2], name[1]+name[3], paint);
                    paint.setColor(Color.WHITE); canvas.drawRect(name[0]+10, name[1]+10, name[0]+160, name[1]+42, paint);
                    f = ocr.analyze(damaged);
                    if (f.readable || f.names[4] != null) throw new AssertionError("未读到的非空卡名不得误认为已购买空位");
                    if (new BuyPolicy(java.util.List.of("可酷伯"), 0, true).decide(f).kind != BuyPolicy.Kind.BUY)
                        throw new AssertionError("无关卡名未读到时阻塞了已知目标");
                    if (new BuyPolicy(java.util.List.of("黛安娜"), 0, true).decide(f).kind != BuyPolicy.Kind.WAIT)
                        throw new AssertionError("存在未读卡位时不应自动刷新");
                } finally { damaged.recycle(); }
                ocr.analyze(b); ocr.requestRecovery();
                BuyPolicy.Frame recovered = ocr.analyze(b);
                if (ocr.cachedLastFrame() || recovered.gold != 31) throw new AssertionError("恢复请求未绕过缓存并重读金币");
                ocr.analyze(b);
                if (!ocr.cachedLastFrame()) throw new AssertionError("恢复后未保留快速缓存");
                long[] changed = new long[12], unchanged = new long[6];
                for (int i = 0; i < changed.length; i++) {
                    long t = android.os.SystemClock.elapsedRealtime();
                    BuyPolicy.Frame f = ocr.analyze(i % 2 == 0 ? a : b);
                    changed[i] = android.os.SystemClock.elapsedRealtime() - t;
                    if (!f.readable || f.gold != (i % 2 == 0 ? 74 : 31)) throw new AssertionError("变化帧缓存失效检查失败");
                }
                for (int i = 0; i < unchanged.length; i++) {
                    long t = android.os.SystemClock.elapsedRealtime(); ocr.analyze(b);
                    unchanged[i] = android.os.SystemClock.elapsedRealtime() - t;
                    if (!ocr.cachedLastFrame()) throw new AssertionError("未变化帧没有复用");
                }
                java.util.Arrays.sort(changed); java.util.Arrays.sort(unchanged);
                result.putString("benchmark", "changed_median_ms=" + changed[changed.length / 2] +
                    " changed_max_ms=" + changed[changed.length - 1] + " unchanged_median_ms=" + unchanged[unchanged.length / 2]);
            } finally { a.recycle(); b.recycle(); }
            result.putString("result", "PASS"); finish(-1, result);
        } catch (Throwable e) { result.putString("error", e.toString()); finish(0, result); }
    }
}
