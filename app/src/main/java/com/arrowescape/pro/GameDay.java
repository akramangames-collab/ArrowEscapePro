package com.arrowescape.pro;
import java.time.*;
/** Local calendar boundaries, including daylight-saving transitions. */
public final class GameDay {
 private GameDay() {}
 public static long today(){return day(System.currentTimeMillis(),ZoneId.systemDefault());}
 public static long day(long time,ZoneId zone){return Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toEpochDay();}
 public static long nextMidnight(long time,ZoneId zone){return Instant.ofEpochMilli(time).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();}
}
