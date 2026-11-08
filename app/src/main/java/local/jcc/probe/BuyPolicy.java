package local.jcc.probe;

import java.util.*;

/** Pure state machine. A decision never counts a purchase until the next frame confirms it. */
public final class BuyPolicy {
    public enum Kind { BUY, REFRESH, WAIT, STOP }
    public static final class Frame {
        public boolean layoutValid, shopOpen, readable;
        public int gold = -1;
        public final String[] names = new String[5];
        public final int[] costs = new int[5];
        public String problem = "";
        public String summary() { return Arrays.toString(names) + " · 金币 " + gold; }
    }
    public static final class Decision {
        public final Kind kind;
        public final int slot;
        public final String message;
        Decision(Kind kind, int slot, String message) { this.kind = kind; this.slot = slot; this.message = message; }
    }
    private final List<String> targets;
    private final int reserve;
    private boolean autoRefresh;
    private Decision pending;
    private int previousGold, pendingCost, misses;
    private String[] previousNames;
    public int bought, refreshed;

    public BuyPolicy(List<String> targets, int reserve, boolean autoRefresh) {
        if (targets.isEmpty() || reserve < 0) throw new IllegalArgumentException();
        this.targets = new ArrayList<>(targets); this.reserve = reserve; this.autoRefresh = autoRefresh;
    }
    public Decision decide(Frame frame) {
        if (!frame.layoutValid) return decision(Kind.WAIT, -1, "等待匹配的横屏画面");
        if (!frame.shopOpen) { shopClosed(); return decision(Kind.WAIT, -1, "已开启 · 等待打开商店"); }
        if (!frame.readable || frame.gold < 0)
            return decision(Kind.WAIT, -1,
                (frame.problem.isEmpty() ? "当前帧暂未读清" : frame.problem) + "，继续识别");
        if (pending != null) {
            if (pending.kind == Kind.REFRESH && java.util.Arrays.stream(frame.names).allMatch(name -> "".equals(name)))
                return decision(Kind.WAIT, -1, "等待刷新动画结束");
            boolean confirmed = pending.kind == Kind.BUY ? "".equals(frame.names[pending.slot]) :
                frame.gold == previousGold - pendingCost || !Arrays.equals(frame.names, previousNames);
            if (confirmed) {
                if (pending.kind == Kind.BUY) bought++; else refreshed++;
                pending = null; misses = 0;
            } else if (++misses >= 8 && frame.gold == previousGold) {
                // A tap that had no effect can be retried after several readable frames.
                pending = null; misses = 0;
            } else return decision(Kind.WAIT, -1, "等待" + (pending.kind == Kind.BUY ? "购买" : "刷新") + "结果");
        }
        for (String target : targets) for (int i = 0; i < 5; i++) if (target.equals(frame.names[i])) {
            int cost = frame.costs[i];
            if (cost < 1 || cost > 10) return decision(Kind.WAIT, -1, "等待读取目标费用");
            if (frame.gold - cost < reserve) return decision(Kind.WAIT, -1, "等待金币达到购买条件");
            return decision(Kind.BUY, i, "购买 " + target + "（第 " + (i + 1) + " 格）");
        }
        if (!autoRefresh) return decision(Kind.WAIT, -1, "当前没有目标，等待你手动刷新");
        if (frame.gold - 2 < reserve) return decision(Kind.WAIT, -1, "等待金币达到刷新条件");
        return decision(Kind.REFRESH, -1, "没有目标，刷新商店");
    }
    public void sent(Decision action, Frame frame) {
        if (action.kind != Kind.BUY && action.kind != Kind.REFRESH) throw new IllegalArgumentException();
        pending = action; previousGold = frame.gold;
        pendingCost = action.kind == Kind.BUY ? frame.costs[action.slot] : 2;
        previousNames = frame.names.clone();
        misses = 0;
    }
    public void shopClosed() { pending = null; previousNames = null; misses = 0; }
    public void setAutoRefresh(boolean enabled) { autoRefresh = enabled; }
    public boolean isAutoRefresh() { return autoRefresh; }
    private static Decision decision(Kind kind, int slot, String message) { return new Decision(kind, slot, message); }
    public static List<String> parseTargets(String input) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String name : input.split("[\\s,，、;；]+")) {
            name = name.trim();
            if (!name.isEmpty()) names.add(name);
        }
        if (names.size() > 20) throw new IllegalArgumentException("最多设置 20 个目标");
        for (String name : names) if (!name.matches("[\\p{IsHan}·]{1,12}"))
            throw new IllegalArgumentException("请输入完整中文卡名：" + name);
        return new ArrayList<>(names);
    }
}
