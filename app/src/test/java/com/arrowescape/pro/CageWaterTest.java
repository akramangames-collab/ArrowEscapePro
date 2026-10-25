package com.arrowescape.pro;
import org.junit.Test;
import static org.junit.Assert.*;
public class CageWaterTest {
 @Test public void thresholdsFullAndRevive(){CageWater w=new CageWater();w.reset(1,3);assertEquals(120,w.remainingSeconds());w.advance(42);assertEquals(1,w.danger());w.advance(42);assertEquals(2,w.danger());w.advance(36);assertTrue(w.full());w.revive();assertEquals(.5f,w.fraction(),.001f);}
 @Test public void invalidInputAndSavedTime(){CageWater w=new CageWater();w.reset(1000,100);w.advance(25);w.advance(Float.NaN);w.advance(-10);assertEquals(25,w.elapsed(),0);w.reset(1000,100);w.restore(25);assertEquals(25,w.elapsed(),0);w.restore(Float.NaN);assertEquals(0,w.elapsed(),0);w.restore(10000);assertTrue(w.full());}
}
