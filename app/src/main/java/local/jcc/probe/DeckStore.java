package local.jcc.probe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Named, ordered target lists stored atomically with the selected deck. */
final class DeckStore {
    static final class Deck {
        final String id, name, targets;
        Deck(String id, String name, String targets) { this.id = id; this.name = name; this.targets = targets; }
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
                decks.add(new Deck(item.getString("id"), item.getString("name"), item.getString("targets")));
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
        if (name.isEmpty() || name.length() > 24) throw new IllegalArgumentException("卡组名需为 1～24 个字符");
        List<String> targets = BuyPolicy.parseTargets(input);
        if (targets.isEmpty()) throw new IllegalArgumentException("卡组至少填写一张目标卡");
        List<Deck> decks = all(); int index = -1;
        for (int i = 0; i < decks.size(); i++) {
            Deck deck = decks.get(i);
            if (deck.id.equals(id)) index = i;
            else if (deck.name.equals(name)) throw new IllegalArgumentException("已有同名卡组，请换个名字或选择该卡组编辑");
        }
        if (index < 0 && decks.size() >= 20) throw new IllegalArgumentException("最多保存 20 套卡组");
        Deck saved = new Deck(index < 0 ? UUID.randomUUID().toString() : id, name, String.join("\n", targets));
        if (index < 0) decks.add(saved); else decks.set(index, saved);
        write(decks, saved.id); return saved;
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
                array.put(new JSONObject().put("id", deck.id).put("name", deck.name).put("targets", deck.targets));
                if (deck.id.equals(selected)) current = deck;
            }
            if (current == null) throw new IllegalArgumentException("当前卡组不存在");
            if (!prefs.edit().putString("decks", array.toString()).putString("activeDeck", selected)
                .putString("targets", current.targets).commit()) throw new IllegalStateException("卡组保存失败");
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
