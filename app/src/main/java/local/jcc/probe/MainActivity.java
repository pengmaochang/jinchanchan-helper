package local.jcc.probe;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.provider.Settings;
import android.widget.*;
import android.view.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import java.io.File;

public class MainActivity extends Activity {
    private TextView status, serviceBadge;
    private static final int BG = 0xff10141d, CARD = 0xff1b2230, FIELD = 0xff121923, INK = 0xffedf0f5, MUTED = 0xff9ba8bd, GOLD = 0xffe8c478;
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
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        deckStore = new DeckStore(this);
        LinearLayout layout = column();
        layout.setFocusableInTouchMode(true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        layout.setPadding(dp(20), dp(20), dp(20), dp(28));
        LinearLayout brand = row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.jcc_icon);
        icon.setBackground(shape(FIELD, 16)); icon.setClipToOutline(true);
        brand.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout brandText = column(); brandText.setPadding(dp(12), 0, 0, 0);
        TextView title = label(getString(R.string.app_name), 21, INK);
        title.setTypeface(null, Typeface.BOLD); brandText.addView(title);
        brandText.addView(label("你的随身 D 牌搭档", 12, MUTED));
        brand.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        layout.addView(brand); space(layout, 22);

        LinearLayout connection = card(layout, "准备就绪，再上场", "选择卡组，显示悬浮窗后开启自动拿卡。");
        serviceBadge = label("", 12, GOLD); connection.addView(serviceBadge); space(connection, 8);
        status = label("", 13, MUTED); status.setLineSpacing(dp(3), 1); connection.addView(status);
        Button settings = button("无障碍设置  ›", false);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addButton(connection, settings);

        LinearLayout decks = card(layout, "我的卡组", "每行一个卡名，按从上到下的顺序优先购买。");
        deckSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        deckSpinner.setBackground(shape(FIELD, 10));
        decks.addView(deckSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        space(decks, 14); decks.addView(label("卡组名称", 12, MUTED)); space(decks, 6);
        deckNameInput = input("给这套卡组起个名字"); deckNameInput.setSingleLine(true); decks.addView(deckNameInput);
        space(decks, 14); decks.addView(label("优先购买", 12, MUTED)); space(decks, 6);
        targetNames = input("例如：可酷伯\n黛安娜");
        targetNames.setMinLines(3); targetNames.setMaxLines(6); targetNames.setGravity(Gravity.TOP);
        targetNames.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        targetNames.setText(getSharedPreferences("probe", MODE_PRIVATE).getString("targets", "可酷伯"));
        decks.addView(targetNames); space(decks, 10);
        LinearLayout deckButtons = row();
        Button newDeck = button("新建", false);
        newDeck.setOnClickListener(v -> { deckSpinner.setSelection(0); newDeckEditor(); });
        Button saveDeck = button("保存卡组", true);
        saveDeck.setOnClickListener(v -> {
            try { DeckStore.Deck saved = persistDeck(); Toast.makeText(this, "已保存：" + saved.name, Toast.LENGTH_SHORT).show(); }
            catch (RuntimeException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        Button deleteDeck = button("删除", false); deleteDeck.setTextColor(0xffefaaa1); deleteDeck.setOnClickListener(v -> deleteDeck());
        weightedButton(deckButtons, newDeck); weightedButton(deckButtons, saveDeck); weightedButton(deckButtons, deleteDeck);
        decks.addView(deckButtons);
        deckSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
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

        LinearLayout options = card(layout, "运行偏好", "关闭自动刷新时，手动刷新也会自动买卡。");
        autoRefresh = new Switch(this); autoRefresh.setText("自动刷新商店"); autoRefresh.setTextSize(15);
        autoRefresh.setTextColor(INK); tintToggle(autoRefresh);
        autoRefresh.setChecked(getSharedPreferences("probe", MODE_PRIVATE).getBoolean("autoRefresh", false));
        options.addView(autoRefresh, new LinearLayout.LayoutParams(-1, dp(48)));
        space(options, 10);
        LinearLayout reserveRow = row();
        LinearLayout reserveText = column(); reserveText.addView(label("保留金币", 15, INK));
        reserveText.addView(label("0 表示不保留", 12, MUTED));
        reserveRow.addView(reserveText, new LinearLayout.LayoutParams(0, -2, 1));
        goldReserve = input("0"); goldReserve.setSingleLine(true); goldReserve.setGravity(Gravity.CENTER);
        goldReserve.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        goldReserve.setText(String.valueOf(getSharedPreferences("probe", MODE_PRIVATE).getInt("reserve", 0)));
        reserveRow.addView(goldReserve, new LinearLayout.LayoutParams(dp(80), dp(48))); options.addView(reserveRow);
        space(options, 14);
        previewOnly = new CheckBox(this); previewOnly.setText("仅识别，不执行点击"); previewOnly.setTextSize(13); previewOnly.setTextColor(MUTED);
        previewOnly.setButtonTintList(toggleColors());
        previewOnly.setChecked(getSharedPreferences("probe", MODE_PRIVATE).getBoolean("previewOnly", false)); options.addView(previewOnly);

        Button floating = button("显示悬浮窗", true);
        floating.setOnClickListener(v -> {
            if (!saveSettings()) return;
            if (CaptureService.instance == null) Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show();
            else CaptureService.instance.showOverlay();
        });
        addButton(layout, floating);
        TextView helper = label("自动保存当前卡组与运行设置", 12, MUTED); helper.setGravity(Gravity.CENTER);
        layout.addView(helper); space(layout, 12);
        LinearLayout actions = row();
        Button save = button("保存设置", false); save.setOnClickListener(v -> saveSettings()); weightedButton(actions, save);
        Button hide = button("关闭悬浮窗", false);
        hide.setOnClickListener(v -> { if (CaptureService.instance != null) CaptureService.instance.hideOverlay(); }); weightedButton(actions, hide);
        layout.addView(actions); space(layout, 18);

        LinearLayout tools = column(); tools.setPadding(dp(16), dp(4), dp(16), dp(12)); tools.setBackground(shape(CARD, 16));
        layout.addView(tools);
        Button toolsToggle = button("截图与诊断  ＋", false); addButton(tools, toolsToggle);
        LinearLayout diagnostics = column(); diagnostics.setVisibility(View.GONE); tools.addView(diagnostics);
        toolsToggle.setOnClickListener(v -> {
            boolean show = diagnostics.getVisibility() != View.VISIBLE;
            diagnostics.setVisibility(show ? View.VISIBLE : View.GONE);
            toolsToggle.setText(show ? "截图与诊断  −" : "截图与诊断  ＋");
        });
        Button capture = button("8 秒后截图", false);
        capture.setOnClickListener(v -> {
            CaptureService service = CaptureService.instance;
            if (service == null) Toast.makeText(this, "请先开启本应用的无障碍服务", Toast.LENGTH_LONG).show();
            else service.scheduleCapture();
            update();
        }); addButton(diagnostics, capture);
        Button preview = button("查看最近截图", false); preview.setOnClickListener(v -> showPreview()); addButton(diagnostics, preview);
        TextView version = label("v0.9.0  ·  小米 14  ·  本地离线识别", 11, MUTED);
        version.setGravity(Gravity.CENTER); space(layout, 22); layout.addView(version);

        FrameLayout centered = new FrameLayout(this);
        FrameLayout.LayoutParams content = new FrameLayout.LayoutParams(dp(600), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        centered.addView(layout, content);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        scroll.addView(centered); scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
            scroll.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard));
            return insets;
        });
        centered.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = Math.min(dp(600), r - l);
            if (content.width != width) { content.width = width; layout.setLayoutParams(content); }
        });
        reloadDeckChoices(deckStore.current().id);
        setContentView(scroll); scroll.requestApplyInsets(); layout.requestFocus(); update();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void space(LinearLayout parent, int size) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size))); }
    private LinearLayout card(LinearLayout parent, String title, String subtitle) {
        LinearLayout v = column(); v.setPadding(dp(18), dp(18), dp(18), dp(18)); v.setBackground(shape(CARD, 18));
        TextView heading = label(title, 17, INK); heading.setTypeface(null, Typeface.BOLD); v.addView(heading);
        space(v, 6); TextView hint = label(subtitle, 12, MUTED); hint.setLineSpacing(dp(3), 1); v.addView(hint); space(v, 16);
        parent.addView(v, new LinearLayout.LayoutParams(-1, -2)); space(parent, 14); return v;
    }
    private EditText input(String hint) {
        EditText v = new EditText(this); v.setTextSize(15); v.setTextColor(INK); v.setHintTextColor(0xff6d7b90); v.setHint(hint);
        v.setPadding(dp(12), dp(12), dp(12), dp(12)); v.setBackground(shape(FIELD, 10)); v.setSelectAllOnFocus(false); return v;
    }
    private Button button(String text, boolean primary) {
        Button v = new Button(this); v.setText(text); v.setTextSize(14); v.setAllCaps(false);
        v.setTypeface(null, Typeface.BOLD); v.setTextColor(primary ? BG : INK);
        v.setBackgroundTintList(null); v.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(primary ? 0x22000000 : 0x22ffffff), shape(primary ? GOLD : 0xff283246, 12), null));
        v.setMinHeight(dp(48)); v.setMinimumHeight(dp(48)); v.setMinWidth(0); v.setMinimumWidth(0);
        v.setPadding(dp(8), 0, dp(8), 0); v.setStateListAnimator(null); return v;
    }
    private void addButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48)); p.topMargin = dp(8); p.bottomMargin = dp(8); parent.addView(button, p);
    }
    private void weightedButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1); p.setMargins(dp(3), 0, dp(3), 0); row.addView(button, p);
    }
    private ColorStateList toggleColors() {
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{GOLD, 0xff728097});
    }
    private void tintToggle(Switch toggle) {
        toggle.setThumbTintList(toggleColors());
        toggle.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0xff8a7042, 0xff3b4659}));
    }
    private void showPreview() {
        File file = new File(getFilesDir(), "latest.png");
        if (!file.exists()) { Toast.makeText(this, "还没有成功的截图", Toast.LENGTH_SHORT).show(); return; }
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options(); opts.inSampleSize = 2;
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts); if (bitmap == null) return;
        ImageView image = new ImageView(this); image.setAdjustViewBounds(true); image.setImageBitmap(bitmap);
        new android.app.AlertDialog.Builder(this).setTitle("最近一次截图").setView(image)
            .setPositiveButton("关闭", (d, w) -> {}).setOnDismissListener(d -> bitmap.recycle()).show();
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
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View recycled, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, recycled, parent);
                view.setTextColor(INK); view.setTextSize(15); view.setPadding(dp(12), dp(8), dp(12), dp(8)); return view;
            }
            @Override public View getDropDownView(int position, View recycled, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, recycled, parent);
                view.setTextColor(INK); view.setBackgroundColor(CARD); view.setPadding(dp(16), dp(14), dp(16), dp(14)); return view;
            }
        };
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
        boolean connected = CaptureService.instance != null;
        serviceBadge.setText(connected ? "● 无障碍服务已连接" : "○ 需要开启无障碍服务");
        serviceBadge.setTextColor(connected ? 0xff77d9b4 : GOLD);
        status.setText(getSharedPreferences("probe", MODE_PRIVATE).getString("status", "开启服务后，即可使用悬浮窗。"));
    }
    @Override protected void onResume() {
        super.onResume();
        if (deckStore != null && editingDeckId != null && !deckStore.current().id.equals(editingDeckId)) reloadDeckChoices(deckStore.current().id);
        handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
