package local.jcc.probe;

public class ActionGateCheck {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        ShopActionGate gate = new ShopActionGate();
        long first = gate.begin(BuyPolicy.Kind.REFRESH, 1000);
        check(gate.delay(5000) == -1, "Capture remains blocked until the refresh callback");
        boolean rejected = false;
        try { gate.begin(BuyPolicy.Kind.BUY, 1200); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "A purchase cannot overlap a refresh gesture");
        check(gate.finish(first, 1100), "Refresh completion releases its lock");
        check(gate.delay(1449) == 1 && gate.delay(1450) == 0, "Capture waits 350 ms after refresh completion");
        long second = gate.begin(BuyPolicy.Kind.BUY, 1450);
        check(!gate.finish(first, 1500) && gate.delay(1500) == -1, "An old callback cannot unlock a newer purchase");
        check(gate.finish(second, 1600), "Matching purchase completion");
        check(gate.delay(1699) == 1 && gate.delay(1700) == 0, "Purchase settles before a new capture");
        check(!gate.finish(second, 1900), "Duplicate completion cannot change the next capture deadline");
        System.out.println("Action gate checks passed.");
    }
}
