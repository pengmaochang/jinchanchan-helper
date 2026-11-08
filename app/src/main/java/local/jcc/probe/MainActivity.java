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
        title.setText("金铲铲 · 商店识别验证 v0.2\n\n选择目标 → 显示悬浮窗 → 切到游戏 → 手动打开商店 → 在悬浮窗点击开始。\n\n本版只识别，不购买、不刷新。模板来自你提供的截图，尚需验证实机闪光场景。\n");
        title.setTextSize(18);
        layout.addView(title);
        Button settings = new Button(this);
        settings.setText("打开无障碍设置");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(settings);
        TextView label = new TextView(this);
        label.setText("目标卡（首版已收录的五张样本）");
        layout.addView(label);
        Spinner target = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ShopVision.NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        target.setAdapter(adapter);
        target.setSelection(Math.max(0, Math.min(4, getSharedPreferences("probe", MODE_PRIVATE).getInt("target", 0))));
        layout.addView(target);
        target.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int position, long id) {
                getSharedPreferences("probe", MODE_PRIVATE).edit().putInt("target", position).apply();
                if (CaptureService.instance != null) CaptureService.instance.stopScanning("目标已设为：" + ShopVision.NAMES[position]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        Button floating = new Button(this);
        floating.setText("显示识别悬浮窗");
        floating.setOnClickListener(v -> {
            if (CaptureService.instance == null) Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show();
            else CaptureService.instance.showOverlay();
        });
        layout.addView(floating);
        Button hide = new Button(this);
        hide.setText("暂停并隐藏悬浮窗");
        hide.setOnClickListener(v -> { if (CaptureService.instance != null) CaptureService.instance.hideOverlay(); });
        layout.addView(hide);
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
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            layout.setPadding(28 + bars.left, 20 + bars.top, 28 + bars.right, 20 + bars.bottom);
            return insets;
        });
        setContentView(scroll);
    }
    private void update() {
        status.setText("\n服务：" + (CaptureService.instance == null ? "未连接" : "已连接") + "\n" +
            getSharedPreferences("probe", MODE_PRIVATE).getString("status", "尚未截图") + "\n");
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
