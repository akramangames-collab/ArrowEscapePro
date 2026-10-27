package com.arrowescape.pro;
import android.app.Application;import android.content.Context;import android.graphics.*;import android.view.*;
import java.time.Duration;import java.util.Map;import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,application=Application.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BoosterDemoRuntimeTest {
 private void tap(BoosterDemoView v,float y){float d=v.getResources().getDisplayMetrics().density;v.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,100*d,y*d,0));}
 private void draw(BoosterDemoView v){v.measure(View.MeasureSpec.makeMeasureSpec(360,1073741824),View.MeasureSpec.makeMeasureSpec(245,1073741824));v.layout(0,0,360,245);Bitmap b=Bitmap.createBitmap(360,245,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));b.recycle();}
 @Test public void allSixDemosLeaveInventoryUntouchedAndProtectGoldenKey(){Context c=RuntimeEnvironment.getApplication();var prefs=c.getSharedPreferences("arrow_puzzle_faithful",0);prefs.edit().clear().putInt("boost_stock_0",4).putInt("boost_stock_6",2).commit();Map<String,?> before=prefs.getAll();
  for(int kind:new int[]{0,1,2,3,4,6}){BoosterDemoView v=new BoosterDemoView(c,kind);draw(v);tap(v,146);assertFalse(v.keyRemoved());v.runDemo();draw(v);assertFalse(v.keyRemoved());assertEquals(before,prefs.getAll());}
 }
 @Test public void tripleDemoEscapesThreeArrowsButNeverAutomaticallyRemovesKey()throws Exception{BoosterDemoView v=new BoosterDemoView(RuntimeEnvironment.getApplication(),6);v.runDemo();shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(1700));draw(v);var f=BoosterDemoView.class.getDeclaredField("removed");f.setAccessible(true);boolean[] removed=(boolean[])f.get(v);assertTrue(removed[0]&&removed[1]&&removed[2]);assertFalse(removed[3]);tap(v,146);assertTrue(v.keyRemoved());}
}
