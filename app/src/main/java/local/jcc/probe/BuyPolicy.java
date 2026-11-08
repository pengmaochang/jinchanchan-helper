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
    private int previousGold, pendingCost, misses, uncertain;
    private boolean opened;
    public int bought, refreshed;

    public BuyPolicy(List<String> targets, int reserve, boolean autoRefresh) {
        if (targets.isEmpty() || reserve < 0) throw new IllegalArgumentException();
        this.targets = new ArrayList<>(targets); this.reserve = reserve; this.autoRefresh = autoRefresh;
    }
    public Decision decide(Frame frame) {
        if (!frame.layoutValid) return decision(Kind.STOP, -1, "画面尺寸不匹配");
        if (!frame.shopOpen) return decision(opened ? Kind.STOP : Kind.WAIT, -1,
            opened ? "商店已关闭，已停止" : "等待手动展开商店");
        opened = true;
        if (!frame.readable || frame.gold < 0)
            return decision(++uncertain >= 3 ? Kind.STOP : Kind.WAIT, -1, "卡名或金币识别不清，等待重读");
        uncertain = 0;
        if (pending != null) {
            boolean confirmed = frame.gold == previousGold - pendingCost &&
                (pending.kind == Kind.REFRESH || "".equals(frame.names[pending.slot]));
            if (!confirmed) return decision(++misses >= 3 ? Kind.STOP : Kind.WAIT, -1,
                "等待" + (pending.kind == Kind.BUY ? "购买" : "刷新") + "结果确认；不会重复点击");
            if (pending.kind == Kind.BUY) bought++; else refreshed++;
            pending = null; misses = 0;
        }
        for (String target : targets) for (int i = 0; i < 5; i++) if (target.equals(frame.names[i])) {
            int cost = frame.costs[i];
            if (cost < 1 || cost > 10) return decision(Kind.STOP, -1, "目标费用识别不清");
            if (frame.gold - cost < reserve) return decision(Kind.STOP, -1, "金币不足或已达保留底线");
            return decision(Kind.BUY, i, "购买 " + target + "（第 " + (i + 1) + " 格）");
        }
        if (!autoRefresh) return decision(Kind.WAIT, -1, "当前没有目标，等待你手动刷新");
        if (frame.gold - 2 < reserve) return decision(Kind.STOP, -1, "刷新金币不足或已达保留底线");
        return decision(Kind.REFRESH, -1, "没有目标，刷新商店");
    }
    public void sent(Decision action, Frame frame) {
        if (action.kind != Kind.BUY && action.kind != Kind.REFRESH) throw new IllegalArgumentException();
        pending = action; previousGold = frame.gold;
        pendingCost = action.kind == Kind.BUY ? frame.costs[action.slot] : 2;
        misses = 0;
    }
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
