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
            for (int i = 0; i < 3; i++) {
                File file = new File(getTargetContext().getExternalFilesDir(null), "fixture" + i + ".jpg");
                Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (image == null) throw new IllegalStateException("样本不存在：" + file);
                long start = android.os.SystemClock.elapsedRealtime();
                BuyPolicy.Frame f;
                try {
                    f = ocr.analyze(image);
                    if (i == 1) for (int slot = 0; slot < 5; slot++) {
                        ShopOcr.Reading raw = ocr.readCardName(image, slot);
                        log.append("raw name ").append(slot).append(": ").append(raw.text).append(" / ").append(raw.confidence).append('\n');
                    }
                } finally { image.recycle(); }
                log.append(i).append(": open=").append(f.shopOpen).append(" readable=").append(f.readable)
                    .append(" ").append(f.summary()).append(" costs=").append(java.util.Arrays.toString(f.costs))
                    .append(" ms=").append(android.os.SystemClock.elapsedRealtime() - start).append('\n');
                result.putString("details", log.toString());
                if (i == 0 && f.shopOpen) throw new AssertionError("收起商店误判");
                if (i > 0 && (!f.shopOpen || !f.readable || f.gold != (i == 1 ? 74 : 73))) throw new AssertionError("商店或金币识别失败");
                if (i == 1 && (!"可酷伯".equals(f.names[0]) || f.costs[0] != 1)) throw new AssertionError("目标识别失败");
                if (i == 2 && !"".equals(f.names[0])) throw new AssertionError("空位识别失败");
            }
            result.putString("result", "PASS"); finish(-1, result);
        } catch (Throwable e) { result.putString("error", e.toString()); finish(0, result); }
    }
}
