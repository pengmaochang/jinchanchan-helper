package local.jcc.probe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Named, ordered target lists stored atomically with the selected deck. */
final class DeckStore {
    static final class Deck {
        final String id, name, targets, code, preview, note;
        final boolean verified;
        Deck(String id, String name, String targets) { this(id, name, targets, "", "", true, ""); }
        Deck(String id, String name, String targets, String code, String preview, boolean verified, String note) {
            this.id=id; this.name=name; this.targets=targets; this.code=code; this.preview=preview; this.verified=verified; this.note=note;
        }
        boolean isCode() { return !code.isEmpty(); }
    }
    private final SharedPreferences prefs;
    DeckStore(Context context) { this(context.getSharedPreferences("probe", Context.MODE_PRIVATE)); }
    DeckStore(SharedPreferences prefs) { this.prefs = prefs; }
    List<Deck> all() {
        String raw = prefs.getString("decks", null);
        if (raw == null) {
            Deck first = new Deck(UUID.randomUUID().toString(), "默认卡组", prefs.getString("targets", "可酷伯"));
            List<Deck> decks = new ArrayList<>(); decks.add(first); write(decks, first.id); return decks;
        }
        try {
            JSONArray array = new JSONArray(raw); List<Deck> decks = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                decks.add(new Deck(item.getString("id"), item.getString("name"), item.optString("targets", ""), item.optString("code", ""), item.optString("preview", ""), item.optBoolean("verified", item.optString("code", "").isEmpty()), item.optString("note", "")));
            }
            if (decks.isEmpty()) throw new IllegalStateException("卡组列表为空");
            return decks;
        } catch (JSONException e) { throw new IllegalStateException("卡组数据无法读取", e); }
    }
    Deck current() {
        List<Deck> decks = all(); String selected = prefs.getString("activeDeck", "");
        for (Deck deck : decks) if (deck.id.equals(selected)) return deck;
        return decks.get(0);
    }
    Deck save(String id, String name, String input) {
        name = name.trim();
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("阵容名需为 1～80 个字符");
        List<String> targets = BuyPolicy.parseTargets(input);
        Deck existing = null; for (Deck d : all()) if (d.id.equals(id)) existing = d;
        if (targets.isEmpty() && (existing == null || !existing.isCode())) throw new IllegalArgumentException("卡组至少填写一张目标卡");
        List<Deck> decks = all(); int index = -1;
        for (int i = 0; i < decks.size(); i++) {
            Deck deck = decks.get(i);
            if (deck.id.equals(id)) index = i;
            else if (deck.name.equals(name)) throw new IllegalArgumentException("已有同名卡组，请换个名字或选择该卡组编辑");
        }
        if (index < 0 && decks.size() >= 200) throw new IllegalArgumentException("最多保存 200 套阵容");
        Deck saved = new Deck(index < 0 ? UUID.randomUUID().toString() : id, name, String.join("\n", targets), existing == null ? "" : existing.code, existing == null ? "" : existing.preview, existing == null || !existing.isCode() || existing.verified, existing == null ? "" : existing.note);
        if (index < 0) decks.add(saved); else decks.set(index, saved);
        write(decks, saved.id); return saved;
    }
    List<Deck> addCodes(String input) {
        List<LineupCode> codes = LineupCode.parseAll(input);
        List<Deck> decks = all(), saved = new ArrayList<>();
        for (LineupCode code : codes) {
            int index = -1;
            for (int i=0; i<decks.size(); i++) if (decks.get(i).isCode() &&
                (LineupCode.parse(decks.get(i).code).payload.equals(code.payload) || decks.get(i).name.equals(code.name))) { index=i; break; }
            if (index < 0 && decks.size() >= 200) throw new IllegalArgumentException("最多保存 200 套阵容");
            Deck old = index < 0 ? null : decks.get(index);
            boolean same = old != null && LineupCode.parse(old.code).payload.equals(code.payload);
            Deck deck = new Deck(old == null ? UUID.randomUUID().toString() : old.id, code.name,
                same ? old.targets : "", code.text, same ? old.preview : "", same && old.verified, same ? old.note : "首次导入后识别英雄");
            if (index < 0) decks.add(deck); else decks.set(index, deck); saved.add(deck);
        }
        write(decks, saved.get(saved.size()-1).id); return saved;
    }
    Deck saveDerived(String id, String targets, String preview, boolean verified, String note) {
        List<Deck> decks = all();
        for (int i=0; i<decks.size(); i++) {
            Deck old = decks.get(i);
            if (!old.id.equals(id)) continue;
            Deck saved = new Deck(old.id, old.name, String.join("\n", BuyPolicy.parseTargets(targets)), old.code, preview, verified, note);
            decks.set(i, saved); write(decks, current().id); return saved;
        }
        throw new IllegalArgumentException("阵容已删除");
    }
    Deck select(String id) {
        List<Deck> decks = all();
        for (Deck deck : decks) if (deck.id.equals(id)) { write(decks, id); return deck; }
        throw new IllegalArgumentException("卡组不存在");
    }
    Deck delete(String id) {
        List<Deck> decks = all();
        if (decks.size() == 1) throw new IllegalArgumentException("至少保留一套卡组");
        String selected = current().id;
        if (!decks.removeIf(deck -> deck.id.equals(id))) throw new IllegalArgumentException("卡组不存在");
        if (selected.equals(id)) selected = decks.get(0).id;
        write(decks, selected); return current();
    }
    private void write(List<Deck> decks, String selected) {
        try {
            JSONArray array = new JSONArray(); Deck current = null;
            for (Deck deck : decks) {
                array.put(new JSONObject().put("id", deck.id).put("name", deck.name).put("targets", deck.targets).put("code", deck.code).put("preview", deck.preview).put("verified", deck.verified).put("note", deck.note));
                if (deck.id.equals(selected)) current = deck;
            }
            if (current == null) throw new IllegalArgumentException("当前卡组不存在");
            if (!prefs.edit().putString("decks", array.toString()).putString("activeDeck", selected)
                .putString("targets", current.targets).commit()) throw new IllegalStateException("卡组保存失败");
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
