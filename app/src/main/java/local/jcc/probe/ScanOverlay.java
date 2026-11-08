package local.jcc.probe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Small, touchable service overlay. Only its handle intercepts drag gestures. */
final class ScanOverlay {
    private final WindowManager windows;
    private final LinearLayout root;
    private final WindowManager.LayoutParams params;
    private final TextView status;
    private final TextView handle;
    private final LinearLayout controls;
    private final Switch refreshToggle;
    private final Button startButton;
    private boolean attached, compact;
    private boolean running;
    private float startX, startY;
    private int originX, originY;

    ScanOverlay(CaptureService service) {
        windows = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(service, 5);
        root.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(24, 31, 45));
        background.setCornerRadius(dp(service, 12));
        root.setBackground(background);
        handle = new TextView(service);
        handle.setText("金铲铲助手 · 拖动 / 点此收起");
        handle.setTextSize(11);
        handle.setTextColor(Color.WHITE);
        handle.setPadding(0, 4, 0, 8);
        root.addView(handle);
        status = new TextView(service);
        status.setTextColor(Color.rgb(180, 224, 255));
        status.setTextSize(11);
        status.setText("已暂停 · 开始后按目标购买");
        status.setMaxLines(1);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(status);
        refreshToggle = new Switch(service);
        refreshToggle.setText("自动刷"); refreshToggle.setTextSize(11); refreshToggle.setTextColor(Color.WHITE);
        refreshToggle.setChecked(service.getSharedPreferences("probe", Context.MODE_PRIVATE).getBoolean("autoRefresh", false));
        refreshToggle.setOnCheckedChangeListener((button, checked) -> service.setAutoRefresh(checked));
        controls = new LinearLayout(service);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        startButton = addButton(service, "开启", service::toggleScanning);
        controls.addView(refreshToggle, new LinearLayout.LayoutParams(dp(service, 94), dp(service, 34)));
        Button close = new Button(service);
        close.setText("×"); close.setTextSize(17); close.setMinWidth(0); close.setMinimumWidth(0); close.setPadding(0,0,0,0);
        controls.addView(close, new LinearLayout.LayoutParams(dp(service, 28), dp(service, 34)));
        close.setOnClickListener(v -> service.hideOverlay());
        root.addView(controls);
        params = new WindowManager.LayoutParams(dp(service, 205), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = dp(service, 130);
        params.y = (int) (windows.getCurrentWindowMetrics().getBounds().height() * .56);
        handle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = event.getRawX(); startY = event.getRawY(); originX = params.x; originY = params.y;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                params.x = originX + (int) (event.getRawX() - startX);
                params.y = originY + (int) (event.getRawY() - startY);
                clamp();
                if (attached) windows.updateViewLayout(root, params);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (Math.abs(event.getRawX() - startX) + Math.abs(event.getRawY() - startY) < dp(service, 8)) {
                    compact = !compact;
                    status.setVisibility(compact ? View.GONE : View.VISIBLE);
                    updateTitle();
                    windows.updateViewLayout(root, params);
                }
                return true;
            }
            return false;
        });
    }
    private static int dp(Context context, int value) { return (int) (value * context.getResources().getDisplayMetrics().density + .5f); }
    private Button addButton(Context context, String title, Runnable action) {
        Button button = new Button(context);
        button.setText(title); button.setTextSize(12); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        controls.addView(button, new LinearLayout.LayoutParams(0, dp(context, 34), 1));
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private void clamp() {
        android.graphics.Rect bounds = windows.getCurrentWindowMetrics().getBounds();
        params.x = Math.max(0, Math.min(params.x, Math.max(0, bounds.width() - params.width)));
        params.y = Math.max(0, Math.min(params.y, Math.max(0, bounds.height() - Math.max(root.getHeight(), 80))));
    }
    void show() { if (!attached) { clamp(); windows.addView(root, params); attached = true; } }
    void onRotation() {
        if (!attached) return;
        params.y = (int) (windows.getCurrentWindowMetrics().getBounds().height() * .56);
        clamp();
        windows.updateViewLayout(root, params);
    }
    void remove() { if (attached) { windows.removeView(root); attached = false; } }
    void update(String message, boolean running) {
        this.running = running;
        status.setText(message);
        startButton.setText(running ? "暂停" : "开启");
        updateTitle();
    }
    private void updateTitle() {
        handle.setText((running ? "● 已开启" : "○ 已暂停") + (compact ? " · 展开" : " · 拖动/收起"));
    }
    void dockBelowShop() {
        if (!attached) return;
        android.graphics.Rect bounds = windows.getCurrentWindowMetrics().getBounds();
        params.x = (int) (bounds.width() * .22);
        params.y = (int) (bounds.height() * .55);
        clamp();
        windows.updateViewLayout(root, params);
    }
    boolean blocksPoint(float x, float y) {
        if (!attached) return false;
        int[] xy = new int[2]; root.getLocationOnScreen(xy);
        return x >= xy[0] && x < xy[0] + root.getWidth() && y >= xy[1] && y < xy[1] + root.getHeight();
    }
    boolean overlapsShop() {
        if (!attached) return false;
        int[] xy = new int[2]; root.getLocationOnScreen(xy);
        android.graphics.Rect bounds = windows.getCurrentWindowMetrics().getBounds();
        android.graphics.Rect shop = new android.graphics.Rect((int) (bounds.width() * .17),
            (int) (bounds.height() * .035), (int) (bounds.width() * .98), (int) (bounds.height() * .48));
        return shop.intersects(xy[0], xy[1], xy[0] + root.getWidth(), xy[1] + root.getHeight());
    }
}
