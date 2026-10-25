package com.arrowescape.pro;

/** The marked arrow opens the box; its key then unlocks the cage. */
final class CageRescue {
    static final float BOX_SECONDS=.35f, KEY_SECONDS=.65f, DOOR_SECONDS=.60f;
    static final float CELEBRATE_SECONDS=.65f;
    private static final float TOTAL=BOX_SECONDS+KEY_SECONDS+DOOR_SECONDS+CELEBRATE_SECONDS;
    private int arrowId=-1;
    private boolean unlocked;
    private float elapsed;

    void reset(int id){arrowId=id;unlocked=false;elapsed=0;}
    int arrowId(){return arrowId;}
    boolean boxOpened(){return unlocked;}
    boolean cageOpened(){return unlocked&&elapsed>=BOX_SECONDS+KEY_SECONDS+DOOR_SECONDS;}
    boolean readyToFinish(){return unlocked&&elapsed>=TOTAL;}
    boolean animating(){return unlocked&&!readyToFinish();}
    void arrowRemoved(int id,boolean reduceMotion){
        if(id!=arrowId||arrowId<0||unlocked)return;
        unlocked=true;elapsed=reduceMotion?TOTAL:0;
    }
    void advance(float dt,boolean reduceMotion){
        if(!unlocked)return;
        elapsed=reduceMotion?TOTAL:Math.min(TOTAL,elapsed+Math.max(0,dt));
    }
    void restore(boolean removed){unlocked=removed;elapsed=removed?TOTAL:0;}
    float lidProgress(){return unlocked?Math.min(1,elapsed/BOX_SECONDS):0;}
    float keyProgress(){return unlocked?Math.max(0,Math.min(1,(elapsed-BOX_SECONDS)/KEY_SECONDS)):0;}
    float doorProgress(){return unlocked?Math.max(0,Math.min(1,(elapsed-BOX_SECONDS-KEY_SECONDS)/DOOR_SECONDS)):0;}
}
