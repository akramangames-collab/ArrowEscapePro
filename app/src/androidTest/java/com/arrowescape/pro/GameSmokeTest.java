package com.arrowescape.pro;

import android.content.Context;
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
        float density=context.getResources().getDisplayMetrics().density;int width=(int)(360*density),height=(int)(780*density);
        game.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY));game.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);game.draw(new Canvas(bitmap));
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
                ArrayList<?> pieces=(ArrayList<?>)get(game,"pieces");assertEquals(48,pieces.size());
                Object bent=null,safe=null;
                for(Object p:pieces){if(((ArrayList<?>)get(p,"pts")).size()>2)bent=p;if((Boolean)call(game,"isClear",new Class[]{p.getClass()},p))safe=p;}
                assertNotNull(bent);assertNotNull(safe);
                float length=(Float)call(game,"piecePathLength",new Class[]{bent.getClass()},bent);
                PointF head=(PointF)call(game,"routePoint",new Class[]{bent.getClass(),float.class},bent,length+3f);
                ArrayList<Point> points=(ArrayList<Point>)get(bent,"pts");Point tip=points.get(points.size()-1);
                assertEquals(tip.x+3*(Integer)get(bent,"dx"),head.x,.001);assertEquals(tip.y+3*(Integer)get(bent,"dy"),head.y,.001);
                set(game,"hints",0);int before=wallet.balance();call(game,"useHint",new Class[]{});assertEquals(before-25,wallet.balance());
                call(game,"useHint",new Class[]{});assertEquals(before-25,wallet.balance());
                call(game,"tapPiece",new Class[]{safe.getClass()},safe);game.saveProgress();game.release();
                ArrowGameView restored=new ArrowGameView(context,new Host(),new Wallet(new PreferenceWalletStorage(context)));restored.setPaused(true);
                assertEquals(0,get(restored,"hints"));int remaining=(Integer)call(restored,"remaining",new Class[]{});assertEquals(47,remaining);assertEquals(75,wallet.balance());
                for(int lv:new int[]{100,200}){call(restored,"startLevel",new Class[]{int.class},lv);render(context,restored,"03-level-"+lv);}
                set(restored,"finished",true);set(restored,"winReward",50);render(context,restored,"04-win");
                set(restored,"finished",false);set(restored,"failed",true);set(restored,"hearts",0);render(context,restored,"05-fail");restored.release();
                assertTrue(BuildConfig.DEBUG);assertTrue(BuildConfig.ADMOB_REWARDED_ID.startsWith("ca-app-pub-3940256099942544/"));
            }catch(Throwable t){error[0]=t;}
        });
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
}
