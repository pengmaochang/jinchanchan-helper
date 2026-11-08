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
    private EditText targetNames, goldReserve;
    private Switch autoRefresh;
    private CheckBox previewOnly;
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
        title.setText("金铲铲助手 v0.5 · 小米 14\n\n保存目标并显示悬浮窗，点击“开启”。保持开启时：打开商店自动买卡，关闭商店停止操作，再打开继续；点击同一个按钮“暂停”退出待命。\n\n单次识别，不做彩色/灰度交叉验证。偶发读不清自动继续等待。仅适配 2670×1200 横屏。\n");
        title.setTextSize(18);
        layout.addView(title);
        Button settings = new Button(this);
        settings.setText("打开无障碍设置");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(settings);
        TextView label = new TextView(this);
        label.setText("目标卡名：每行一个，越靠前越优先");
        layout.addView(label);
        targetNames = new EditText(this);
        targetNames.setMinLines(2); targetNames.setMaxLines(5); targetNames.setGravity(android.view.Gravity.TOP);
        targetNames.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        targetNames.setText(getSharedPreferences("probe", MODE_PRIVATE).getString("targets", "可酷伯"));
        layout.addView(targetNames);
        autoRefresh = new Switch(this);
        autoRefresh.setText("自动刷新（关闭后由你手动刷新，仍自动买卡）");
        autoRefresh.setChecked(getSharedPreferences("probe", MODE_PRIVATE).getBoolean("autoRefresh", false));
        layout.addView(autoRefresh);
        TextView goldLabel = new TextView(this); goldLabel.setText("保留金币（0 表示不保留）"); layout.addView(goldLabel);
        goldReserve = new EditText(this);
        goldReserve.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        goldReserve.setText(String.valueOf(getSharedPreferences("probe", MODE_PRIVATE).getInt("reserve", 0)));
        layout.addView(goldReserve);
        previewOnly = new CheckBox(this);
        previewOnly.setText("仅识别，不点击（用于检查卡名和金币）");
        previewOnly.setChecked(getSharedPreferences("probe", MODE_PRIVATE).getBoolean("previewOnly", false));
        layout.addView(previewOnly);
        Button save = new Button(this); save.setText("保存设置");
        save.setOnClickListener(v -> saveSettings()); layout.addView(save);
        Button floating = new Button(this);
        floating.setText("保存并显示悬浮窗");
        floating.setOnClickListener(v -> {
            if (!saveSettings()) return;
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
    private boolean saveSettings() {
        try {
            java.util.List<String> targets = BuyPolicy.parseTargets(targetNames.getText().toString());
            if (targets.isEmpty()) throw new IllegalArgumentException("请填写至少一个目标卡名");
            int reserve = Integer.parseInt(goldReserve.getText().toString().trim());
            if (reserve < 0 || reserve > 999) throw new IllegalArgumentException("保留金币应为 0～999");
            getSharedPreferences("probe", MODE_PRIVATE).edit().putString("targets", String.join("\n", targets))
                .putInt("reserve", reserve).putBoolean("autoRefresh", autoRefresh.isChecked())
                .putBoolean("previewOnly", previewOnly.isChecked()).apply();
            if (CaptureService.instance != null) CaptureService.instance.hideOverlay();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            return true;
        } catch (IllegalArgumentException e) { Toast.makeText(this, "设置无效：" + e.getMessage(), Toast.LENGTH_LONG).show(); return false; }
    }
    private void update() {
        status.setText("\n服务：" + (CaptureService.instance == null ? "未连接" : "已连接") + "\n" +
            getSharedPreferences("probe", MODE_PRIVATE).getString("status", "尚未截图") + "\n");
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
