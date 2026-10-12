package com.arrowescape.pro;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.RectF;
import android.view.MotionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CampaignRuntimeTest {
    private Context context;
    private static Object get(Object o,String k)throws Exception{Field f=o.getClass().getDeclaredField(k);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String k,Object v)throws Exception{Field f=o.getClass().getDeclaredField(k);f.setAccessible(true);f.set(o,v);}
    private static Object call(Object o,String n,Class<?>[] types,Object...args)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
    private static final class Host implements ArrowGameView.Host {
        public void onLevelCompleted(){}public void requestRewardedErase(){}public void requestRewardedRevive(){}public void requestRewardedCoins(){}public void openWallet(){}public void openSettings(){}public void onContinueAfterWin(Runnable r){r.run();}
    }
    @Before public void reset(){
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("arrow_puzzle_faithful",0).edit().clear().putBoolean("tutorialSeen",true).commit();
        context.getSharedPreferences("arrow_escape_pro",0).edit().clear().commit();
    }
    private ArrowGameView game(){return new ArrowGameView(context,new Host(),new Wallet(new PreferenceWalletStorage(context)));}
    private void render(ArrowGameView g,int width,int height,String name)throws Exception{
        g.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(height,1073741824));g.layout(0,0,width,height);
        Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);g.draw(new Canvas(b));
        if(name!=null){File dir=new File("build/game-previews");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
        b.recycle();
    }
    @Test public void campaignLoads400DistinctPuzzlesAndPreservesOldCapProgress()throws Exception{
        context.getSharedPreferences("arrow_puzzle_faithful",0).edit().putInt("maxUnlocked",200).putInt("stars_200",3).putInt("lastLevel",200).commit();
        ArrowGameView g=game();try{
            assertEquals(400,((String[])get(g,"packedLevels")).length);assertEquals(201,get(g,"maxUnlocked"));
            call(g,"startLevel",new Class[]{int.class},400);assertEquals(400,g.currentLevelNumber());
            render(g,1080,1920,"level-400");
        }finally{g.release();}
    }
    @Test public void windingMapUsesDrawnHitTargetsAndCanReachLevel400()throws Exception{
        ArrowGameView g=game();try{
            set(g,"maxUnlocked",400);call(g,"startLevel",new Class[]{int.class},396);g.openLevels();
            render(g,1080,1920,"escape-trail-phone");assertEquals(19,get(g,"levelPage"));
            RectF[] hits=(RectF[])get(g,"mapNodeHits");
            for(int i=0;i<20;i++)if(hits[i].centerY()>(Float)get(g,"mapViewportTop")&&hits[i].centerY()<(Float)get(g,"mapViewportBottom")){
                assertTrue((Boolean)call(g,"handleLevelsTouch",new Class[]{float.class,float.class},hits[i].centerX(),hits[i].centerY()));
                assertEquals(381+i,g.currentLevelNumber());break;
            }
            g.openLevels();render(g,1600,1100,"escape-trail-tablet");
            call(g,"startLevel",new Class[]{int.class},1);g.openLevels();render(g,720,1280,"escape-trail-start");
            float before=(Float)get(g,"mapScroll"),top=(Float)get(g,"mapViewportTop");long now=100;
            g.onTouchEvent(MotionEvent.obtain(now,now,0,300,top+150,0));
            g.onTouchEvent(MotionEvent.obtain(now,now+20,2,300,top+300,0));
            g.onTouchEvent(MotionEvent.obtain(now,now+40,1,300,top+300,0));
            assertTrue((Float)get(g,"mapScroll")>before);assertEquals(1,g.currentLevelNumber());
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void bombPreviewCancelEmptyTargetAndDoubleConfirmCannotSpendTwice()throws Exception{
        ArrowGameView g=game();try{
            render(g,1080,1920,null);Wallet w=(Wallet)get(g,"wallet");int coins=w.balance();
            g.grantBooster(2,"test-bomb");g.beginBooster(2);g.cancelBooster();assertEquals(coins,w.balance());
            g.beginBooster(2);call(g,"confirmBooster",new Class[]{});assertEquals(coins,w.balance());
            ArrayList<Object> pieces=(ArrayList<Object>)get(g,"pieces");Object first=pieces.get(0);
            ArrayList<Point> pts=(ArrayList<Point>)get(first,"pts");Point p=pts.get(pts.size()-1);
            float cell=(Float)get(g,"cell"),x=(Float)get(g,"boardLeft")+p.x*cell,y=(Float)get(g,"boardTop")+p.y*cell*(Float)get(g,"boardStretchY");
            call(g,"selectBoosterTarget",new Class[]{float.class,float.class},x,y);
            ArrayList<?> targets=(ArrayList<?>)get(g,"boosterTargets");int count=targets.size();assertTrue(count>0);
            render(g,1080,1920,"area-bomb-preview");
            call(g,"confirmBooster",new Class[]{});assertEquals(coins,w.balance());assertEquals(0,g.boosterCount(2));
            assertEquals(pieces.size()-count,call(g,"remaining",new Class[]{}));assertEquals(1,get(g,"assistsUsed"));
            call(g,"confirmBooster",new Class[]{});assertEquals(coins,w.balance());assertEquals(0,g.boosterCount(2));
            g.saveProgress();g.release();g=game();assertEquals(pieces.size()-count,call(g,"remaining",new Class[]{}));
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void completingExtremeLevelGrantsTwoBombsOnceAndRestoresResult()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},30);
            for(Object piece:(ArrayList<Object>)get(g,"pieces"))set(piece,"removed",true);
            call(g,"updateAnimations",new Class[]{float.class,long.class},.1f,10000L);
            assertEquals(2,g.boosterCount(2));assertEquals(2,get(g,"winBoosterAmount"));
            render(g,720,1560,"extreme-gift-result");assertTrue(g.challengeShareText().contains("Level 30"));
            g.saveProgress();g.release();g=game();assertEquals(2,g.boosterCount(2));assertEquals(2,get(g,"winBoosterAmount"));
            call(g,"startLevel",new Class[]{int.class},30);
            for(Object piece:(ArrayList<Object>)get(g,"pieces"))set(piece,"removed",true);
            call(g,"updateAnimations",new Class[]{float.class,long.class},.1f,10001L);
            assertEquals(2,g.boosterCount(2));assertEquals(0,get(g,"winBoosterAmount"));
        }finally{g.release();}
    }
    @Test public void allFourFrontToolsStayVisibleAndEnterPreviewWithoutSpendingCoins()throws Exception{
        ArrowGameView g=game();try{
            render(g,720,1280,"four-front-boosters");
            String[] names={"hintHit","eraseHit","bombHit","rewardHit"};float previous=0;
            for(int i=0;i<4;i++){RectF r=(RectF)get(g,names[i]);assertTrue(r.width()>=48);assertTrue(r.height()>=48);assertTrue(r.left>=previous);assertTrue(r.bottom<=1280);previous=r.right;g.grantBooster(i,"front-"+i);}
            int coins=((Wallet)get(g,"wallet")).balance();
            for(int mode=1;mode<=3;mode++){g.beginBooster(mode);assertEquals(mode,get(g,"boosterMode"));g.cancelBooster();assertEquals(1,g.boosterCount(mode));}
            assertEquals(coins,((Wallet)get(g,"wallet")).balance());
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void everyCampaignLevelSolvesUnderRuntimeSpecialAndTailRules()throws Exception{
        ArrowGameView g=game();try{
            for(int lv=1;lv<=400;lv++){
                call(g,"startLevel",new Class[]{int.class},lv);
                ArrayList<Object> all=(ArrayList<Object>)get(g,"pieces");
                int pending=all.size();
                while(pending>0){
                    Object safe=null;
                    for(Object piece:all)if(!(Boolean)get(piece,"removed")&&
                        (Boolean)call(g,"isClear",new Class[]{piece.getClass()},piece)){safe=piece;break;}
                    assertNotNull("Runtime deadlock at level "+lv,safe);
                    set(safe,"removed",true);pending--;
                }
            }
        }finally{g.release();}
    }
    @Test public void insufficientBalanceKeepsBombTargetsAndBoardIntact()throws Exception{
        ArrowGameView g=game();try{
            render(g,1080,1920,null);Wallet w=(Wallet)get(g,"wallet");assertTrue(w.spend(100));
            g.beginBooster(2);call(g,"selectBoosterTarget",new Class[]{float.class,float.class},540f,960f);
            int remaining=(Integer)call(g,"remaining",new Class[]{});
            call(g,"confirmBooster",new Class[]{});assertEquals(remaining,call(g,"remaining",new Class[]{}));assertEquals(0,w.balance());
        }finally{g.release();}
    }
}
