package com.arrowescape.pro;
import android.app.Application;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={24,30,35,36},application=ArrowApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StartupRuntimeTest {
 @Test public void earlyGameplayCallbackDoesNotAccessUninitializedGame(){
  try(org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class)){
   c.get().onGameplayStarted();
  }
 }
 @Test public void coldLaunchDoesNotCountAsPlayingButStartingLevelDoes()throws Exception{
  try(org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
   shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
   android.content.SharedPreferences p=c.get().getSharedPreferences("arrow_escape_pro",0);
   assertEquals(-1L,p.getLong("last_played_local_day",-1));
   java.lang.reflect.Field f=MainActivity.class.getDeclaredField("game");f.setAccessible(true);
   ((ArrowGameView)f.get(c.get())).openPlay();
   assertEquals(GameDay.today(),p.getLong("last_played_local_day",-1));
  }
 }
 @Test public void coldLaunchAndDelayedHomeDoNotCrash(){
  try(org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
   shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
   assertFalse(c.get().isFinishing());
  }
 }
}
