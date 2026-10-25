package com.arrowescape.pro;

/** Chapters, level milestones and deterministic timed-rescue pacing. */
public final class CampaignRules {
    private CampaignRules() {}
    public static final int LEVEL_COUNT = 1000;
    public static final int CHAPTER_SIZE = 20;
    public static final int CHAPTER_COUNT = 50;
    private static final String[] WORLDS = {
        "Rescue Harbor","Drowned Passage","Frozen Vault","Clockwork Gate",
        "Storm Corridor","Shadow Basin","Lava Citadel","Deepwater Maze",
        "Lost Kingdom","Final Fortress"
    };
    private static final String[] ACTS = {"Arrival","Crossfire","Deadlock","Torrent","Guardian"};
    public static int chapter(int level) { return Math.max(1,Math.min(CHAPTER_COUNT,(level-1)/CHAPTER_SIZE+1)); }
    public static int chapterStart(int chapter) { return (Math.max(1,Math.min(CHAPTER_COUNT,chapter))-1)*CHAPTER_SIZE+1; }
    public static int chapterEnd(int chapter) { return chapterStart(chapter)+CHAPTER_SIZE-1; }
    public static String chapterName(int level) {
        int index=chapter(level)-1;
        return WORLDS[index/5]+" · "+ACTS[index%5];
    }
    public static int waterSeconds(int level,int arrows) {
        if(level<=5)return 120;
        int rate=level<=40?23:level<=160?20:level<=400?18:level<=700?16:15;
        return Math.max(45,(Math.max(0,arrows)*rate+5)/10+8);
    }
    public static int tier(int level) {
        if(level<1||level>LEVEL_COUNT)return 0;
        if(level%30==0)return 3;
        if(level%12==0)return 2;
        if(level%6==0)return 1;
        return 0;
    }
    public static String label(int level) {
        switch(tier(level)){
            case 3:return "EXTREME HARD";
            case 2:return "SUPER HARD";
            case 1:return "HARD";
            default:return level<=5?"EASY":"HARD";
        }
    }
    public static int bonus(int level) {
        switch(tier(level)){
            case 3:return 150;
            case 2:return 75;
            case 1:return 35;
            default:return 0;
        }
    }
}
