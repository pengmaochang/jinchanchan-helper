package local.jcc.probe;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.provider.Settings;
import android.widget.*;
import java.io.File;

public class MainActivity extends Activity {
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 64, 28, 28);
        TextView title = new TextView(this);
        title.setText("金铲铲 · 单次截图验证\n\n1. 开启无障碍服务\n2. 点击延时截图\n3. 在 8 秒内切换到游戏\n4. 返回这里查看结果；相册查看原图\n");
        title.setTextSize(18);
        layout.addView(title);
        Button settings = new Button(this);
        settings.setText("打开无障碍设置");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(settings);
        Button capture = new Button(this);
        capture.setText("8 秒后截取一张");
        capture.setOnClickListener(v -> {
            CaptureService service = CaptureService.instance;
            if (service == null) Toast.makeText(this, "请先开启本应用的无障碍服务", Toast.LENGTH_LONG).show();
            else service.scheduleCapture();
            update();
        });
        layout.addView(capture);
        status = new TextView(this);
        status.setTextSize(16);
        layout.addView(status);
        Button preview = new Button(this);
        preview.setText("查看最近一次截图预览");
        preview.setOnClickListener(v -> {
            File file = new File(getFilesDir(), "latest.png");
            if (!file.exists()) { Toast.makeText(this, "还没有成功的截图", Toast.LENGTH_SHORT).show(); return; }
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = 2;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bitmap == null) return;
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setImageBitmap(bitmap);
            new android.app.AlertDialog.Builder(this).setTitle("最近一次成功截图（失败时不会更新）")
                .setView(image).setPositiveButton("关闭", (d, w) -> {}).setOnDismissListener(d -> bitmap.recycle()).show();
        });
        layout.addView(preview);
        setContentView(layout);
    }
    private void update() {
        status.setText("\n服务：" + (CaptureService.instance == null ? "未连接" : "已连接") + "\n" +
            getSharedPreferences("probe", MODE_PRIVATE).getString("status", "尚未截图") + "\n");
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
