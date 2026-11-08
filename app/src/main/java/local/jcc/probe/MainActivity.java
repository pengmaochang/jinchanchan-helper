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
    private EditText deckNameInput;
    private Spinner deckSpinner;
    private DeckStore deckStore;
    private java.util.List<DeckStore.Deck> savedDecks;
    private String editingDeckId;
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
        title.setText("金铲铲助手 v0.8.4 · 极速测试\n\n半透明图标点击展开，点外部收起。选择卡组，切换自动拿卡和自动刷新。开启后开商店自动买、关商店待命。\n\n去除额外等待，按系统截图极限运行。短点击可能漏点，会补买；只用足够新的画面派发。仅适配小米 14 的 2670×1200 横屏。\n");
        title.setTextSize(18);
        layout.addView(title);
        Button settings = new Button(this);
        settings.setText("打开无障碍设置");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(settings);
        deckStore = new DeckStore(this);
        TextView deckLabel = new TextView(this); deckLabel.setText("已保存的卡组"); layout.addView(deckLabel);
        deckSpinner = new Spinner(this); layout.addView(deckSpinner);
        deckNameInput = new EditText(this); deckNameInput.setSingleLine(true); deckNameInput.setHint("卡组名称，例如：五费追卡");
        layout.addView(deckNameInput);
        TextView label = new TextView(this);
        label.setText("目标卡名：每行一个，越靠前越优先");
        layout.addView(label);
        targetNames = new EditText(this);
        targetNames.setMinLines(2); targetNames.setMaxLines(5); targetNames.setGravity(android.view.Gravity.TOP);
        targetNames.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        targetNames.setText(getSharedPreferences("probe", MODE_PRIVATE).getString("targets", "可酷伯"));
        layout.addView(targetNames);
        LinearLayout deckButtons = new LinearLayout(this);
        Button newDeck = new Button(this); newDeck.setText("新建");
        newDeck.setOnClickListener(v -> { deckSpinner.setSelection(0); newDeckEditor(); });
        deckButtons.addView(newDeck, new LinearLayout.LayoutParams(0, -2, 1));
        Button saveDeck = new Button(this); saveDeck.setText("保存卡组");
        saveDeck.setOnClickListener(v -> {
            try { DeckStore.Deck saved = persistDeck(); Toast.makeText(this, "已保存：" + saved.name, Toast.LENGTH_SHORT).show(); }
            catch (RuntimeException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        deckButtons.addView(saveDeck, new LinearLayout.LayoutParams(0, -2, 1));
        Button deleteDeck = new Button(this); deleteDeck.setText("删除"); deleteDeck.setOnClickListener(v -> deleteDeck());
        deckButtons.addView(deleteDeck, new LinearLayout.LayoutParams(0, -2, 1)); layout.addView(deckButtons);
        deckSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                if (position != deckSpinner.getSelectedItemPosition()) return;
                if (position == 0) { if (editingDeckId != null) newDeckEditor(); return; }
                if (savedDecks == null || position > savedDecks.size()) return;
                DeckStore.Deck deck = savedDecks.get(position - 1);
                if (deck.id.equals(editingDeckId)) return;
                loadDeckEditor(deck);
                if (CaptureService.instance != null) CaptureService.instance.selectDeck(deck.id); else deckStore.select(deck.id);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
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
        reloadDeckChoices(deckStore.current().id);
        setContentView(scroll);
    }
    private void newDeckEditor() {
        editingDeckId = null; deckNameInput.setText(""); targetNames.setText(""); deckNameInput.requestFocus();
    }
    private void loadDeckEditor(DeckStore.Deck deck) {
        editingDeckId = deck.id; deckNameInput.setText(deck.name); targetNames.setText(deck.targets);
    }
    private void reloadDeckChoices(String selected) {
        savedDecks = deckStore.all();
        java.util.List<String> labels = new java.util.ArrayList<>(); labels.add("＋ 新建卡组"); int position = 0;
        for (int i = 0; i < savedDecks.size(); i++) {
            DeckStore.Deck deck = savedDecks.get(i); labels.add(deck.name);
            if (deck.id.equals(selected)) { position = i + 1; loadDeckEditor(deck); }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deckSpinner.setAdapter(adapter); deckSpinner.setSelection(position);
    }
    private DeckStore.Deck persistDeck() {
        DeckStore.Deck saved = deckStore.save(editingDeckId, deckNameInput.getText().toString(), targetNames.getText().toString());
        reloadDeckChoices(saved.id);
        if (CaptureService.instance != null) CaptureService.instance.selectDeck(saved.id);
        return saved;
    }
    private void deleteDeck() {
        if (editingDeckId == null) { Toast.makeText(this, "当前卡组尚未保存", Toast.LENGTH_SHORT).show(); return; }
        final String deleting = editingDeckId;
        new android.app.AlertDialog.Builder(this).setTitle("删除卡组")
            .setMessage("删除“" + deckNameInput.getText() + "”？")
            .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> {
                try {
                    DeckStore.Deck current = deckStore.delete(deleting); reloadDeckChoices(current.id);
                    if (CaptureService.instance != null) CaptureService.instance.selectDeck(current.id);
                } catch (RuntimeException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
            }).show();
    }
    private boolean saveSettings() {
        try {
            java.util.List<String> targets = BuyPolicy.parseTargets(targetNames.getText().toString());
            if (targets.isEmpty()) throw new IllegalArgumentException("请填写至少一个目标卡名");
            int reserve = Integer.parseInt(goldReserve.getText().toString().trim());
            if (reserve < 0 || reserve > 999) throw new IllegalArgumentException("保留金币应为 0～999");
            persistDeck();
            getSharedPreferences("probe", MODE_PRIVATE).edit().putString("targets", String.join("\n", targets))
                .putInt("reserve", reserve).putBoolean("autoRefresh", autoRefresh.isChecked())
                .putBoolean("previewOnly", previewOnly.isChecked()).apply();
            if (CaptureService.instance != null) CaptureService.instance.hideOverlay();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            return true;
        } catch (RuntimeException e) { Toast.makeText(this, "设置无效：" + e.getMessage(), Toast.LENGTH_LONG).show(); return false; }
    }
    private void update() {
        status.setText("\n服务：" + (CaptureService.instance == null ? "未连接" : "已连接") + "\n" +
            getSharedPreferences("probe", MODE_PRIVATE).getString("status", "尚未截图") + "\n");
    }
    @Override protected void onResume() {
        super.onResume();
        if (deckStore != null && editingDeckId != null && !deckStore.current().id.equals(editingDeckId)) reloadDeckChoices(deckStore.current().id);
        handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
