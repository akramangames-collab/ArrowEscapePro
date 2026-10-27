package com.arrowescape.pro;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=ArrowApplication.class)
public class FireTankRuntimeTest {
 private static Object get(Object o,String k)throws Exception{Field f=o.getClass().getDeclaredField(k);f.setAccessible(true);return f.get(o);}
 private static void set(Object o,String k,Object v)throws Exception{Field f=o.getClass().getDeclaredField(k);f.setAccessible(true);f.set(o,v);}
 private static Object invoke(Object o,String k,Class<?>[] types,Object...args)throws Exception{Method m=o.getClass().getDeclaredMethod(k,types);m.setAccessible(true);return m.invoke(o,args);}
 @Test public void tankKeysFollowLegalPathsStopFireAndSurviveRestart()throws Exception{
  for(int level:new int[]{122,130,140}){
   try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
    MainActivity activity=controller.get();ArrowGameView game=(ArrowGameView)get(activity,"game");
    invoke(game,"startLevel",new Class[]{int.class},level);
    int key=(int)get(game,"waterTankKeyId");assertTrue("tank key exists at "+level,key>=0);
    CageRescue cage=(CageRescue)get(game,"cageRescue");assertNotEquals(cage.arrowId(),key);
    @SuppressWarnings("unchecked") ArrayList<Object> pieces=(ArrayList<Object>)get(game,"pieces");
    Class<?> piece=pieces.get(0).getClass();Object keyPiece=null;
    for(Object p:pieces)if((int)get(p,"id")==key)keyPiece=p;assertNotNull(keyPiece);
    @SuppressWarnings("unchecked") ArrayList<Object> targets=(ArrayList<Object>)get(game,"boosterTargets");
    targets.clear();targets.add(keyPiece);set(game,"boosterTargeted",true);set(game,"boosterMode",1);
    invoke(game,"confirmBooster",new Class[]{});assertFalse("tank key must resist removal", (boolean)get(keyPiece,"removed"));
    assertTrue("protected target is removed from selection",targets.isEmpty());
    set(game,"boosterMode",0);
    CageWater timer=(CageWater)get(game,"cageWater");timer.restore(5f);
    float before=timer.elapsed();invoke(game,"updateWater",new Class[]{float.class},1f);assertTrue(timer.elapsed()>before);
    int steps=0;
    while(!(boolean)get(keyPiece,"removed")){
     Object next=null;
     for(Object p:pieces)if(!(boolean)get(p,"removed")&&(boolean)invoke(game,"isClear",new Class[]{piece},p)){next=p;break;}
     assertNotNull("legal move must exist",next);
     assertNotEquals("golden cage key must be last",cage.arrowId(),get(next,"id"));
     set(next,"removed",true);assertTrue(++steps<pieces.size());
    }
    assertEquals(true,invoke(game,"waterTankOpened",new Class[]{}));
    float stopped=timer.elapsed();invoke(game,"updateWater",new Class[]{float.class},3f);assertEquals(stopped,timer.elapsed(),0f);
    game.saveProgress();
    try(var resumed=Robolectric.buildActivity(MainActivity.class).setup()){
     ArrowGameView restored=(ArrowGameView)get(resumed.get(),"game");
     assertEquals(level,get(restored,"level"));
     assertEquals(key,get(restored,"waterTankKeyId"));
     assertEquals(true,invoke(restored,"waterTankOpened",new Class[]{}));
    }
   }
  }
 }
}
