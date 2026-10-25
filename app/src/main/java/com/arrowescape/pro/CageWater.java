package com.arrowescape.pro;
/** Active-play timer. The view excludes paused, tutorial and menu time. */
final class CageWater {
    private float seconds,elapsed;
    void reset(int level,int arrows){
        // Chapter water accelerates; finales offer 10 percent less time.
        if(level<=5)seconds=120f;
        else{
            int rate=level<=40?210:level<=200?190:level<=600?170:155;
            int bonus=level<=200?9:10;
            int result=(arrows*rate+50)/100+bonus;
            if(level%40==0)result=(result*90+50)/100;
            seconds=result;
        }
        elapsed=0;
    }
    void advance(float dt){if(Float.isFinite(dt)&&dt>0)elapsed=Math.min(seconds,elapsed+dt);}
    void restore(float saved){elapsed=Float.isFinite(saved)?Math.max(0,Math.min(seconds,saved)):0;}
    void revive(){elapsed=Math.min(elapsed,seconds*.5f);}
    float elapsed(){return elapsed;}
    float fraction(){return seconds>0?Math.min(1,elapsed/seconds):0;}
    int remainingSeconds(){return Math.max(0,(int)Math.ceil(seconds-elapsed));}
    boolean full(){return fraction()>=1;}
    int danger(){return fraction()>=.7f?2:fraction()>=.35f?1:0;}
}
