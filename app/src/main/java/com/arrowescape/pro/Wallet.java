package com.arrowescape.pro;

/** One wallet shared by gameplay, daily rewards, daily challenges and earned-ad callbacks. */
public final class Wallet {
    public static final int STARTER = 100, HINT_COST = 25, DAILY = 100, AD_REWARD = 75;
    public static final int HEART_COST = 40, CONTINUE_COST = 60;
    public static final long DAY_MS = 86400000L;
    public static final class State {
        public int coins = STARTER, streak;
        public long dailyDay = -1, challengeDay = -1;
        public String levelToken = "", adToken = "", treasureClaims = "";
        public boolean initialized;
        public State copy() {
            State s = new State();
            s.coins = coins; s.streak = streak; s.dailyDay = dailyDay; s.challengeDay = challengeDay;
            s.levelToken = levelToken; s.adToken = adToken; s.treasureClaims = treasureClaims; s.initialized = initialized;
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
    public synchronized boolean canRewardDailyChallenge(long day) { state = storage.load().copy(); return day >= 0 && day > state.challengeDay; }
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
    public synchronized int rewardLevel(String token, int level, int stars) {
        state = storage.load().copy();
        if (token == null || token.isEmpty() || token.equals(state.levelToken) || level < 1 || level > 200 || stars < 1 || stars > 3) return 0;
        boolean milestone = level % 5 == 0;
        boolean boss = level == 25 || level == 50 || level == 100 || level == 150 || level == 200;
        int amount = 15 + (stars == 3 ? 10 : stars == 2 ? 5 : 0) + (milestone ? 75 : 0) + (boss ? 75 : 0);
        State next = state.copy(); next.coins = add(next.coins, amount); next.levelToken = token;
        return commit(next) ? amount : 0;
    }
    public synchronized int rewardLevel(String token, int level, boolean perfect) {
        return rewardLevel(token, level, perfect ? 3 : 1);
    }
    public synchronized int rewardDailyChallenge(long day, int stars) {
        state = storage.load().copy();
        if (day < 0 || day <= state.challengeDay || stars < 1 || stars > 3) return 0;
        int amount = 75 + stars * 25;
        State next = state.copy(); next.coins = add(next.coins, amount); next.challengeDay = day;
        return commit(next) ? amount : 0;
    }
    public synchronized int rewardTreasure(int level) {
        state = storage.load().copy();
        if (level < 1 || level > 200 || level % 5 != 0) return 0;
        String marker = "," + level + ",";
        String claims = state.treasureClaims == null ? "" : state.treasureClaims;
        if (("," + claims + ",").contains(marker)) return 0;
        boolean boss = level == 25 || level == 50 || level == 100 || level == 150 || level == 200;
        int amount = boss ? 50 : 25;
        State next = state.copy();
        next.coins = add(next.coins, amount);
        next.treasureClaims = claims.isEmpty() ? String.valueOf(level) : claims + "," + level;
        return commit(next) ? amount : 0;
    }

    public synchronized int rewardAd(String token) {
        state = storage.load().copy();
        if (token == null || token.isEmpty() || token.equals(state.adToken)) return 0;
        State next = state.copy(); next.coins = add(next.coins, AD_REWARD); next.adToken = token;
        return commit(next) ? AD_REWARD : 0;
    }
    private static int add(int balance, int amount) { return (int)Math.min(Integer.MAX_VALUE, (long)balance + amount); }
}
