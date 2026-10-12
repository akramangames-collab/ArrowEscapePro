package com.arrowescape.pro;

/** Shared cadence for level badges, rewards and checkpoints. */
public final class CampaignRules {
    private CampaignRules() {}
    public static final int LEVEL_COUNT = 400;
    public static int tier(int level) {
        if (level < 1 || level > LEVEL_COUNT) return 0;
        if (level % 30 == 0) return 3;
        if (level % 12 == 0) return 2;
        if (level % 6 == 0) return 1;
        return 0;
    }
    public static String label(int level) {
        switch (tier(level)) {
            case 3: return "EXTREME HARD";
            case 2: return "SUPER HARD";
            case 1: return "HARD";
            default: return "NORMAL";
        }
    }
    public static int bonus(int level) {
        switch (tier(level)) {
            case 3: return 150;
            case 2: return 75;
            case 1: return 35;
            default: return 0;
        }
    }
}
