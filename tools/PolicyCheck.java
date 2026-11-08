import local.jcc.probe.BuyPolicy;
import static local.jcc.probe.BuyPolicy.Kind.*;
import java.util.*;

public class PolicyCheck {
    static BuyPolicy.Frame frame(int gold, String... names) {
        BuyPolicy.Frame f = new BuyPolicy.Frame();
        f.layoutValid = f.shopOpen = f.readable = true; f.gold = gold;
        for (int i = 0; i < 5; i++) { f.names[i] = i < names.length ? names[i] : ""; f.costs[i] = 1; }
        return f;
    }
    static BuyPolicy.Frame row(int gold, String... names) {
        BuyPolicy.Frame f = frame(gold, names);
        for (int i = names.length; i < 5; i++) f.names[i] = "阿卡丽";
        return f;
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static BuyPolicy.Decision stable(BuyPolicy p, BuyPolicy.Frame f) { p.decide(f); return p.decide(f); }
    public static void main(String[] args) {
        BuyPolicy p = new BuyPolicy(BuyPolicy.parseTargets("可酷伯\n维迦，可酷伯"), 0, true);
        BuyPolicy.Frame f = frame(74, "维迦", "可酷伯", "可酷伯");
        BuyPolicy.Decision d = p.decide(f);
        check(d.kind == BUY && Arrays.equals(d.slots, new int[]{1,2,0}), "Batch priority order"); p.sent(d, f);
        check(p.decide(f).kind == WAIT && p.bought == 0, "No duplicate tap or early count");
        f = frame(73, "维迦", "", "可酷伯"); d = p.decide(f);
        check(p.bought == 1 && d.kind == BUY && Arrays.equals(d.slots, new int[]{2,0}), "Partial batch only retries remaining cards"); p.sent(d, f);
        f = frame(71); d = p.decide(f);
        check(d.kind == WAIT, "Do not refresh immediately on the first empty purchase result");
        d = p.decide(f);
        check(p.bought == 3 && d.kind == REFRESH, "Refresh only when targets absent"); p.sent(d, f);
        check(p.decide(frame(69)).kind == WAIT && p.refreshed == 0, "Never refresh again while new cards are loading");
        f = row(69, "洛");
        check(p.decide(f).kind == WAIT, "First new row is not stable yet");
        check(p.decide(f).kind == REFRESH && p.refreshed == 1, "Confirm the new stable shop, not just the gold spend");
        f.shopOpen = false;
        check(p.decide(f).kind == WAIT, "Closed shop waits without disabling");
        check(p.decide(frame(68, "可酷伯")).kind == BUY, "Reopening shop resumes automatically");
        p = new BuyPolicy(List.of("可酷伯"), 10, true);
        check(p.decide(frame(10, "可酷伯")).kind == WAIT, "Gold reserve waits without disabling");
        check(p.decide(frame(12, "可酷伯")).kind == BUY, "Income resumes purchases");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = frame(20); f.readable = false;
        for (int i = 0; i < 12; i++) check(p.decide(f).kind == WAIT, "Uncertain frame waits without refreshing or disabling");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = frame(20, "可酷伯"); d = p.decide(f); p.sent(d, f);
        check(p.decide(f).kind == WAIT, "Failed tap is not immediately repeated");
        check(p.decide(f).kind == BUY && p.bought == 0, "Unchanged gold permits delayed retry without a false purchase count");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = frame(20); f.shopOpen = false;
        check(p.decide(f).kind == WAIT, "Closed initial shop waits without tapping");
        d = p.decide(frame(20, "可酷伯")); p.sent(d, frame(20, "可酷伯"));
        p.decide(f);
        check(p.decide(frame(20, "可酷伯")).kind == BUY, "Closing cancels a pending operation before reopen");
        p = new BuyPolicy(List.of("可酷伯"), 0, false);
        check(p.decide(frame(20, "洛")).kind == WAIT, "Manual refresh mode never refreshes");
        check(p.decide(frame(18, "可酷伯")).kind == BUY, "Manual refresh mode still buys targets");
        p.setAutoRefresh(true);
        check(stable(p, frame(18, "洛")).kind == REFRESH, "Live toggle enables refresh after stable frames");
        p.setAutoRefresh(false);
        check(p.decide(frame(18, "洛")).kind == WAIT, "Live toggle disables refresh");
        p = new BuyPolicy(List.of("可酷伯", "维迦"), 0, true);
        check(Arrays.equals(p.decide(frame(2, "维迦", "可酷伯", "可酷伯")).slots, new int[]{1,2}), "Batch never exceeds gold budget");
        p = new BuyPolicy(List.of("凯尔"), 1, false);
        f = frame(12, "凯尔", "凯尔", "凯尔"); Arrays.fill(f.costs, 5);
        check(Arrays.equals(p.decide(f).slots, new int[]{0,1}), "Expensive batch respects reserve");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        p.setReserve(50);
        check(p.decide(frame(50, "可酷伯")).kind == WAIT, "Raised live reserve prevents a purchase");
        check(p.decide(frame(51, "可酷伯")).kind == BUY, "Purchase may reach exactly the reserve");
        check(p.decide(frame(51, "洛")).kind == WAIT, "Refresh may not cross the reserve");
        check(p.decide(frame(52, "洛")).kind == REFRESH, "Refresh may reach exactly the reserve");
        p.setReserve(0);
        check(p.decide(frame(1, "可酷伯")).kind == BUY, "Lowered live reserve takes effect immediately");
        boolean invalidReserve = false;
        try { p.setReserve(-1); } catch (IllegalArgumentException expected) { invalidReserve = true; }
        check(invalidReserve, "Reject negative reserve");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = frame(20, "可酷伯"); f.readable = false; f.names[4] = null;
        check(p.decide(f).kind == BUY, "An unreadable unrelated slot does not block a known target");
        f.names[0] = "洛";
        check(p.decide(f).kind == WAIT, "Never refresh over an unreadable slot");
        f.gold = -1; f.names[0] = "可酷伯";
        check(p.decide(f).kind == WAIT, "Missing gold still prevents all spending");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = frame(20, "可酷伯"); p.sent(p.decide(f), f);
        f = frame(21, "可酷伯");
        check(p.decide(f).kind == WAIT, "Income does not immediately replay a pending tap");
        check(p.decide(f).kind == WAIT, "Pending income frame gets a bounded wait");
        check(p.decide(f).kind == BUY && p.bought == 0, "Income cannot deadlock a pending purchase or falsely count it");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = row(20, "洛"); p.sent(stable(p, f), f);
        f = row(19, "洛");
        check(p.decide(f).kind == WAIT, "A refresh with income needs fresh confirmation");
        check(p.decide(f).kind == WAIT, "Wait before replanning an uncertain refresh");
        for (int i = 0; i < 12; i++) check(p.decide(f).kind == WAIT, "Gold mismatch alone cannot authorize a new refresh");
        f = row(19, "维迦");
        check(p.decide(f).kind == WAIT, "Changed row still needs to settle");
        check(p.decide(f).kind == REFRESH && p.refreshed == 1, "A stable new row confirms refresh despite income");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = row(20, "洛"); p.sent(stable(p, f), f);
        for (int i = 0; i < 12; i++) check(p.decide(frame(18)).kind == WAIT, "Never refresh through a persistent blank transition");
        f = row(18, "可酷伯");
        check(p.decide(f).kind == WAIT, "A target emerging during refresh is not clicked on its first transitional frame");
        check(p.decide(f).kind == BUY && p.refreshed == 1, "Buy only after the new target row is stable");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = row(20, "洛"); p.sent(stable(p, f), f);
        for (int i = 0; i < 12; i++) check(p.decide(row(18, "洛")).kind == WAIT, "Gold deducted while old faces remain does not confirm refresh");
        check(p.decide(row(18, "可酷伯")).kind == WAIT, "Transient target does not trigger a stale click");
        check(p.decide(row(18, "维迦")).kind == WAIT, "A changed row resets stability");
        BuyPolicy.Decision safe = p.decide(row(18, "维迦"));
        check(safe.kind == REFRESH && safe.slots.length == 0, "Never replay the transient target slot on the next row");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = row(20, "洛"); p.sent(stable(p, f), f);
        BuyPolicy.Frame hidden = row(18, "洛"); hidden.shopOpen = false;
        check(p.decide(hidden).kind == WAIT, "Refresh button can disappear during transition");
        f = row(18, "可酷伯");
        check(p.decide(f).kind == WAIT, "A transient closed-shop reading cannot bypass the refresh barrier");
        check(p.decide(f).kind == BUY, "Buy after the new visible row settles");
        System.out.println("Policy checks passed.");
    }
}
