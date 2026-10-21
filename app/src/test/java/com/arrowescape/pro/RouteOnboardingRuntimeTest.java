package com.arrowescape.pro;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
@Config(sdk=35,application=ArrowApplication.class,qualifiers="w360dp-h780dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RouteOnboardingRuntimeTest {
 private static Object get(Object o,String k)throws Exception{Field f=o.getClass().getDeclaredField(k);f.setAccessible(true);return f.get(o);}
 private static void set(Object o,String k,Object v)throws Exception{Field f=o.getClass().getDeclaredField(k);f.setAccessible(true);f.set(o,v);}
 private static Object call(Object o,String k,Class<?>[] types,Object... values)throws Exception{Method m=o.getClass().getDeclaredMethod(k,types);m.setAccessible(true);return m.invoke(o,values);}
 private static TextView text(View v,String prefix){if(v instanceof TextView t&&t.getText().toString().startsWith(prefix))return t;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView t=text(g.getChildAt(i),prefix);if(t!=null)return t;}return null;}
 private static View practice(View v){if(v instanceof TutorialPracticeView)return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View t=practice(g.getChildAt(i));if(t!=null)return t;}return null;}
 private static void render(View v,int w,int h,String name)throws Exception{
  v.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));v.layout(0,0,w,h);
  Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File dir=new File("build/approved-ui-previews");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
 }
 private static View dialog(MainActivity a)throws Exception{return ((AlertDialog)get(a,"menu")).getWindow().findViewById(android.R.id.content);}
 private static void idle(){shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));}
 private static void tap(View v,float x,float y){long t=100;v.onTouchEvent(MotionEvent.obtain(t,t,0,x*v.getWidth(),y*v.getHeight(),0));v.onTouchEvent(MotionEvent.obtain(t,t+30,1,x*v.getWidth(),y*v.getHeight(),0));}
 @Test public void firstLaunchPracticeSkipAndNextLaunchRouteAreIsolatedFromProgress()throws Exception{
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();idle();assertEquals(0,get(a,"onboardingStep"));
   Wallet wallet=(Wallet)get(a,"wallet");int balance=wallet.balance();ArrowGameView game=(ArrowGameView)get(a,"game");int level=game.currentLevelNumber();
   render(dialog(a),720,1560,"welcome");text(dialog(a),"NEXT").performClick();assertEquals(1,get(a,"onboardingStep"));
   render(dialog(a),720,1560,"tools");text(dialog(a),"TRY THE PRACTICE").performClick();assertEquals(2,get(a,"onboardingStep"));
   render(dialog(a),720,1560,"practice");View p=practice(dialog(a));assertNotNull(p);tap(p,.4f,.4f);assertEquals(2,get(a,"onboardingStep"));
   tap(p,.68f,.3f);tap(p,.4f,.4f);assertEquals(-1,get(a,"onboardingStep"));
   assertEquals(balance,wallet.balance());assertEquals(level,game.currentLevelNumber());assertEquals(0,game.completedLevelCount());
   assertEquals("LEVELS",get(game,"screen").toString());assertEquals(View.VISIBLE,((View)get(a,"routeNav")).getVisibility());
   render((View)get(a,"root"),720,1560,"route-home");
   call(a,"showOnboarding",new Class[]{int.class},0);text(dialog(a),"Skip tutorial").performClick();assertEquals("LEVELS",get(game,"screen").toString());
  }
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){idle();assertEquals(-1,get(c.get(),"onboardingStep"));assertEquals("LEVELS",get(get(c.get(),"game"),"screen").toString());}
 }
 @Test public void allDestinationsReturnToRouteAndStorePreviewDoesNotSpend()throws Exception{
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();idle();call(a,"finishOnboarding",new Class[]{});Wallet wallet=(Wallet)get(a,"wallet");int balance=wallet.balance();
   String[] titles={"DAILY CHALLENGE","STORE","AWARDS","ME"};
   for(int i=1;i<=4;i++){call(a,"openNav",new Class[]{int.class},i);assertNotNull(text(dialog(a),titles[i-1]));render(dialog(a),720,1560,"nav-"+i);call(a,"openNav",new Class[]{int.class},0);assertEquals("LEVELS",get(get(a,"game"),"screen").toString());}
   call(a,"showPremiumStore",new Class[]{});TextView preview=text(dialog(a),"PREVIEW");assertNotNull(preview);preview.performClick();
   AlertDialog popup=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();render(popup.getWindow().getDecorView(),720,1000,"store-preview");popup.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(balance,wallet.balance());
   call(a,"showMenu",new Class[]{boolean.class},false);assertNotNull(text(dialog(a),"Reduce motion"));render(dialog(a),720,1560,"settings");
  }
 }
 @Test public void resultActionsFitPhoneAndLandscapeAndActiveGameHasNoBanner()throws Exception{
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();idle();call(a,"finishOnboarding",new Class[]{});ArrowGameView game=(ArrowGameView)get(a,"game");game.openPlay();
   assertEquals(View.GONE,((View)get(a,"routeNav")).getVisibility());assertFalse((Boolean)get(a,"gameplayBannerRequested"));
   render((View)get(a,"root"),720,1560,"gameplay");set(game,"finished",true);set(game,"earnedStars",2);set(game,"winReward",25);
   for(int[] size:new int[][]{{720,1560},{1280,800}}){render((View)get(a,"root"),size[0],size[1],"win-"+size[0]);for(String key:new String[]{"primaryHit","secondaryHit","tertiaryHit","shareHit"}){RectF hit=(RectF)get(game,key);assertTrue(hit.top>=0);assertTrue(hit.bottom<=game.getHeight());}}
   set(game,"finished",false);set(game,"failed",true);render((View)get(a,"root"),720,1560,"retry");RectF route=(RectF)get(game,"tertiaryHit");tap(game,route.centerX()/game.getWidth(),route.centerY()/game.getHeight());assertEquals("LEVELS",get(game,"screen").toString());
  }
 }
 @Test public void interruptedTutorialResumesItsStepAfterActivityRecreation()throws Exception{
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   idle();call(c.get(),"showOnboarding",new Class[]{int.class},1);c.recreate();idle();assertEquals(1,get(c.get(),"onboardingStep"));
   text(dialog(c.get()),"Skip tutorial").performClick();assertEquals("LEVELS",get(get(c.get(),"game"),"screen").toString());
  }
 }
 @Test public void routePlayResumesUnfinishedRunAndChapterCardsCover400Levels()throws Exception{
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()){
   MainActivity a=c.get();idle();call(a,"finishOnboarding",new Class[]{});ArrowGameView game=(ArrowGameView)get(a,"game");
   set(game,"hearts",2);game.openLevels();render((View)get(a,"root"),720,1560,"route-resume");RectF play=(RectF)get(game,"mapCurrentHit");tap(game,play.centerX()/game.getWidth(),play.centerY()/game.getHeight());assertEquals(2,get(game,"hearts"));assertEquals("PLAY",get(game,"screen").toString());
   String[][] chapters=game.chapterAwardCards();assertEquals(13,chapters.length);assertTrue(chapters[0][1].contains("1–20"));assertTrue(chapters[12][1].contains("361–400"));
  }
 }

}
