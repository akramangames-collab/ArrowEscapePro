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
 @Test public void coldLaunchAndDelayedHomeDoNotCrash(){
  try(org.robolectric.android.controller.ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
   shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
   assertFalse(c.get().isFinishing());
  }
 }
}
