package local.jcc.probe;

/** Main-thread gate: one gesture at a time, then capture a frame after rendering can settle. */
final class ShopActionGate {
    private long sequence, notBefore;
    private boolean inFlight;
    private BuyPolicy.Kind kind;
    long delay(long now) { return inFlight ? -1 : Math.max(0, notBefore-now); }
    long begin(BuyPolicy.Kind action, long now) {
        if (delay(now) != 0) throw new IllegalStateException("上一个动作尚未结束");
        kind = action; inFlight = true; return ++sequence;
    }
    boolean finish(long id, long now) {
        if (!inFlight || id != sequence) return false;
        inFlight = false; notBefore = now + (kind == BuyPolicy.Kind.REFRESH ? 350 : 100); return true;
    }
}
