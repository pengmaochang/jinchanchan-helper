package local.jcc.probe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** A translucent draggable bubble; an outside touch dismisses its compact control panel. */
final class ScanOverlay {
    private final CaptureService service;
    private final WindowManager windows;
    private final FrameLayout root;
    private final BubbleView bubble;
    private final LinearLayout panel;
    private final WindowManager.LayoutParams params;
    private final Switch assistSwitch, refreshSwitch;
    private final TextView deckLabel;
    private final EditText reserveInput;
    private PopupMenu deckMenu;
    private boolean attached, expanded, running, syncing, moved;
    private float downX, downY;
    private int originX, originY, bubbleX, bubbleY;

    ScanOverlay(CaptureService service) {
        this.service = service;
        windows = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        root = new FrameLayout(service) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (expanded && deckMenu == null) collapse();
                    return true;
                }
                return super.dispatchTouchEvent(event);
            }
        };
        bubble = new BubbleView(service);
        bubble.setOnClickListener(v -> expand());
        bubble.setOnTouchListener((v, event) -> drag(v, event, true));
        panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFocusableInTouchMode(true);
        panel.setPadding(dp(12), dp(8), dp(12), dp(10));
        panel.setBackground(background(Color.argb(246, 24, 33, 48), 16, Color.rgb(61, 77, 97)));
        LinearLayout heading = new LinearLayout(service); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(service.getString(R.string.app_name), 14, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setOnTouchListener((v, event) -> drag(v, event, false));
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(32), 1));
        TextView fold = text("⌄", 24, Color.rgb(169, 187, 206)); fold.setGravity(Gravity.CENTER);
        fold.setContentDescription("收起为图标"); fold.setOnClickListener(v -> collapse());
        heading.addView(fold, new LinearLayout.LayoutParams(dp(32), dp(32))); panel.addView(heading);
        TextView label = text("当前卡组", 10, Color.rgb(148, 168, 190)); panel.addView(label);
        deckLabel = text(service.currentDeckName() + "  ▾", 13, Color.rgb(190, 239, 230));
        deckLabel.setSingleLine(true); deckLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        deckLabel.setGravity(Gravity.CENTER_VERTICAL);
        deckLabel.setPadding(dp(10), 0, dp(8), 0);
        deckLabel.setBackground(background(Color.rgb(38, 51, 69), 8, Color.TRANSPARENT));
        deckLabel.setOnClickListener(v -> chooseDeck());
        panel.addView(deckLabel, new LinearLayout.LayoutParams(-1, dp(38)));
        assistSwitch = makeSwitch("自动拿卡");
        refreshSwitch = makeSwitch("自动刷新");
        panel.addView(assistSwitch, new LinearLayout.LayoutParams(-1, dp(44)));
        panel.addView(refreshSwitch, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout reserveRow = new LinearLayout(service); reserveRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView reserveLabel = text("刷新留金", 13, Color.WHITE);
        reserveRow.addView(reserveLabel, new LinearLayout.LayoutParams(0, dp(52), 1)); reserveLabel.setGravity(Gravity.CENTER_VERTICAL);
        reserveInput = new EditText(service); reserveInput.setSingleLine(true); reserveInput.setTextSize(15);
        reserveInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); reserveInput.setTextColor(Color.rgb(232, 196, 120));
        reserveInput.setGravity(Gravity.CENTER); reserveInput.setPadding(0, 0, 0, 0);
        reserveInput.setSelectAllOnFocus(true); reserveInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        reserveInput.setBackground(background(Color.rgb(17, 25, 37), 8, Color.rgb(61, 77, 97)));
        reserveInput.setText(String.valueOf(service.goldReserve())); reserveInput.setContentDescription("自动刷新保留金币，不限制购买目标卡");
        reserveRow.addView(reserveInput, new LinearLayout.LayoutParams(dp(56), dp(44)));
        LinearLayout steps = new LinearLayout(service); steps.setOrientation(LinearLayout.VERTICAL);
        TextView up = text("▲", 12, Color.rgb(232, 196, 120)); up.setGravity(Gravity.CENTER); up.setContentDescription("增加保留金币到下一档");
        TextView down = text("▼", 12, Color.rgb(232, 196, 120)); down.setGravity(Gravity.CENTER); down.setContentDescription("减少保留金币到上一档");
        steps.addView(up, new LinearLayout.LayoutParams(dp(40), dp(28))); steps.addView(down, new LinearLayout.LayoutParams(dp(40), dp(28)));
        up.setOnClickListener(v -> stepReserve(true)); down.setOnClickListener(v -> stepReserve(false)); reserveRow.addView(steps);
        panel.addView(reserveRow);
        reserveInput.setOnEditorActionListener((v, action, event) -> { finishReserveInput(); hideKeyboard(); reserveInput.clearFocus(); return true; });
        reserveInput.setOnFocusChangeListener((v, focused) -> { if (!focused) finishReserveInput(); });
        reserveInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncing || s.length() == 0) return;
                try { int value = Integer.parseInt(s.toString()); if (value != service.goldReserve()) service.setGoldReserve(value); }
                catch (NumberFormatException ignored) {}
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });
        TextView close = text("关闭悬浮窗", 12, Color.rgb(237, 177, 171));
        close.setGravity(Gravity.CENTER);
        close.setBackground(background(Color.rgb(54, 40, 48), 8, Color.rgb(91, 59, 68)));
        close.setContentDescription("停止自动操作并关闭悬浮窗");
        close.setOnClickListener(v -> service.hideOverlay());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(36));
        closeParams.topMargin = dp(6);
        panel.addView(close, closeParams);
        assistSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) return;
            if (checked) { service.startScanning(); if(service.isImporting())collapse(); } else service.stopScanning("已手动暂停");
        });
        refreshSwitch.setOnCheckedChangeListener((button, checked) -> { if (!syncing) service.setAutoRefresh(checked); });
        params = new WindowManager.LayoutParams(dp(44), dp(44), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT);
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = dp(32); params.y = (int) (bounds().height() * .56);
        bubbleX = params.x; bubbleY = params.y;
        root.addView(bubble, new FrameLayout.LayoutParams(-1, -1));
        update("", false);
    }
    private void stepReserve(boolean up) {
        reserveInput.clearFocus();
        reserveInput.setText(String.valueOf(GoldReserve.step(service.goldReserve(), up)));
        hideKeyboard();
    }
    private void finishReserveInput() {
        if (reserveInput.getText().toString().trim().isEmpty()) {
            syncing = true; reserveInput.setText(String.valueOf(service.goldReserve())); syncing = false;
        }
    }
    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (ime != null) ime.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }
    private int dp(int value) { return (int) (value * service.getResources().getDisplayMetrics().density + .5f); }
    private Rect bounds() { return windows.getCurrentWindowMetrics().getBounds(); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(service); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable background(int color, int radius, int border) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), border); return shape;
    }
    private Switch makeSwitch(String title) {
        Switch toggle = new Switch(service); toggle.setText(title); toggle.setTextSize(13); toggle.setTextColor(Color.WHITE);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{Color.rgb(86, 221, 190), Color.rgb(166, 177, 194)}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{Color.rgb(34, 119, 106), Color.rgb(62, 75, 93)}));
        toggle.setSwitchPadding(dp(12)); return toggle;
    }
    private boolean drag(View view, MotionEvent event, boolean icon) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX(); downY = event.getRawY(); originX = params.x; originY = params.y; moved = false; return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                if (Math.abs(dx) + Math.abs(dy) > ViewConfiguration.get(service).getScaledTouchSlop()) moved = true;
                if (moved) {
                    params.x = originX + (int) dx; params.y = originY + (int) dy;
                    clamp(); bubbleX = params.x; bubbleY = params.y;
                    if (attached) windows.updateViewLayout(root, params);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved && icon) view.performClick(); return true;
            case MotionEvent.ACTION_CANCEL: moved = false; return true;
            default: return false;
        }
    }
    private void expand() {
        if (!attached || expanded) return;
        expanded = true; bubbleX = params.x; bubbleY = params.y;
        service.beginDeckSelection();
        root.removeAllViews(); root.addView(panel, new FrameLayout.LayoutParams(-1, -2)); panel.requestFocus();
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.width = dp(248); params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        panel.measure(View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        clamp(); windows.updateViewLayout(root, params);
    }
    private void collapse() {
        if (!expanded) return;
        finishReserveInput(); hideKeyboard(); reserveInput.clearFocus();
        expanded = false;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        if (deckMenu != null) { PopupMenu menu = deckMenu; deckMenu = null; menu.dismiss(); }
        root.removeAllViews(); root.addView(bubble, new FrameLayout.LayoutParams(-1, -1));
        params.width = dp(44); params.height = dp(44); params.x = bubbleX; params.y = bubbleY;
        clamp();
        if (attached) windows.updateViewLayout(root, params);
        service.endDeckSelection();
    }
    private void clamp() {
        Rect screen = bounds();
        int height = expanded ? Math.max(panel.getMeasuredHeight(), dp(196)) : dp(44);
        params.x = Math.max(0, Math.min(params.x, Math.max(0, screen.width() - params.width)));
        params.y = Math.max(0, Math.min(params.y, Math.max(0, screen.height() - height)));
    }
    void show() { if (!attached) { clamp(); windows.addView(root, params); attached = true; } }
    void remove() {
        finishReserveInput(); hideKeyboard();
        if (deckMenu != null) { PopupMenu menu = deckMenu; deckMenu = null; menu.dismiss(); }
        if (attached) { windows.removeView(root); attached = false; }
        expanded = false; service.endDeckSelection();
    }
    void onRotation() {
        if (expanded) collapse();
        params.y = (int) (bounds().height() * .56); bubbleY = params.y;
        clamp(); if (attached) windows.updateViewLayout(root, params);
    }
    void update(String message, boolean running) {
        this.running = running; syncing = true;
        assistSwitch.setChecked(running);
        refreshSwitch.setChecked(service.getSharedPreferences("probe", Context.MODE_PRIVATE).getBoolean("autoRefresh", false));
        if (!reserveInput.hasFocus()) reserveInput.setText(String.valueOf(service.goldReserve()));
        syncing = false;
        deckLabel.setText(service.currentDeckName() + "  ▾");
        bubble.setContentDescription((running ? "辅助已开启" : "辅助已暂停") + "，当前卡组 " + service.currentDeckName() + "，点击展开");
        bubble.invalidate();
    }
    private void chooseDeck() {
        try {
            java.util.List<DeckStore.Deck> decks = new DeckStore(service).all();
            String active = new DeckStore(service).current().id;
            PopupMenu menu = new PopupMenu(service, deckLabel); deckMenu = menu;
            final boolean[] chosen = {false};
            for (int i = 0; i < decks.size(); i++) menu.getMenu().add(0, i, i, decks.get(i).name)
                .setCheckable(true).setChecked(decks.get(i).id.equals(active));
            menu.setOnMenuItemClickListener(item -> { chosen[0] = true; DeckStore.Deck selected=decks.get(item.getItemId());service.selectDeckFromOverlay(selected.id);if(selected.isCode())collapse();return true; });
            menu.setOnDismissListener(m -> { deckMenu = null; if (!chosen[0]) collapse(); });
            menu.show();
        } catch (RuntimeException e) {
            deckMenu = null; Toast.makeText(service, "无法打开卡组：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    void dockBelowShop() {
        if (!attached || expanded) return;
        params.x = (int) (bounds().width() * .22); params.y = (int) (bounds().height() * .55);
        clamp(); bubbleX = params.x; bubbleY = params.y; windows.updateViewLayout(root, params);
    }
    boolean blocksPoint(float x, float y) {
        if (!attached) return false;
        int[] xy = new int[2]; root.getLocationOnScreen(xy);
        return x >= xy[0] && x < xy[0] + root.getWidth() && y >= xy[1] && y < xy[1] + root.getHeight();
    }
    boolean overlapsShop() {
        if (!attached) return false;
        int[] xy = new int[2]; root.getLocationOnScreen(xy); Rect screen = bounds();
        Rect shop = new Rect((int) (screen.width() * .17), (int) (screen.height() * .035),
            (int) (screen.width() * .98), (int) (screen.height() * .48));
        return shop.intersects(xy[0], xy[1], xy[0] + root.getWidth(), xy[1] + root.getHeight());
    }
    private final class BubbleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap gameIcon;
        BubbleView(Context context) {
            super(context); setClickable(true);
            gameIcon = BitmapFactory.decodeResource(getResources(), R.drawable.jcc_icon);
            paint.setFilterBitmap(true);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(cx, cy) - dp(1);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(178, 19, 29, 42)); canvas.drawCircle(cx, cy, radius, paint);
            if (gameIcon != null) {
                int saved = canvas.save();
                Path clip = new Path(); clip.addCircle(cx, cy, radius, Path.Direction.CW); canvas.clipPath(clip);
                paint.setAlpha(190);
                canvas.drawBitmap(gameIcon, null, new RectF(cx - radius, cy - radius, cx + radius, cy + radius), paint);
                canvas.restoreToCount(saved); paint.setAlpha(255);
            }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            paint.setColor(running ? Color.argb(225, 83, 229, 187) : Color.argb(170, 157, 175, 195));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(running ? Color.rgb(56, 191, 148) : Color.rgb(99, 114, 136));
            canvas.drawRoundRect(cx - dp(10), getHeight() - dp(14), cx + dp(10), getHeight() - dp(1), dp(6), dp(6), paint);
            paint.setColor(Color.WHITE); paint.setTextSize(dp(9));
            canvas.drawText(service.isImporting() ? "导" : running ? "开" : "停", cx, getHeight() - dp(4), paint);
        }
    }
}
