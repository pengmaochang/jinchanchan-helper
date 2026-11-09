package local.jcc.probe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Search the bundled catalog; retain the exact saved order, including legacy names. */
final class CardPicker extends LinearLayout {
    private static final int INK = 0xffedf0f5, MUTED = 0xff9ba8bd, GOLD = 0xffe8c478;
    private final EditText search;
    private final LinearLayout results;
    private final Flow chips;
    private final TextView empty;
    private final List<String> selected = new ArrayList<>();
    private final List<Card> catalog = new ArrayList<>();
    private final Map<String, Bitmap> portraits = new HashMap<>();
    private static final class Card {
        String id, name; int cost;
        Card(JSONObject o) throws JSONException { id = o.getString("id"); name = o.getString("name"); cost = o.getInt("price"); }
    }
    CardPicker(Context context) {
        super(context); setOrientation(VERTICAL);
        try (InputStream in = context.getAssets().open("champions_s18.json")) {
            JSONArray list = new JSONObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("champions");
            for (int i = 0; i < list.length(); i++) catalog.add(new Card(list.getJSONObject(i)));
        } catch (IOException | JSONException e) { throw new IllegalStateException("棋子目录读取失败", e); }
        search = new EditText(context); search.setSingleLine(true); search.setTextSize(14); search.setTextColor(INK);
        search.setHint("搜索棋子名字，例如：黛、娜"); search.setHintTextColor(0xff6d7b90);
        search.setPadding(dp(12), 0, dp(12), 0); search.setBackground(shape(0xff121923, 10));
        search.setContentDescription("搜索棋子");
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        addView(search, new LayoutParams(-1, dp(48)));
        results = new LinearLayout(context); results.setOrientation(VERTICAL); results.setVisibility(GONE);
        results.setBackground(shape(0xff222d3e, 10));
        LayoutParams rp = new LayoutParams(-1, -2); rp.topMargin = dp(6); addView(results, rp);
        TextView hint = text("已选棋子 · 识别到就购买", 11, MUTED);
        LayoutParams hp = new LayoutParams(-1, -2); hp.topMargin = dp(12); hp.bottomMargin = dp(6); addView(hint, hp);
        chips = new Flow(context); addView(chips, new LayoutParams(-1, -2));
        empty = text("还没选卡，搜索名字后点选添加", 12, MUTED); empty.setPadding(dp(4), dp(8), 0, dp(8)); addView(empty);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { showResults(); }
            @Override public void afterTextChanged(Editable e) {}
        });
    }
    String value() { return String.join("\n", selected); }
    void setValue(String value) {
        selected.clear(); selected.addAll(BuyPolicy.parseTargets(value)); search.setText(""); renderChips();
    }
    private void showResults() {
        results.removeAllViews(); String query = search.getText().toString().trim();
        results.setVisibility(query.isEmpty() ? GONE : VISIBLE); if (query.isEmpty()) return;
        int count = 0;
        for (Card card : catalog) {
            if (!card.name.contains(query) || selected.contains(card.name)) continue;
            LinearLayout row = new LinearLayout(getContext()); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), dp(6), dp(10), dp(6));
            addPortrait(row, card, 32);
            TextView name = text(card.name, 14, INK); name.setPadding(dp(10), 0, 0, 0); row.addView(name, new LayoutParams(0, -2, 1));
            row.addView(text(card.cost + " 金币  ＋", 12, GOLD)); row.setContentDescription("添加" + card.name);
            row.setOnClickListener(v -> {
                if (selected.size() >= 20) { Toast.makeText(getContext(), "最多设置 20 个目标", Toast.LENGTH_SHORT).show(); return; }
                if (!selected.contains(card.name)) selected.add(card.name);
                search.setText(""); renderChips();
            });
            results.addView(row, new LayoutParams(-1, dp(48))); if (++count == 5) break;
        }
        if (count == 0) { TextView hint = text("没有可添加的匹配棋子", 12, MUTED); hint.setPadding(dp(12), dp(14), dp(12), dp(14)); results.addView(hint); }
    }
    private void renderChips() {
        chips.removeAllViews(); empty.setVisibility(selected.isEmpty() ? VISIBLE : GONE);
        for (int i = 0; i < selected.size(); i++) {
            String name = selected.get(i); Card card = null;
            for (Card c : catalog) if (c.name.equals(name)) { card = c; break; }
            FrameLayout chip = new FrameLayout(getContext()); chip.setBackground(shape(0xff333346, 10));
            LinearLayout portrait = new LinearLayout(getContext()); portrait.setGravity(Gravity.CENTER);
            if (card != null) addPortrait(portrait, card, 40);
            else { TextView fallback=text(name.substring(0,1),22,GOLD);fallback.setGravity(Gravity.CENTER);portrait.addView(fallback,new LayoutParams(dp(40),dp(40))); }
            FrameLayout.LayoutParams portraitParams=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            portraitParams.topMargin=dp(8);chip.addView(portrait,portraitParams);
            TextView label = text(name, 12, GOLD); label.setGravity(Gravity.CENTER); label.setSingleLine(true);label.setTypeface(null, Typeface.BOLD);
            label.setAutoSizeTextTypeUniformWithConfiguration(8,12,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            label.setPadding(dp(3),0,dp(3),0);label.setIncludeFontPadding(false);
            FrameLayout.LayoutParams labelParams=new FrameLayout.LayoutParams(-1,dp(23),Gravity.BOTTOM);
            labelParams.bottomMargin=dp(3);chip.addView(label,labelParams);
            FrameLayout remove = new FrameLayout(getContext()); remove.setContentDescription("移除" + name);
            remove.setFocusable(true);
            remove.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff), null, shape(0xffffffff, 14)));
            TextView badge = text("×", 16, 0xcce8edf5); badge.setGravity(Gravity.CENTER); badge.setIncludeFontPadding(false);
            badge.setBackground(shape(0x99202a3b, 9)); badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP | Gravity.RIGHT);
            badgeParams.topMargin=dp(3); badgeParams.rightMargin=dp(3); remove.addView(badge, badgeParams);
            remove.setOnClickListener(v -> { selected.remove(name); renderChips(); showResults(); });
            chip.addView(remove, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.RIGHT));
            chips.addView(chip, new ViewGroup.LayoutParams(dp(68), dp(74)));
        }
    }
    private void addPortrait(LinearLayout parent, Card card, int size) {
        Bitmap bitmap = portraits.get(card.id);
        if (bitmap == null) try (InputStream in = getContext().getAssets().open("portraits/" + card.id + ".png")) {
            bitmap = BitmapFactory.decodeStream(in); if (bitmap != null) portraits.put(card.id, bitmap);
        } catch (IOException ignored) {}
        if (bitmap == null) return;
        ImageView image = new ImageView(getContext()); image.setImageBitmap(bitmap); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(shape(0xff121923, 7)); image.setClipToOutline(true); parent.addView(image, new LayoutParams(dp(size), dp(size)));
    }
    private TextView text(String value, int size, int color) { TextView v = new TextView(getContext()); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private final class Flow extends ViewGroup {
        Flow(Context c) { super(c); }
        @Override protected void onMeasure(int ws, int hs) {
            int width = MeasureSpec.getSize(ws), x = 0, y = 0, height = 0, gap = dp(6);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i); child.measure(MeasureSpec.makeMeasureSpec(dp(68), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(74), MeasureSpec.EXACTLY));
                if (x > 0 && x + child.getMeasuredWidth() > width) { x = 0; y += height + gap; height = 0; }
                x += child.getMeasuredWidth() + gap; height = Math.max(height, child.getMeasuredHeight());
            }
            setMeasuredDimension(width, resolveSize(y + height, hs));
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int x = 0, y = 0, height = 0, gap = dp(6), width = r - l;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i); int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                if (x > 0 && x + w > width) { x = 0; y += height + gap; height = 0; }
                child.layout(x, y, x + w, y + h); x += w + gap; height = Math.max(height, h);
            }
        }
    }
}
