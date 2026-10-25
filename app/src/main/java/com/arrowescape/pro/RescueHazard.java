package com.arrowescape.pro;
/** Chapter-stable timer environments. Collision rules and golden key never change. */
public enum RescueHazard {
 WATER("WATER","Rising water","Clear the arrows before water fills the cage.",0xFF14A3EB,0xFF88E6FF),
 FIRE("HEAT","Fire rescue","Clear arrows before the heat meter fills. Flames never block taps.",0xFFEE7031,0xFFFFD58A),
 SAND("SAND","Rising sand","Clear arrows before sand fills the cage. The board stays visible.",0xFFD5A64D,0xFFFFE3A6);
 public final String meter,title,tutorial;public final int color,highlight;
 RescueHazard(String m,String t,String help,int c,int h){meter=m;title=t;tutorial=help;color=c;highlight=h;}
 public static RescueHazard forLevel(int level){int ch=CampaignRules.chapter(level);return ch<=3?WATER:ch<=6?FIRE:ch<=9?SAND:values()[(ch-10)%3];}
}
