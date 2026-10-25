package com.arrowescape.pro;
import org.junit.Test;
import static org.junit.Assert.*;
public class CageWaterTest {
 @Test public void thresholdsFullAndRevive(){CageWater w=new CageWater();w.reset(1,3);assertEquals(120,w.remainingSeconds());w.advance(42);assertEquals(1,w.danger());w.advance(42);assertEquals(2,w.danger());w.advance(36);assertTrue(w.full());w.revive();assertEquals(.5f,w.fraction(),.001f);}
 @Test public void chapterPressureAndFinalesAreFaster(){
     CageWater w=new CageWater();
     w.reset(6,24);assertEquals(59,w.remainingSeconds());
     w.reset(40,30);assertEquals(65,w.remainingSeconds());
     w.reset(100,32);assertEquals(70,w.remainingSeconds());
     w.reset(1000,101);assertEquals(150,w.remainingSeconds());
     assertEquals(25,CampaignRules.CHAPTER_COUNT);
     assertEquals(25,CampaignRules.chapter(1000));
     assertEquals(961,CampaignRules.chapterStart(25));
     assertEquals(1000,CampaignRules.chapterEnd(25));
     assertEquals("The Last Key",CampaignRules.chapterName(1000));
 }
 @Test public void invalidInputAndSavedTime(){CageWater w=new CageWater();w.reset(1000,100);w.advance(25);w.advance(Float.NaN);w.advance(-10);assertEquals(25,w.elapsed(),0);w.reset(1000,100);w.restore(25);assertEquals(25,w.elapsed(),0);w.restore(Float.NaN);assertEquals(0,w.elapsed(),0);w.restore(10000);assertTrue(w.full());}
}
