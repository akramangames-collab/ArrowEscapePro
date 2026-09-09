package com.arrowescape.pro;

/** One wallet shared by gameplay, daily rewards, daily challenges and earned-ad callbacks. */
public final class Wallet {
    public static final int STARTER = 100, HINT_COST = 25, DAILY = 100, AD_REWARD = 75;
    public static final int HEART_COST = 40, CONTINUE_COST = 60;
    public static final long DAY_MS = 86400000L;
    public static final class State {
        public int coins = STARTER, streak, arrowTypeMask = 1, themeMask = 1;
        public long dailyDay = -1, challengeDay = -1, weeklyChallengeWeek = -1;
        public String levelToken = "", adToken = "";
        public boolean initialized;
        public State copy() {
            State s = new State();
            s.coins = coins; s.streak = streak; s.arrowTypeMask = arrowTypeMask; s.themeMask = themeMask; s.dailyDay = dailyDay; s.challengeDay = challengeDay; s.weeklyChallengeWeek = weeklyChallengeWeek;
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
    public synchronized boolean ownsArrowType(int type) {
        state = storage.load().copy();
        if (type < 0 || type > 4) return false;
        int mask = state.arrowTypeMask | 1;
        return (mask & (1 << type)) != 0;
    }
    public synchronized int arrowTypeMask() {
        state = storage.load().copy();
        return state.arrowTypeMask | 1;
    }
    public synchronized boolean purchaseArrowType(int type, int price) {
        state = storage.load().copy();
        if (type <= 0 || type > 4 || price <= 0) return type == 0;
        int bit = 1 << type;
        if (((state.arrowTypeMask | 1) & bit) != 0) return true;
        if (state.coins < price) return false;
        State next = state.copy();
        next.coins -= price;
        next.arrowTypeMask = (state.arrowTypeMask | 1) | bit;
        return commit(next);
    }
    public synchronized boolean ownsTheme(int theme) {
        state = storage.load().copy();
        if (theme < 0 || theme > 5) return false;
        return (((state.themeMask | 1) & (1 << theme)) != 0);
    }
    public synchronized boolean purchaseTheme(int theme, int price) {
        state = storage.load().copy();
        if (theme <= 0 || theme > 5 || price <= 0) return theme == 0;
        int bit = 1 << theme;
        if (((state.themeMask | 1) & bit) != 0) return true;
        if (state.coins < price) return false;
        State next = state.copy();
        next.coins -= price;
        next.themeMask = (state.themeMask | 1) | bit;
        return commit(next);
    }

    public synchronized boolean canClaimDaily(long now) { state = storage.load().copy(); return now >= 0 && now / DAY_MS > state.dailyDay; }
    public synchronized boolean canRewardDailyChallenge(long day) { state = storage.load().copy(); return day >= 0 && day > state.challengeDay; }
    public synchronized boolean canRewardWeeklyChallenge(long week) { state = storage.load().copy(); return week >= 0 && week > state.weeklyChallengeWeek; }
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
    public synchronized int rewardWeeklyChallenge(long week, int stars) {
        state = storage.load().copy();
        if (week < 0 || week <= state.weeklyChallengeWeek || stars < 1 || stars > 3) return 0;
        int amount = 200 + stars * 50;
        State next = state.copy();
        next.coins = add(next.coins, amount);
        next.weeklyChallengeWeek = week;
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
