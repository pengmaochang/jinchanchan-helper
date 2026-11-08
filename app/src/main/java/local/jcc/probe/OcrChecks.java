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
            Bitmap a = BitmapFactory.decodeFile(new File(getTargetContext().getExternalFilesDir(null), "fixture1.jpg").getAbsolutePath());
            Bitmap b = BitmapFactory.decodeFile(new File(getTargetContext().getExternalFilesDir(null), "fixture3.png").getAbsolutePath());
            if (a == null || b == null) throw new AssertionError("测速样本缺失");
            try {
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
