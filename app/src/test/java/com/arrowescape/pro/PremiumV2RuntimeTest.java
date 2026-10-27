package com.arrowescape.pro;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,application=Application.class)
public class PremiumV2RuntimeTest {
 @Test public void dailyChoicesPersistAndNeverDoubleGrant(){
  SharedPreferences p=RuntimeEnvironment.getApplication().getSharedPreferences("v2_choices",Context.MODE_PRIVATE);p.edit().clear().commit();
  long day=GameDay.today();BoosterInventory stock=new BoosterInventory(p);
  assertFalse(stock.claimDailyChoice(day,0,-1));assertFalse(stock.claimDailyChoice(day,5,day));
  assertTrue(stock.claimDailyChoice(day,6,day));assertEquals(1,stock.count(6));
  stock=new BoosterInventory(p);assertTrue(stock.dailyChoiceClaimed(day));assertFalse(stock.claimDailyChoice(day,0,day));assertEquals(0,stock.count(0));
  assertFalse(stock.claimDailyChoice(day+1,0,day+1));
 }
 @Test public void timerEnvironmentsAreStableAndClassicWaterIsPreserved(){
  for(int n=1;n<=120;n++)assertEquals(RescueHazard.WATER,RescueHazard.forLevel(n));
  assertEquals(RescueHazard.FIRE,RescueHazard.forLevel(121));assertEquals(RescueHazard.SAND,RescueHazard.forLevel(241));
  for(int n=1;n<=1000;n++)assertSame(RescueHazard.forLevel(CampaignRules.chapterStart(CampaignRules.chapter(n))),RescueHazard.forLevel(n));
  assertEquals(1200,Heartbeat.interval(0));assertEquals(500,Heartbeat.interval(1));assertEquals(500,Heartbeat.interval(2));
 }
}
