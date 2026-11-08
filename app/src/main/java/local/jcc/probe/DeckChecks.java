package local.jcc.probe;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Uses an isolated preferences file; never touches real decks or invokes gestures. */
public class DeckChecks extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void onStart() {
        Bundle output = new Bundle();
        SharedPreferences prefs = getTargetContext().getSharedPreferences("deck_checks_only", 0);
        prefs.edit().clear().putString("targets", "艾希\n洛").commit();
        try {
            DeckStore store = new DeckStore(prefs);
            DeckStore.Deck legacy = store.current();
            check(store.all().size() == 1 && legacy.targets.equals("艾希\n洛"), "Legacy migration");
            DeckStore.Deck a = store.save(null, "五费追卡", "瑟提\n凯尔\n瑟提");
            DeckStore.Deck b = store.save(null, "前期", "可酷伯\n维迦");
            check(store.all().size() == 3 && store.current().id.equals(b.id), "Multiple decks");
            store.select(a.id);
            check(new DeckStore(prefs).current().id.equals(a.id), "Selection persisted across instances");
            check(prefs.getString("targets", "").equals("瑟提\n凯尔"), "Ordered deduplication and active targets");
            DeckStore.Deck updated = store.save(a.id, "五费优先", "凯尔\n瑟提");
            check(updated.id.equals(a.id) && store.all().size() == 3, "Edit preserves identity");
            boolean rejected = false;
            try { store.save(null, "五费优先", "洛"); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "Duplicate name rejected");
            rejected = false;
            try { store.save(null, "空卡组", " "); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "Empty targets rejected");
            check(store.delete(a.id).id.equals(legacy.id), "Deleting active deck selects a remaining deck");
            store.delete(b.id);
            rejected = false;
            try { store.delete(legacy.id); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected && store.all().size() == 1, "Last deck is retained");
            output.putString("result", "PASS"); finish(-1, output);
        } catch (Throwable e) { output.putString("error", e.toString()); finish(0, output); }
        finally { prefs.edit().clear().commit(); }
    }
}
