package com.arrowescape.pro;

import android.content.SharedPreferences;

/** Persistent consumables. Level gifts and ad receipts are recorded with the stock. */
public final class BoosterInventory {
    public static final int HINT=0, ERASER=1, BOMB=2, SWEEP=3;
    private final SharedPreferences prefs;
    public BoosterInventory(SharedPreferences prefs){this.prefs=prefs;}
    public static String name(int kind){return new String[]{"Hint","Eraser","Bomb","Sweep"}[kind];}
    public synchronized int count(int kind){check(kind);return Math.max(0,prefs.getInt("boost_stock_"+kind,0));}
    public synchronized boolean consume(int kind){int stock=count(kind);return stock>0&&prefs.edit().putInt("boost_stock_"+kind,stock-1).commit();}
    public synchronized boolean rewardAd(int kind,String receipt){
        check(kind);if(receipt==null||receipt.isEmpty()||prefs.getBoolean("boost_ad_"+receipt,false))return false;
        return prefs.edit().putInt("boost_stock_"+kind,count(kind)+1).putBoolean("boost_ad_"+receipt,true).commit();
    }
    /** Sparse, reproducible gifts: a small gift every fifth clear; two bombs at extreme checkpoints. */
    public static int giftKind(int level){if(level<1||level>400)return -1;if(level%30==0)return BOMB;if(level%5!=0)return -1;return new int[]{HINT,ERASER,BOMB,SWEEP}[(level/5-1)%4];}
    public static int giftAmount(int level){return giftKind(level)<0?0:level%30==0?2:1;}
    public synchronized int rewardLevel(int level){
        int kind=giftKind(level),amount=giftAmount(level);if(kind<0||prefs.getBoolean("boost_level_"+level,false))return 0;
        return prefs.edit().putInt("boost_stock_"+kind,count(kind)+amount).putBoolean("boost_level_"+level,true).commit()?amount:0;
    }
    private static void check(int kind){if(kind<0||kind>3)throw new IllegalArgumentException("Unknown booster");}
}
