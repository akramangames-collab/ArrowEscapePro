package com.arrowescape.pro;

/** Shared cadence for level badges, rewards and checkpoints. */
public final class CampaignRules {
    private CampaignRules() {}
    public static final int LEVEL_COUNT = 1000;
    public static final int LEVELS_PER_CHAPTER = 40;
    public static final int CHAPTER_COUNT = LEVEL_COUNT / LEVELS_PER_CHAPTER;
    private static final String[] CHAPTER_NAMES = {
        "First Escape", "Key Chamber", "Frozen Tunnels", "Switch Factory", "Linked Labyrinth",
        "Pressure Pipes", "Flooded Vault", "Twisted Rails", "Iron Lockdown", "Deepwater Maze",
        "Chain Reactor", "Turbine Tower", "Storm Drain", "Crystal Prison", "Inferno Gates",
        "Abyssal Lab", "Clockwork Depths", "Serpent Circuit", "Titan Floodgate", "Drowned City",
        "Impossible Junction", "Nightmare Engine", "Master Lock", "Final Countdown", "The Last Key"
    };
    public static int chapter(int level) {
        return Math.max(1,Math.min(CHAPTER_COUNT,(Math.max(1,level)-1)/LEVELS_PER_CHAPTER+1));
    }
    public static int chapterStart(int chapter) {
        return (Math.max(1,Math.min(CHAPTER_COUNT,chapter))-1)*LEVELS_PER_CHAPTER+1;
    }
    public static int chapterEnd(int chapter) {
        return Math.min(LEVEL_COUNT,chapterStart(chapter)+LEVELS_PER_CHAPTER-1);
    }
    public static String chapterName(int level) {
        return CHAPTER_NAMES[chapter(level)-1];
    }
    public static boolean chapterFinale(int level) {
        return level>0&&level<=LEVEL_COUNT&&level%LEVELS_PER_CHAPTER==0;
    }
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
            default: return level<=5?"EASY":"HARD";
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
