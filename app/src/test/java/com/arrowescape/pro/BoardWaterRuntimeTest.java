package com.arrowescape.pro;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=ArrowApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BoardWaterRuntimeTest {
 private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static void set(Object o,String name,Object v)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
 @Test public void risingWaterAppearsBehindBoardAndRespectsHazard()throws Exception{
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   ArrowGameView game=(ArrowGameView)get(controller.get(),"game");
   set(game,"rescueBoard",true);set(game,"level",1);
   set(game,"boardViewportLeft",0f);set(game,"boardViewportTop",0f);
   set(game,"boardViewportRight",300f);set(game,"boardViewportBottom",300f);
   set(game,"boardLeft",0f);set(game,"boardTop",0f);set(game,"gridW",30);set(game,"gridH",30);
   set(game,"cell",10f);set(game,"boardStretchY",1f);
   CageWater water=(CageWater)get(game,"cageWater");water.reset(1,3);
   Method draw=ArrowGameView.class.getDeclaredMethod("drawBoardWater",Canvas.class,long.class);draw.setAccessible(true);
   Bitmap dry=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888);
   draw.invoke(game,new Canvas(dry),0L);assertEquals(0,Color.alpha(dry.getPixel(150,250)));
   water.restore(60f);Bitmap wet=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888);
   draw.invoke(game,new Canvas(wet),0L);
   assertTrue(Color.alpha(wet.getPixel(150,250))>0);
   assertEquals(0,Color.alpha(wet.getPixel(150,25)));
   set(game,"level",121);Bitmap heat=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888);
   draw.invoke(game,new Canvas(heat),0L);assertEquals(0,Color.alpha(heat.getPixel(150,250)));
   dry.recycle();wet.recycle();heat.recycle();
  }
 }
 @Test public void waterlineReachesHeadAtFailureAndRestores()throws Exception{
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup()){
   ArrowGameView game=(ArrowGameView)get(controller.get(),"game");
   Method start=ArrowGameView.class.getDeclaredMethod("startLevel",int.class);start.setAccessible(true);start.invoke(game,1);
   set(game,"boardViewportBottom",300f);set(game,"boardTop",0f);set(game,"gridH",30);set(game,"cell",10f);set(game,"boardStretchY",1f);
   CageWater water=(CageWater)get(game,"cageWater");water.reset(1,3);
   Method surface=ArrowGameView.class.getDeclaredMethod("waterSurfaceY");surface.setAccessible(true);
   Method update=ArrowGameView.class.getDeclaredMethod("updateWater",float.class);update.setAccessible(true);
   RescueCharacterArt art=(RescueCharacterArt)get(game,"rescueArt");
   float head=150f+22.6f-art.drawnHeight(RescueCharacterArt.STARTLED,53.5f,54f);
   water.restore(119f);assertTrue((float)surface.invoke(game)>head);
   update.invoke(game,.5f);assertFalse((boolean)get(game,"failed"));
   update.invoke(game,.5f);assertTrue((boolean)get(game,"failed"));assertEquals(head,(float)surface.invoke(game),.01f);
   game.saveProgress();
   try(var resumed=Robolectric.buildActivity(MainActivity.class).setup()){
    ArrowGameView restored=(ArrowGameView)get(resumed.get(),"game");assertTrue((boolean)get(restored,"failed"));
    restored.grantRevive();assertFalse((boolean)get(restored,"failed"));
    assertEquals(.5f,((CageWater)get(restored,"cageWater")).fraction(),.001f);
   }
  }
 }

}
