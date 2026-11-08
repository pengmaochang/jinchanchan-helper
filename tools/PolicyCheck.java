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
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        BuyPolicy p = new BuyPolicy(BuyPolicy.parseTargets("可酷伯\n维迦，可酷伯"), 0, true);
        BuyPolicy.Frame f = frame(74, "维迦", "可酷伯", "可酷伯");
        BuyPolicy.Decision d = p.decide(f);
        check(d.kind == BUY && d.slot == 1, "Priority order"); p.sent(d, f);
        check(p.decide(f).kind == WAIT && p.bought == 0, "No duplicate tap or early count");
        f = frame(73, "维迦", "", "可酷伯"); d = p.decide(f);
        check(p.bought == 1 && d.kind == BUY && d.slot == 2, "Confirm then buy second copy"); p.sent(d, f);
        f = frame(72, "维迦"); d = p.decide(f);
        check(p.bought == 2 && d.slot == 0, "Next priority"); p.sent(d, f);
        f = frame(71); d = p.decide(f);
        check(p.bought == 3 && d.kind == REFRESH, "Refresh only when targets absent"); p.sent(d, f);
        f = frame(69, "洛");
        check(p.decide(f).kind == REFRESH && p.refreshed == 1, "Confirm refresh spend");
        f.shopOpen = false;
        check(p.decide(f).kind == STOP, "Close shop stops");
        p = new BuyPolicy(List.of("可酷伯"), 10, true);
        check(p.decide(frame(10, "可酷伯")).kind == STOP, "Gold reserve");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = frame(20); f.readable = false;
        check(p.decide(f).kind == WAIT && p.decide(f).kind == WAIT && p.decide(f).kind == STOP, "Uncertain never refreshes");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = frame(20, "可酷伯"); d = p.decide(f); p.sent(d, f);
        check(p.decide(f).kind == WAIT && p.decide(f).kind == WAIT && p.decide(f).kind == STOP, "Failed purchase stops");
        p = new BuyPolicy(List.of("可酷伯"), 0, true);
        f = frame(20); f.shopOpen = false;
        check(p.decide(f).kind == WAIT, "Closed initial shop waits without tapping");
        p = new BuyPolicy(List.of("可酷伯"), 0, false);
        check(p.decide(frame(20, "洛")).kind == WAIT, "Manual refresh mode never refreshes");
        check(p.decide(frame(18, "可酷伯")).kind == BUY, "Manual refresh mode still buys targets");
        p.setAutoRefresh(true);
        check(p.decide(frame(18, "洛")).kind == REFRESH, "Live toggle enables refresh");
        p.setAutoRefresh(false);
        check(p.decide(frame(18, "洛")).kind == WAIT, "Live toggle disables refresh");
        System.out.println("Policy checks passed.");
    }
}
