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
        int requested=-1;public void requestRewardedBooster(int kind){requested=kind;}
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
    @Test public void oneFreeHintPersistsAfterRestartAndEmptyToolsRequestAds()throws Exception{
        Host host=new Host();ArrowGameView g=new ArrowGameView(context,host,new Wallet(new PreferenceWalletStorage(context)));
        try{
            int coins=((Wallet)get(g,"wallet")).balance();assertEquals(1,get(g,"hints"));
            call(g,"useHint",new Class[]{});assertEquals(0,get(g,"hints"));assertEquals(-1,host.requested);assertEquals(0,g.boosterCount(0));
            for(Object piece:(ArrayList<Object>)get(g,"pieces"))set(piece,"hintUntil",0L);
            call(g,"useHint",new Class[]{});assertEquals(0,host.requested);assertEquals(coins,((Wallet)get(g,"wallet")).balance());
            g.saveProgress();g.release();g=game();assertEquals(0,get(g,"hints"));
            call(g,"startLevel",new Class[]{int.class},2);assertEquals(1,get(g,"hints"));
            call(g,"useHint",new Class[]{});assertEquals(0,get(g,"hints"));
            g.grantBooster(0,"hint-ad");for(Object piece:(ArrayList<Object>)get(g,"pieces"))set(piece,"hintUntil",0L);
            call(g,"useHint",new Class[]{});assertEquals(0,g.boosterCount(0));
            host=new Host();g.release();g=new ArrowGameView(context,host,new Wallet(new PreferenceWalletStorage(context)));
            for(int kind=1;kind<=3;kind++){g.beginBooster(kind);assertEquals(kind,host.requested);assertEquals(0,get(g,"boosterMode"));assertEquals(0,g.boosterCount(kind));}
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void completingExtremeLevelGrantsTwoBombsOnceAndRestoresResult()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},30);
            for(Object piece:(ArrayList<Object>)get(g,"pieces"))set(piece,"removed",true);
            // v42 rescue: wait for the key-box/door opening before the result is shown.
            for(int frame=0;frame<6;frame++)
                call(g,"updateAnimations",new Class[]{float.class,long.class},.5f,10000L+frame*500L);
            assertEquals(2,g.boosterCount(2));assertEquals(2,get(g,"winBoosterAmount"));
            render(g,720,1560,"extreme-gift-result");assertTrue(g.challengeShareText().contains("Level 30"));
            g.saveProgress();g.release();g=game();assertEquals(2,g.boosterCount(2));assertEquals(2,get(g,"winBoosterAmount"));
            call(g,"startLevel",new Class[]{int.class},30);
            for(Object piece:(ArrayList<Object>)get(g,"pieces"))set(piece,"removed",true);
            for(int frame=0;frame<6;frame++)
                call(g,"updateAnimations",new Class[]{float.class,long.class},.5f,12000L+frame*500L);
            assertEquals(2,g.boosterCount(2));assertEquals(0,get(g,"winBoosterAmount"));
        }finally{g.release();}
    }
    @Test public void sixBoostersAreVisibleInTwoRowsAndRetiredShieldCannotBeGranted()throws Exception{
        ArrowGameView g=game();try{
            render(g,720,1280,"six-visible-boosters");
            RectF[] hits=(RectF[])get(g,"visibleBoosterHits");
            int[] kinds=(int[])get(g,"VISIBLE_BOOSTERS");
            assertEquals(6,hits.length);
            assertArrayEquals(new int[]{BoosterInventory.HINT,BoosterInventory.ERASER,
                BoosterInventory.BOMB,BoosterInventory.SWEEP,BoosterInventory.SCAN,
                BoosterInventory.TRIPLE},kinds);
            for(int i=0;i<hits.length;i++){
                RectF r=hits[i];
                assertTrue("Booster "+i+" width",r.width()>=48);
                assertTrue("Booster "+i+" height",r.height()>=48);
                assertTrue("Booster "+i+" on screen",r.left>=0&&r.right<=720&&r.top>=0&&r.bottom<=1280);
                if(i%3!=0)assertTrue("Columns do not overlap",r.left>=hits[i-1].right);
                if(i>=3)assertTrue("Rows do not overlap",r.top>=hits[i-3].bottom);
                assertTrue(g.grantBooster(kinds[i],"visible-"+i));
            }
            assertFalse(g.grantBooster(BoosterInventory.SHIELD,"retired"));
            assertEquals(0,g.boosterCount(BoosterInventory.SHIELD));
            int coins=((Wallet)get(g,"wallet")).balance();
            for(int mode=1;mode<=3;mode++){
                g.beginBooster(mode);
                assertEquals(mode,get(g,"boosterMode"));
                g.cancelBooster();assertEquals(1,g.boosterCount(mode));
            }
            assertEquals(coins,((Wallet)get(g,"wallet")).balance());
        }finally{g.release();}
    }

    @SuppressWarnings("unchecked")
    @Test public void automaticFlowShieldIsOneUseAndSurvivesGameRecreation()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},30);
            assertFalse(g.grantBooster(BoosterInventory.SHIELD,"retired-shield"));
            ArrayList<Object> all=(ArrayList<Object>)get(g,"pieces");
            Object safe=null;
            for(Object p:all)if((Boolean)call(g,"isClear",new Class[]{p.getClass()},p)){safe=p;break;}
            assertNotNull(safe);
            set(g,"combo",4);
            set(g,"lastCorrectTapAt",android.os.SystemClock.elapsedRealtime());
            call(g,"tapPiece",new Class[]{safe.getClass()},safe);
            assertTrue((Boolean)get(g,"flowShield"));
            assertTrue((Boolean)get(g,"flowShieldUsed"));
            g.saveProgress();g.release();g=game();
            assertTrue((Boolean)get(g,"flowShield"));
            assertTrue((Boolean)get(g,"flowShieldUsed"));
            Object blocked=null;
            for(Object p:(ArrayList<Object>)get(g,"pieces")){
                if((Boolean)get(p,"removed")||(Boolean)get(p,"moving"))continue;
                if(!(Boolean)call(g,"isClear",new Class[]{p.getClass()},p)){blocked=p;break;}
            }
            assertNotNull(blocked);
            int hearts=(Integer)get(g,"hearts");
            call(g,"tapPiece",new Class[]{blocked.getClass()},blocked);
            assertEquals(hearts,get(g,"hearts"));
            assertFalse((Boolean)get(g,"flowShield"));
            assertTrue((Boolean)get(g,"flowShieldUsed"));
            set(g,"combo",4);
            Object another=null;
            for(Object p:(ArrayList<Object>)get(g,"pieces"))
                if(!(Boolean)get(p,"removed") && !(Boolean)get(p,"moving") &&
                    (Boolean)call(g,"isClear",new Class[]{p.getClass()},p)){another=p;break;}
            assertNotNull(another);
            set(g,"lastCorrectTapAt",android.os.SystemClock.elapsedRealtime());
            call(g,"tapPiece",new Class[]{another.getClass()},another);
            assertFalse("Flow Shield cannot recharge this attempt",(Boolean)get(g,"flowShield"));
            call(g,"tapPiece",new Class[]{blocked.getClass()},blocked);
            assertEquals(hearts-1,get(g,"hearts"));
            g.restartCurrentLevel();
            assertFalse((Boolean)get(g,"flowShieldUsed"));
        }finally{g.release();}
    }

    @SuppressWarnings("unchecked")
    @Test public void cageKeyArrowIsLastOnEveryCampaignLevelAndCannotBeBombed()throws Exception{
        ArrowGameView g=game();try{
            for(int lv=1;lv<=400;lv++){
                call(g,"startLevel",new Class[]{int.class},lv);
                assertTrue("Missing rescue cage at "+lv,(Boolean)get(g,"rescueBoard"));
                CageRescue rescue=(CageRescue)get(g,"cageRescue");
                int keyId=rescue.arrowId();
                ArrayList<Object> all=(ArrayList<Object>)get(g,"pieces");
                assertTrue(keyId>=0);
                int pending=all.size();
                Object last=null,key=null;
                for(Object p:all)if((Integer)get(p,"id")==keyId){key=p;break;}
                assertNotNull(key);
                assertFalse("Key escapes before other arrows in level "+lv,
                        (Boolean)call(g,"isClear",new Class[]{key.getClass()},key));
                if(lv==11){
                    render(g,720,1280,"cage-key-11");
                    assertTrue(g.grantBooster(BoosterInventory.BOMB,"bomb-for-cage"));
                    g.beginBooster(BoosterInventory.BOMB);
                    Object pt=((ArrayList<Point>)get(key,"pts")).get(0);
                    float cell=(Float)get(g,"cell");
                    Point head=(Point)pt;
                    call(g,"selectBoosterTarget",new Class[]{float.class,float.class},
                        (Float)get(g,"boardLeft")+head.x*cell,
                        (Float)get(g,"boardTop")+head.y*cell*(Float)get(g,"boardStretchY"));
                    assertFalse(((ArrayList<?>)get(g,"boosterTargets")).contains(key));
                    g.cancelBooster();
                }
                while(pending>0){
                    Object safe=null;
                    for(Object p:all)if(!(Boolean)get(p,"removed")&&
                            (Boolean)call(g,"isClear",new Class[]{p.getClass()},p)){
                        safe=p;break;
                    }
                    assertNotNull("Unsolvable with final key: level "+lv,safe);
                    if(pending>1)assertNotSame("Key escaped prematurely on "+lv,key,safe);
                    set(safe,"removed",true);last=safe;pending--;
                }
                assertSame("Wrong final arrow on level "+lv,key,last);
                call(g,"syncRescueKey",new Class[]{});
                assertTrue("Final key failed to open box "+lv,rescue.boxOpened());
            }
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void rewardedScanHighlightsOnlySafeArrowsAndTripleEscapesSequentially()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},30);
            ArrayList<Object> pieces=(ArrayList<Object>)get(g,"pieces");
            assertTrue(g.grantBooster(BoosterInventory.SCAN,"scan-test"));
            g.useInstantBooster(BoosterInventory.SCAN);
            long now=android.os.SystemClock.elapsedRealtime();
            int safe=0,glowing=0;
            for(Object p:pieces)if((Boolean)call(g,"isClear",new Class[]{p.getClass()},p)){
                safe++;
                if((Long)get(p,"hintUntil")>now)glowing++;
            }
            assertTrue(safe>0);assertEquals(safe,glowing);
            assertEquals(0,g.boosterCount(BoosterInventory.SCAN));
            assertTrue(g.grantBooster(BoosterInventory.TRIPLE,"triple-test"));
            int before=(Integer)call(g,"remaining",new Class[]{});
            g.useInstantBooster(BoosterInventory.TRIPLE);
            assertEquals(0,g.boosterCount(BoosterInventory.TRIPLE));
            for(int i=0;i<5;i++)call(g,"updateAnimations",new Class[]{float.class,long.class},2f,now+1000L*(i+1));
            assertEquals(before-3,call(g,"remaining",new Class[]{}));
            assertEquals(0,get(g,"autoMovesRemaining"));
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
