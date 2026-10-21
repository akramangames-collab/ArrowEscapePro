package com.arrowescape.pro;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.File;
import java.io.FileOutputStream;
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
@Config(sdk=35,application=ArrowApplication.class,qualifiers="w360dp-h780dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BannerLayoutRuntimeTest {
 private static Object get(Object o,String k)throws Exception{Field f=o.getClass().getDeclaredField(k);f.setAccessible(true);return f.get(o);}
 private static void set(Object o,String k,Object v)throws Exception{Field f=o.getClass().getDeclaredField(k);f.setAccessible(true);f.set(o,v);}
 private static void call(Object o,String k)throws Exception{Method m=o.getClass().getDeclaredMethod(k);m.setAccessible(true);m.invoke(o);}
 private static void render(View root,int w,int h,String name)throws Exception{
  root.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));root.layout(0,0,w,h);
  Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));
  if(name!=null){File dir=new File("build/game-previews");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
  b.recycle();
 }
 @Test public void actualBannerFootprintNeverOverlapsGameControls()throws Exception{
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();FrameLayout root=(FrameLayout)get(a,"root");ArrowGameView game=(ArrowGameView)get(a,"game");
   set(game,"tutorial",false);game.openPlay();game.setPaused(false);
   WindowInsetsCompat insets=new WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(0,48,0,24)).build();
   ViewCompat.dispatchApplyWindowInsets(root,insets);
   FrameLayout slot=(FrameLayout)get(a,"gameBannerSlot");
   // Simulate a large creative whose actual footprint exceeds the cached hint.
   slot.getLayoutParams().height=-2;slot.setMinimumHeight(100);slot.setVisibility(View.VISIBLE);set(a,"gameBannerHeightPx",100);call(a,"updateGameBottomInset");
   View creative=new View(a);creative.setBackgroundColor(0xFFDFE9F7);slot.addView(creative,new FrameLayout.LayoutParams(-1,200));
   render(root,720,1560,"banner-safe-controls-phone");
   assertEquals(0,get(game,"insetTop"));assertEquals(0,get(game,"insetBottom"));
   for(String name:new String[]{"hintHit","eraseHit","bombHit","rewardHit"}){
    RectF hit=(RectF)get(game,name);assertTrue(name+" overlaps banner",game.getTop()+hit.bottom<=slot.getTop());
   }
   assertTrue("Game viewport overlaps banner",game.getBottom()<=slot.getTop());
   int before=game.getHeight();slot.setVisibility(View.GONE);call(a,"updateGameBottomInset");render(root,720,1560,null);
   assertEquals(before+200,game.getHeight());
   game.openLevels();render(root,720,1560,"polished-trail-phone");
   assertTrue(((RectF)get(game,"mapNextHit")).bottom<=game.getHeight());
   FrameLayout result=(FrameLayout)get(a,"resultBannerSlot");result.getLayoutParams().height=220;result.setVisibility(View.VISIBLE);call(a,"updateGameBottomInset");
   render(root,1280,800,"banner-safe-controls-landscape");assertTrue(game.getBottom()<=result.getTop());
  }
 } @Test public void previousResultBannerCannotReviveDuringNextLevelOrNewResult()throws Exception{
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();Method finish=MainActivity.class.getDeclaredMethod("finishOnboarding");finish.setAccessible(true);finish.invoke(a);
   FrameLayout root=(FrameLayout)get(a,"root");ArrowGameView game=(ArrowGameView)get(a,"game");game.openPlay();
   FrameLayout slot=(FrameLayout)get(a,"resultBannerSlot");View oldAd=new View(a);slot.addView(oldAd,new FrameLayout.LayoutParams(-1,200));slot.setVisibility(View.VISIBLE);
   set(game,"finished",true);
   Method current=MainActivity.class.getDeclaredMethod("bannerRequestCurrent",View.class,FrameLayout.class);current.setAccessible(true);
   assertEquals(true,current.invoke(a,oldAd,slot));
   Method start=ArrowGameView.class.getDeclaredMethod("startLevel",int.class);start.setAccessible(true);start.invoke(game,4);
   assertEquals(View.GONE,slot.getVisibility());assertNull(oldAd.getParent());assertEquals(false,current.invoke(a,oldAd,slot));
   // A delayed result retry must reject gameplay even if a stale SDK view stayed attached.
   slot.addView(oldAd,new FrameLayout.LayoutParams(-1,200));assertEquals(false,current.invoke(a,oldAd,slot));slot.removeAllViews();
   call(a,"showResultBanner");assertEquals(View.GONE,slot.getVisibility());
   render(root,720,1560,"next-level-four-visible-tools");
   for(String name:new String[]{"hintHit","eraseHit","bombHit","rewardHit"}){RectF r=(RectF)get(game,name);assertTrue(r.top>=0);assertTrue(r.bottom<=game.getHeight());}
   // A later win cannot make a callback belonging to the previous banner current.
   set(game,"finished",true);View newAd=new View(a);slot.addView(newAd,new FrameLayout.LayoutParams(-1,200));
   assertEquals(false,current.invoke(a,oldAd,slot));assertEquals(true,current.invoke(a,newAd,slot));
   game.openLevels();assertEquals(View.GONE,slot.getVisibility());assertEquals(false,current.invoke(a,newAd,slot));
  }
 }

}
