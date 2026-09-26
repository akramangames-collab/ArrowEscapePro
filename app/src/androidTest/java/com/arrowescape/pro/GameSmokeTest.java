package com.arrowescape.pro;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GameSmokeTest {
    private static Object get(Object o,String key) throws Exception { Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o); }
    private static void set(Object o,String key,Object value) throws Exception {Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
    private static Object call(Object o,String name,Class<?>[] types,Object... args) throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    private static class Host implements ArrowGameView.Host {
        public void onLevelCompleted(){} public void requestRewardedErase(){} public void requestRewardedRevive(){} public void requestRewardedCoins(){} public void openWallet(){} public void openSettings(){} public void onContinueAfterWin(Runnable p){p.run();}
    }
    private static void render(Context context,ArrowGameView game,String name) throws Exception {
        int width=1080,height=1920;
        game.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY));game.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.setHasAlpha(false);game.draw(new Canvas(bitmap));
        File directory=new File(context.getExternalFilesDir(null),"screenshots");directory.mkdirs();
        try(FileOutputStream file=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();
    }
    @Test public void snakeMovementPaidHintsAndSavedProgress() throws Exception {
        final Throwable[] error={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
                context.getSharedPreferences("arrow_puzzle_faithful",0).edit().clear().commit();context.getSharedPreferences("arrow_escape_pro",0).edit().clear().commit();
                Wallet wallet=new Wallet(new PreferenceWalletStorage(context));
                ArrowGameView game=new ArrowGameView(context,new Host(),wallet);
                game.setPaused(true);render(context,game,"01-tutorial");set(game,"tutorial",false);render(context,game,"02-level-1");
                ArrayList<?> pieces=(ArrayList<?>)get(game,"pieces");assertEquals("V17 starts with a compact dependency puzzle",24,pieces.size());
                int levelOneCount=pieces.size();
                Object bent=null,safe=null;
                for(Object p:pieces){
                    assertTrue((Boolean)call(game,"hasSelfClearance",new Class[]{p.getClass()},p));
                    if(((ArrayList<?>)get(p,"pts")).size()>2)bent=p;
                    if((Boolean)call(game,"isClear",new Class[]{p.getClass()},p))safe=p;
                }
                assertNotNull(bent);assertNotNull(safe);

                // Synthetic hook: head points right, but reaches its own vertical tail
                // before that tail cell has been vacated. V17 must reject it.
                Class<?> pieceClass=safe.getClass();
                java.lang.reflect.Constructor<?> ctor=pieceClass.getDeclaredConstructor();ctor.setAccessible(true);
                Object selfBlocked=ctor.newInstance();set(selfBlocked,"dx",1);set(selfBlocked,"dy",0);
                @SuppressWarnings("unchecked") ArrayList<Point> trapPts=(ArrayList<Point>)get(selfBlocked,"pts");
                trapPts.add(new Point(0,0));trapPts.add(new Point(4,0));trapPts.add(new Point(4,2));
                trapPts.add(new Point(1,2));trapPts.add(new Point(1,1));trapPts.add(new Point(2,1));
                assertFalse((Boolean)call(game,"hasSelfClearance",new Class[]{pieceClass},selfBlocked));
                float length=(Float)call(game,"piecePathLength",new Class[]{bent.getClass()},bent);
                PointF head=(PointF)call(game,"routePoint",new Class[]{bent.getClass(),float.class},bent,length+3f);
                ArrayList<Point> points=(ArrayList<Point>)get(bent,"pts");Point tip=points.get(points.size()-1);
                assertEquals(tip.x+3*(Integer)get(bent,"dx"),head.x,.001);assertEquals(tip.y+3*(Integer)get(bent,"dy"),head.y,.001);
                set(game,"hints",0);int before=wallet.balance();call(game,"useHint",new Class[]{});assertEquals(before-25,wallet.balance());
                call(game,"useHint",new Class[]{});assertEquals(before-25,wallet.balance());
                call(game,"tapPiece",new Class[]{safe.getClass()},safe);game.saveProgress();game.release();
                ArrowGameView restored=new ArrowGameView(context,new Host(),new Wallet(new PreferenceWalletStorage(context)));restored.setPaused(true);set(restored,"tutorial",false);
                assertEquals(0,get(restored,"hints"));int remaining=(Integer)call(restored,"remaining",new Class[]{});assertEquals(levelOneCount-1,remaining);assertEquals(75,wallet.balance());
                for(int lv:new int[]{100,200}){call(restored,"startLevel",new Class[]{int.class},lv);render(context,restored,"03-level-"+lv);}
                set(restored,"finished",true);set(restored,"winReward",50);render(context,restored,"04-win");
                set(restored,"finished",false);set(restored,"failed",true);set(restored,"hearts",0);render(context,restored,"05-fail");restored.release();
                assertTrue(BuildConfig.DEBUG);assertTrue(BuildConfig.ADMOB_REWARDED_ID.startsWith("ca-app-pub-3940256099942544/"));
                exportStoreGraphics(context);
                context.getSharedPreferences("arrow_puzzle_faithful",0).edit().remove("v16_progress").putInt("lastLevel",1).putBoolean("tutorialSeen",true).commit();
            }catch(Throwable t){error[0]=t;}
        });
        if(error[0]!=null)throw new AssertionError(error[0]);
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        captureWindow(context,"06-device-puzzle");
        instrumentation.runOnMainSync(activity::openWallet);instrumentation.waitForIdleSync();
        captureWindow(context,"07-device-wallet");
        instrumentation.runOnMainSync(activity::openSettings);instrumentation.waitForIdleSync();
        captureWindow(context,"08-device-settings");
        instrumentation.runOnMainSync(activity::finish);
    }
    private static void captureWindow(Context context,String name) throws Exception {
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File directory=new File(context.getExternalFilesDir(null),"screenshots");directory.mkdirs();
        try(FileOutputStream file=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();
    }
    private static void exportStoreGraphics(Context context) throws Exception {
        File directory=new File(context.getExternalFilesDir(null),"screenshots");directory.mkdirs();
        android.graphics.drawable.Drawable icon=context.getDrawable(R.drawable.icon_art);
        Bitmap storeIcon=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);
        Canvas iconCanvas=new Canvas(storeIcon);iconCanvas.drawColor(0xFF081D49);
        icon.setBounds(0,0,512,512);icon.draw(iconCanvas);
        try(FileOutputStream file=new FileOutputStream(new File(directory,"play-icon-512.png"))){storeIcon.compress(Bitmap.CompressFormat.PNG,100,file);}storeIcon.recycle();
        Bitmap feature=Bitmap.createBitmap(1024,500,Bitmap.Config.ARGB_8888);feature.setHasAlpha(false);
        Canvas canvas=new Canvas(feature);canvas.drawColor(0xFF081D49);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF163464);canvas.drawCircle(900,160,360,paint);
        paint.setColor(0xFF217FE7);canvas.drawCircle(80,525,220,paint);
        icon.setBounds(654,95,964,405);icon.draw(canvas);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
        paint.setColor(android.graphics.Color.WHITE);paint.setTextSize(58);canvas.drawText("Arrow Escape",60,165,paint);
        paint.setColor(0xFFFFCC39);paint.setTextSize(45);canvas.drawText("Puzzle Maze",60,223,paint);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));
        paint.setColor(0xFFBDD6FA);paint.setTextSize(25);canvas.drawText("200 mazes. One clear way out.",62,282,paint);
        android.graphics.Path route=new android.graphics.Path();route.moveTo(65,375);route.lineTo(215,375);route.lineTo(215,330);route.lineTo(382,330);
        paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(10);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);paint.setColor(android.graphics.Color.WHITE);canvas.drawPath(route,paint);
        paint.setStyle(android.graphics.Paint.Style.FILL);android.graphics.Path tip=new android.graphics.Path();tip.moveTo(412,330);tip.lineTo(379,311);tip.lineTo(379,349);tip.close();canvas.drawPath(tip,paint);
        try(FileOutputStream file=new FileOutputStream(new File(directory,"play-feature-1024x500.png"))){feature.compress(Bitmap.CompressFormat.PNG,100,file);}feature.recycle();
    }
}
