package com.arrowescape.pro;

/** One wallet shared by gameplay, daily rewards and earned-ad callbacks. */
public final class Wallet {
    public static final int STARTER = 100, HINT_COST = 25, DAILY = 100, AD_REWARD = 75;
    public static final int HEART_COST = 40, CONTINUE_COST = 60;
    public static final long DAY_MS = 86400000L;
    public static final class State {
        public int coins = STARTER, streak;
        public long dailyDay = -1;
        public String levelToken = "", adToken = "";
        public boolean initialized;
        public State copy() {
            State s = new State();
            s.coins = coins; s.streak = streak; s.dailyDay = dailyDay;
            s.levelToken = levelToken; s.adToken = adToken; s.initialized = initialized;
            return s;
        }
    }
    public interface Storage { State load(); boolean save(State state); }
    private final Storage storage;
    private State state;
    public Wallet(Storage storage) {
        this.storage = storage;
        state = storage.load().copy();
        state.coins = Math.max(0, state.coins);
        if (!state.initialized) {
            State next = state.copy(); next.initialized = true;
            if (!commit(next)) throw new IllegalStateException("Could not save wallet");
        }
    }
    private boolean commit(State next) {
        if (!storage.save(next.copy())) return false;
        state = next; return true;
    }
    public synchronized int balance() { state = storage.load().copy(); return state.coins; }
    public synchronized int streak() { state = storage.load().copy(); return state.streak; }
    public synchronized boolean canClaimDaily(long now) { state = storage.load().copy(); return now >= 0 && now / DAY_MS > state.dailyDay; }
    public synchronized boolean spend(int price) {
        state = storage.load().copy();
        if (price <= 0 || state.coins < price) return false;
        State next = state.copy(); next.coins -= price;
        return commit(next);
    }
    public synchronized int claimDaily(long now) {
        if (!canClaimDaily(now)) return 0;
        long day = now / DAY_MS;
        State next = state.copy();
        next.streak = state.dailyDay == day - 1 ? Math.min(9999, state.streak + 1) : 1;
        next.dailyDay = day;
        next.coins = add(next.coins, DAILY);
        return commit(next) ? DAILY : 0;
    }
    public synchronized int rewardLevel(String token, int level, boolean perfect) {
        state = storage.load().copy();
        if (token == null || token.isEmpty() || token.equals(state.levelToken) || level < 1 || level > 200) return 0;
        int amount = 15 + (perfect ? 5 : 0) + (level % 5 == 0 ? 30 : 0);
        State next = state.copy(); next.coins = add(next.coins, amount); next.levelToken = token;
        return commit(next) ? amount : 0;
    }
    /** Call only from OnUserEarnedRewardListener, with a unique token per shown ad. */
    public synchronized int rewardAd(String token) {
        state = storage.load().copy();
        if (token == null || token.isEmpty() || token.equals(state.adToken)) return 0;
        State next = state.copy(); next.coins = add(next.coins, AD_REWARD); next.adToken = token;
        return commit(next) ? AD_REWARD : 0;
    }
    private static int add(int balance, int amount) { return (int)Math.min(Integer.MAX_VALUE, (long)balance + amount); }
}
