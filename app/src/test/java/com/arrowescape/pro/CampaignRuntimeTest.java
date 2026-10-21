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
            call(g,"updateAnimations",new Class[]{float.class,long.class},.1f,10000L);
            assertEquals(false,get(g,"finished"));
            call(g,"updateAnimations",new Class[]{float.class,long.class},3f,10000L);
            assertEquals(2,g.boosterCount(2));assertEquals(2,get(g,"winBoosterAmount"));
            render(g,720,1560,"extreme-gift-result");assertTrue(g.challengeShareText().contains("Level 30"));
            g.saveProgress();g.release();g=game();assertEquals(2,g.boosterCount(2));assertEquals(2,get(g,"winBoosterAmount"));
            call(g,"startLevel",new Class[]{int.class},30);
            for(Object piece:(ArrayList<Object>)get(g,"pieces"))set(piece,"removed",true);
            call(g,"updateAnimations",new Class[]{float.class,long.class},.1f,10001L);
            call(g,"updateAnimations",new Class[]{float.class,long.class},3f,10001L);
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
        context.getSharedPreferences("arrow_escape_pro",0).edit().putBoolean("reduce_motion",true).commit();
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
                call(g,"updateAnimations",new Class[]{float.class,long.class},0f,0L);
                assertTrue("Cage did not unlock at level "+lv,((CageRescue)get(g,"cageRescue")).cageOpened());
                assertEquals("Rescue did not finish at level "+lv,true,get(g,"finished"));
            }
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void markedArrowFinishesBeforeKeyFliesAndCompletionWaitsForCage()throws Exception{
        ArrowGameView g=game();try{
            render(g,720,1560,"v42-cage-locked");CageRescue cage=(CageRescue)get(g,"cageRescue");
            ArrayList<Object> all=(ArrayList<Object>)get(g,"pieces");Object target=null;
            for(Object p:all)if((Integer)get(p,"id")==cage.arrowId())target=p;
            assertNotNull(target);assertFalse((Boolean)call(g,"isClear",new Class[]{target.getClass()},target));
            while(!(Boolean)call(g,"isClear",new Class[]{target.getClass()},target)){
                Object safe=null;for(Object p:all)if(!(Boolean)get(p,"removed")&&(Boolean)call(g,"isClear",new Class[]{p.getClass()},p)){safe=p;break;}
                assertNotNull(safe);set(safe,"removed",true);
            }
            call(g,"tapPiece",new Class[]{target.getClass()},target);assertFalse(cage.boxOpened());
            set(target,"moveT",1f);call(g,"updateAnimations",new Class[]{float.class,long.class},0f,0L);
            assertTrue(cage.boxOpened());assertFalse(cage.cageOpened());
            call(g,"updateAnimations",new Class[]{float.class,long.class},.2f,0L);render(g,720,1560,"v42-key-box-opening");
            call(g,"updateAnimations",new Class[]{float.class,long.class},.4f,0L);render(g,720,1560,"v42-key-flying");
            for(Object p:all)set(p,"removed",true);
            call(g,"updateAnimations",new Class[]{float.class,long.class},.15f,0L);assertEquals(false,get(g,"finished"));
            call(g,"updateAnimations",new Class[]{float.class,long.class},1f,0L);assertTrue(cage.cageOpened());assertEquals(false,get(g,"finished"));
            render(g,720,1560,"v42-cage-open");
            call(g,"updateAnimations",new Class[]{float.class,long.class},.7f,0L);assertEquals(true,get(g,"finished"));
            call(g,"startLevel",new Class[]{int.class},2);assertFalse(cage.boxOpened());
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void savedRemovedKeyRestoresOpenCageWithoutChangingPackOrWallet()throws Exception{
        ArrowGameView g=game();int balance;
        try{
            CageRescue cage=(CageRescue)get(g,"cageRescue");
            for(Object p:(ArrayList<Object>)get(g,"pieces"))if((Integer)get(p,"id")==cage.arrowId())set(p,"removed",true);
            balance=((Wallet)get(g,"wallet")).balance();g.saveProgress();
        }finally{g.release();}
        ArrowGameView restored=game();try{
            assertTrue(((CageRescue)get(restored,"cageRescue")).cageOpened());
            assertEquals(balance,((Wallet)get(restored,"wallet")).balance());
        }finally{restored.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void erasingTheMarkedArrowCannotLeaveTheCagePermanentlyLocked()throws Exception{
        ArrowGameView g=game();try{
            CageRescue cage=(CageRescue)get(g,"cageRescue");Object target=null;
            for(Object p:(ArrayList<Object>)get(g,"pieces"))if((Integer)get(p,"id")==cage.arrowId())target=p;
            assertNotNull(target);g.grantBooster(1,"cage-eraser");g.beginBooster(1);
            ((ArrayList<Object>)get(g,"boosterTargets")).add(target);set(g,"boosterTargeted",true);
            call(g,"confirmBooster",new Class[]{});assertEquals(true,get(target,"removed"));
            call(g,"updateAnimations",new Class[]{float.class,long.class},0f,0L);assertTrue(cage.boxOpened());
            call(g,"updateAnimations",new Class[]{float.class,long.class},3f,0L);assertTrue(cage.cageOpened());
            assertEquals(false,get(g,"finished"));
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void rescueShelterFitsDenseBoardsAndEveryArrowStaysOutsideIt()throws Exception{
        ArrowGameView g=game();try{
            for(int lv:new int[]{1,6,12,30,400}){
                call(g,"startLevel",new Class[]{int.class},lv);
                assertEquals(true,get(g,"rescueBoard"));
                int cx=(Integer)get(g,"gridW")/2,cy=(Integer)get(g,"gridH")/2;
                for(Object piece:(ArrayList<Object>)get(g,"pieces")){
                    for(Point p:(ArrayList<Point>)get(piece,"pts"))
                        assertFalse(Math.abs(p.x-cx)<=4&&Math.abs(p.y-cy)<=4);
                }
                render(g,720,1560,"v41-rescue-level-"+lv);
            }
            render(g,1600,1100,"v41-rescue-tablet");
            render(g,1280,720,"v41-rescue-landscape");
        }finally{g.release();}
    }
    @Test public void priorLevelPackRestartsBoardWithoutLosingWalletStarsOrUnlocks()throws Exception{
        context.getSharedPreferences("arrow_puzzle_faithful",0).edit()
            .putInt("maxUnlocked",50).putInt("stars_12",3).putInt("lastLevel",12)
            .putString("v16_progress","{\"packVersion\":\"v32-deeper-400-1\",\"level\":12,\"removed\":[0,1,2]}").commit();
        Wallet wallet=new Wallet(new PreferenceWalletStorage(context));int balance=wallet.balance();
        ArrowGameView g=new ArrowGameView(context,new Host(),wallet);try{
            assertEquals(balance,wallet.balance());assertEquals(50,get(g,"maxUnlocked"));
            assertEquals(3,g.totalStarCount());
            for(Object piece:(ArrayList<?>)get(g,"pieces"))assertEquals(false,get(piece,"removed"));
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void rescueCharacterReactsToRealTapsProgressResultsAndRestart()throws Exception{
        ArrowGameView g=game();try{
            assertEquals(RescueCharacterArt.WORRIED,call(g,"rescueExpression",new Class[]{long.class},0L));
            ArrayList<Object> pieces=(ArrayList<Object>)get(g,"pieces");Object blocked=null,safe=null;
            for(Object p:pieces){
                if((Boolean)call(g,"isClear",new Class[]{p.getClass()},p))safe=p;else blocked=p;
            }
            assertNotNull(blocked);assertNotNull(safe);
            call(g,"tapPiece",new Class[]{blocked.getClass()},blocked);
            long hurtUntil=(Long)get(g,"rescueReactionUntil");
            assertEquals(2,get(g,"hearts"));
            assertEquals(RescueCharacterArt.STARTLED,call(g,"rescueExpression",new Class[]{long.class},hurtUntil-1));
            render(g,720,1560,"v41-character-blocked");
            assertEquals(RescueCharacterArt.WORRIED,call(g,"rescueExpression",new Class[]{long.class},hurtUntil+1));
            call(g,"tapPiece",new Class[]{safe.getClass()},safe);
            long encouragedUntil=(Long)get(g,"rescueEncouragementUntil");
            assertEquals(RescueCharacterArt.HOPEFUL,call(g,"rescueExpression",new Class[]{long.class},encouragedUntil-1));
            render(g,720,1560,"v41-character-hopeful");
            for(int i=0;i<pieces.size()/2;i++)set(pieces.get(i),"removed",true);
            assertTrue((Float)call(g,"rescueProgress",new Class[]{})>=.4f);
            assertEquals(RescueCharacterArt.HOPEFUL,call(g,"rescueExpression",new Class[]{long.class},encouragedUntil+1));
            render(g,720,1560,"v41-character-half-clear");
            set(g,"finished",true);set(g,"earnedStars",3);
            assertEquals(RescueCharacterArt.HAPPY,call(g,"rescueExpression",new Class[]{long.class},0L));
            render(g,720,1560,"v41-character-rescued");
            set(g,"finished",false);set(g,"failed",true);
            assertEquals(RescueCharacterArt.STARTLED,call(g,"rescueExpression",new Class[]{long.class},0L));
            render(g,720,1560,"v41-character-retry");
            call(g,"startLevel",new Class[]{int.class},2);
            assertEquals(0L,get(g,"rescueReactionUntil"));assertEquals(0L,get(g,"rescueEncouragementUntil"));
            assertEquals(RescueCharacterArt.WORRIED,call(g,"rescueExpression",new Class[]{long.class},0L));
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void firstLevelLeavesSpaceAndUsesMostlyShortPaths()throws Exception{
        ArrowGameView g=game();try{
            ArrayList<Object> pieces=(ArrayList<Object>)get(g,"pieces");
            int shortPaths=0,longPaths=0,maxBends=0,openings=0,simplePaths=0;
            for(Object piece:pieces){
                ArrayList<Point> pts=(ArrayList<Point>)get(piece,"pts");int length=0;
                for(int i=1;i<pts.size();i++)length+=Math.abs(pts.get(i).x-pts.get(i-1).x)+Math.abs(pts.get(i).y-pts.get(i-1).y);
                assertTrue("Oversized path",length<=32);
                if(length<=14)shortPaths++;if(length>=24)longPaths++;
                maxBends=Math.max(maxBends,pts.size()-2);if(pts.size()-2<=3)simplePaths++;
                if((Boolean)call(g,"isClear",new Class[]{piece.getClass()},piece))openings++;
            }
            assertTrue(shortPaths>=pieces.size()/2);assertTrue(longPaths<=pieces.size()*.12);assertTrue(maxBends<=6);assertTrue(simplePaths>=pieces.size()*.9);assertTrue(openings>=1&&openings<=4);
            int occupied=0;for(Object piece:pieces)occupied+=((java.util.Set<?>)get(piece,"nodes")).size();
            int usable=((Integer)get(g,"gridW")+1)*((Integer)get(g,"gridH")+1)-81;
            assertTrue("Intentional space missing",occupied<=usable*.76);
            render(g,720,1560,"v41-interwoven-level-1");
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void firstBoardHasPathsAcrossEveryHorizontalBoundary()throws Exception{
        ArrowGameView g=game();try{
            int h=(Integer)get(g,"gridH");int[] bridges=new int[h];
            for(Object piece:(ArrayList<Object>)get(g,"pieces")){
                ArrayList<Point> pts=(ArrayList<Point>)get(piece,"pts");
                for(int i=1;i<pts.size();i++){
                    Point a=pts.get(i-1),b=pts.get(i);
                    if(a.x==b.x)for(int y=Math.min(a.y,b.y);y<Math.max(a.y,b.y);y++)bridges[y]++;
                }
            }
            for(int y=0;y<h;y++)assertTrue("Horizontal separator at row "+y,bridges[y]>=1);
            render(g,720,1560,"v41-no-horizontal-bands");
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
