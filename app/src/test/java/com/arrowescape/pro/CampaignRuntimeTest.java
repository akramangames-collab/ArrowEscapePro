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

    private Object shieldTestPiece(ArrowGameView g,boolean clear)throws Exception{
        for(Object p:(ArrayList<?>)get(g,"pieces"))
            if(!(Boolean)get(p,"removed")&&!(Boolean)get(p,"moving")
                &&(Boolean)call(g,"isClear",new Class[]{p.getClass()},p)==clear)return p;
        throw new AssertionError("Expected "+(clear?"clear":"blocked")+" arrow");
    }
    private void earnFiveArrowCombo(ArrowGameView g)throws Exception{
        set(g,"combo",4);set(g,"lastCorrectTapAt",android.os.SystemClock.elapsedRealtime()+1L);
        Object p=shieldTestPiece(g,true);call(g,"tapPiece",new Class[]{p.getClass()},p);
        set(p,"moving",false);
    }
    private void blockedShieldTap(ArrowGameView g)throws Exception{
        Object p=shieldTestPiece(g,false);call(g,"tapPiece",new Class[]{p.getClass()},p);
    }
    @Test public void flowShieldProtectsOnlyOneMistakeAndCannotRechargeInSameLevel()throws Exception{
        ArrowGameView g=game();try{
            assertEquals(false,get(g,"flowShield"));assertEquals(false,get(g,"flowShieldEarned"));
            earnFiveArrowCombo(g);assertEquals(true,get(g,"flowShield"));
            blockedShieldTap(g);assertEquals(3,get(g,"hearts"));assertEquals(false,get(g,"flowShield"));
            earnFiveArrowCombo(g);assertEquals(false,get(g,"flowShield"));
            blockedShieldTap(g);assertEquals(2,get(g,"hearts"));
            earnFiveArrowCombo(g);assertEquals(false,get(g,"flowShield"));
        }finally{g.release();}
    }
    @Test public void flowShieldConsumptionSurvivesSaveAndResume()throws Exception{
        ArrowGameView g=game();try{
            earnFiveArrowCombo(g);blockedShieldTap(g);g.saveProgress();g.release();g=game();
            assertEquals(true,get(g,"flowShieldEarned"));assertEquals(false,get(g,"flowShield"));
            earnFiveArrowCombo(g);assertEquals(false,get(g,"flowShield"));
            blockedShieldTap(g);assertEquals(2,get(g,"hearts"));
        }finally{g.release();}
    }
    @Test public void chargedFlowShieldSurvivesResumeButNewLevelStartsUncharged()throws Exception{
        ArrowGameView g=game();try{
            earnFiveArrowCombo(g);g.saveProgress();g.release();g=game();
            assertEquals(true,get(g,"flowShield"));assertEquals(true,get(g,"flowShieldEarned"));
            blockedShieldTap(g);assertEquals(3,get(g,"hearts"));
            call(g,"startLevel",new Class[]{int.class},2);
            assertEquals(false,get(g,"flowShield"));assertEquals(false,get(g,"flowShieldEarned"));
            earnFiveArrowCombo(g);assertEquals(true,get(g,"flowShield"));
        }finally{g.release();}
    }
    @Test public void legacyCheckpointWithPriorFiveComboCannotGrantAnotherShield()throws Exception{
        ArrowGameView g=game();try{
            earnFiveArrowCombo(g);blockedShieldTap(g);g.saveProgress();g.release();
            android.content.SharedPreferences p=context.getSharedPreferences("arrow_puzzle_faithful",0);
            org.json.JSONObject state=new org.json.JSONObject(p.getString("v16_progress",""));
            state.remove("flowShieldEarned");p.edit().putString("v16_progress",state.toString()).commit();
            g=game();assertEquals(true,get(g,"flowShieldEarned"));assertEquals(false,get(g,"flowShield"));
            earnFiveArrowCombo(g);assertEquals(false,get(g,"flowShield"));
        }finally{g.release();}
    }

    @Test public void goldKeyStaysLastAfterAnAssistRemovesItsDirectBlockers()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},6);Object key=null;
            for(Object p:(ArrayList<?>)get(g,"pieces"))if((Integer)get(p,"id")==((CageRescue)get(g,"cageRescue")).arrowId())key=p;
            assertNotNull(key);
            for(Object p:(ArrayList<?>)call(g,"physicalBlockers",new Class[]{key.getClass()},key))set(p,"removed",true);
            assertTrue((Integer)call(g,"remaining",new Class[]{})>1);
            assertFalse((Boolean)call(g,"isClear",new Class[]{key.getClass()},key));
            for(Object p:(ArrayList<?>)get(g,"pieces"))if(p!=key)set(p,"removed",true);
            assertTrue((Boolean)call(g,"isClear",new Class[]{key.getClass()},key));
        }finally{g.release();}
    }
    @Test public void everyRemovalBoosterProtectsGoldEvenWhenItIsLast()throws Exception{
        ArrowGameView g=game();try{
            ArrayList<Object> all=(ArrayList<Object>)get(g,"pieces");Object key=null;
            int id=((CageRescue)get(g,"cageRescue")).arrowId();
            for(Object p:all)if((Integer)get(p,"id")==id)key=p;else set(p,"removed",true);
            assertNotNull(key);
            for(int mode:new int[]{1,2,3}){
                g.grantBooster(mode,"gold-immune-"+mode);g.beginBooster(mode);
                ((ArrayList<Object>)get(g,"boosterTargets")).add(key);set(g,"boosterTargeted",true);
                call(g,"confirmBooster",new Class[]{});assertEquals(false,get(key,"removed"));assertEquals(1,g.boosterCount(mode));g.cancelBooster();
            }
            g.grantBooster(BoosterInventory.TRIPLE,"gold-triple");
            assertFalse(g.canUseInstantBooster(BoosterInventory.TRIPLE));g.useInstantBooster(BoosterInventory.TRIPLE);assertEquals(1,g.boosterCount(BoosterInventory.TRIPLE));
            set(g,"autoMovesRemaining",3);call(g,"updateAnimations",new Class[]{float.class,long.class},1f,0L);
            assertEquals(false,get(key,"moving"));assertEquals(false,get(key,"removed"));assertFalse(((CageRescue)get(g,"cageRescue")).boxOpened());
            render(g,720,1560,"gold-key-manual-final");
            call(g,"tapPiece",new Class[]{key.getClass()},key);assertEquals(true,get(key,"moving"));set(key,"moveT",1f);
            call(g,"updateAnimations",new Class[]{float.class,long.class},3f,0L);assertEquals(true,get(g,"finished"));
        }finally{g.release();}
    }
    @Test public void waterPersistsFailsRevivesAndRendersRescueResults()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},6);set(g,"milestoneIntroUntil",0L);CageWater water=(CageWater)get(g,"cageWater");
            render(g,720,1560,"water-calm");water.restore(10000);water.revive();render(g,720,1560,"water-worried");float half=water.elapsed();
            g.saveProgress();g.release();g=game();water=(CageWater)get(g,"cageWater");assertEquals(half,water.elapsed(),.001f);
            call(g,"updateWater",new Class[]{float.class},half*.5f);assertEquals(RescueCharacterArt.STARTLED,call(g,"rescueExpression",new Class[]{long.class},0L));render(g,720,1560,"water-panic");
            call(g,"updateWater",new Class[]{float.class},10000f);assertEquals(true,get(g,"failed"));render(g,720,1560,"water-timeout");g.grantRevive();assertEquals(false,get(g,"failed"));assertEquals(.5f,water.fraction(),.001f);
            call(g,"startLevel",new Class[]{int.class},1000);for(Object p:(ArrayList<?>)get(g,"pieces"))set(p,"removed",true);call(g,"updateAnimations",new Class[]{float.class,long.class},3f,0L);assertEquals(true,get(g,"finished"));
            render(g,720,1560,"rescue-result-phone");render(g,720,1150,"rescue-result-short");render(g,1280,720,"rescue-result-landscape");
        }finally{g.release();}
    }
    @Test public void waterUsesRealTimeAndPausesWithMenusAndTutorial()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},6);set(g,"milestoneIntroUntil",0L);CageWater water=(CageWater)get(g,"cageWater");render(g,720,1560,null);
            set(g,"lastFrame",android.os.SystemClock.elapsedRealtime()-5000L);render(g,720,1560,null);assertTrue(water.elapsed()>=5f);float saved=water.elapsed();
            g.setPaused(true);set(g,"lastFrame",android.os.SystemClock.elapsedRealtime()-10000L);render(g,720,1560,null);assertEquals(saved,water.elapsed(),.001f);
            g.setPaused(false);set(g,"tutorial",true);set(g,"lastFrame",android.os.SystemClock.elapsedRealtime()-10000L);render(g,720,1560,null);assertEquals(saved,water.elapsed(),.001f);
        }finally{g.release();}
    }
    @Test public void campaignLoads1000DistinctPuzzlesAndPreservesOldCapProgress()throws Exception{
        context.getSharedPreferences("arrow_puzzle_faithful",0).edit().putInt("maxUnlocked",200).putInt("stars_200",3).putInt("lastLevel",200).commit();
        ArrowGameView g=game();try{
            assertEquals(1000,((String[])get(g,"packedLevels")).length);assertEquals(201,get(g,"maxUnlocked"));
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
            for(Object q:pieces)if((Integer)get(q,"id")!=((CageRescue)get(g,"cageRescue")).arrowId()){first=q;break;}
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
    @Test public void originalFourButtonsRemainAndScanTripleAppearWithoutAdPlusOne()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},11);
            render(g,720,1560,"v42-cage-plus-two-boosters");
            RectF[] old={(RectF)get(g,"hintHit"),(RectF)get(g,"eraseHit"),
                (RectF)get(g,"bombHit"),(RectF)get(g,"rewardHit")};
            RectF scan=(RectF)get(g,"scanHit"),triple=(RectF)get(g,"tripleHit");
            assertTrue(scan.width()>=48);assertTrue(scan.height()>=32);
            assertTrue(triple.width()>=48);assertTrue(triple.height()>=32);
            assertTrue(scan.bottom<old[0].top);assertTrue(triple.bottom<old[0].top);
            assertEquals(old[0].top,old[1].top,0.1f);
            assertEquals(old[1].top,old[2].top,0.1f);
            assertEquals(old[2].top,old[3].top,0.1f);
            assertTrue(scan.left>=0&&triple.right<=720);
            assertTrue((Boolean)get(g,"rescueBoard"));
            assertFalse(g.grantBooster(BoosterInventory.RETIRED_MISTAKE_SHIELD,"retired"));
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void safeScanOnlyHighlightsLegalArrowsAndNeverChangesCageOrCoinBalance()throws Exception{
        Host host=new Host();ArrowGameView g=new ArrowGameView(context,host,new Wallet(new PreferenceWalletStorage(context)));
        try{
            call(g,"startLevel",new Class[]{int.class},11);
            render(g,720,1560,"v42-cage-before-scan");
            int coins=((Wallet)get(g,"wallet")).balance(),before=(Integer)call(g,"remaining",new Class[]{});
            CageRescue cage=(CageRescue)get(g,"cageRescue");int keyId=cage.arrowId();
            g.useInstantBooster(BoosterInventory.SCAN);
            assertEquals(BoosterInventory.SCAN,host.requested);
            assertTrue(g.grantBooster(BoosterInventory.SCAN,"scan-once"));
            assertFalse(g.grantBooster(BoosterInventory.SCAN,"scan-once"));
            g.useInstantBooster(BoosterInventory.SCAN);
            assertEquals(0,g.boosterCount(BoosterInventory.SCAN));
            long now=android.os.SystemClock.elapsedRealtime();int safe=0,highlighted=0;
            for(Object piece:(ArrayList<Object>)get(g,"pieces")){
                if((Boolean)call(g,"isClear",new Class[]{piece.getClass()},piece)){
                    safe++;if((Long)get(piece,"hintUntil")>now)highlighted++;
                }
            }
            assertTrue(safe>0);assertEquals(safe,highlighted);
            assertEquals(before,call(g,"remaining",new Class[]{}));
            assertEquals(coins,((Wallet)get(g,"wallet")).balance());
            assertEquals(keyId,cage.arrowId());assertFalse(cage.boxOpened());
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void tripleEscapeReleasesOnlyThreeSequentialLegalArrowsAndRetainsV42Cage()throws Exception{
        ArrowGameView g=game();try{
            call(g,"startLevel",new Class[]{int.class},11);
            render(g,720,1560,"v42-cage-before-triple");
            int before=(Integer)call(g,"remaining",new Class[]{});
            int coins=((Wallet)get(g,"wallet")).balance();
            int cageArrow=((CageRescue)get(g,"cageRescue")).arrowId();
            assertTrue(g.grantBooster(BoosterInventory.TRIPLE,"triple-once"));
            assertEquals(1,g.boosterCount(BoosterInventory.TRIPLE));
            g.useInstantBooster(BoosterInventory.TRIPLE);
            assertEquals(0,g.boosterCount(BoosterInventory.TRIPLE));
            assertEquals(3,get(g,"autoMovesRemaining"));
            for(int i=0;i<25;i++)call(g,"updateAnimations",new Class[]{float.class,long.class},.5f,10000L+i*500L);
            assertEquals(0,get(g,"autoMovesRemaining"));
            assertEquals(before-3,call(g,"remaining",new Class[]{}));
            assertEquals(coins,((Wallet)get(g,"wallet")).balance());
            assertEquals(cageArrow,((CageRescue)get(g,"cageRescue")).arrowId());
            g.saveProgress();g.release();g=game();
            assertEquals(before-3,call(g,"remaining",new Class[]{}));
            assertEquals(0,g.boosterCount(BoosterInventory.TRIPLE));
        }finally{g.release();}
    }
    @SuppressWarnings("unchecked")
    @Test public void everyCampaignLevelSolvesUnderRuntimeSpecialAndTailRules()throws Exception{
        context.getSharedPreferences("arrow_escape_pro",0).edit().putBoolean("reduce_motion",true).commit();
        ArrowGameView g=game();try{
            for(int lv=1;lv<=1000;lv++){
                call(g,"startLevel",new Class[]{int.class},lv);
                ArrayList<Object> all=(ArrayList<Object>)get(g,"pieces");
                java.util.HashMap<Object,ArrayList<Object>> blockers=new java.util.HashMap<>();
                for(Object p:all)blockers.put(p,(ArrayList<Object>)call(g,"physicalBlockers",new Class[]{p.getClass()},p));
                int pending=all.size();
                while(pending>0){
                    Object safe=null;int choices=0;
                    for(Object p:all)if(!(Boolean)get(p,"removed")){
                        boolean blocked=false;for(Object b:blockers.get(p))if(!(Boolean)get(b,"removed")){blocked=true;break;}
                        if(!blocked){safe=p;choices++;}
                    }
                    assertEquals("Unique sequence at level "+lv,1,choices);
                    if(pending>1)assertNotEquals(((CageRescue)get(g,"cageRescue")).arrowId(),get(safe,"id"));
                    assertNotNull("Runtime deadlock at level "+lv,safe);
                    assertTrue((Boolean)call(g,"isClear",new Class[]{safe.getClass()},safe));
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
    @Test public void eraserCannotRemoveTheKeyBeforeOtherArrows()throws Exception{
        ArrowGameView g=game();try{
            CageRescue cage=(CageRescue)get(g,"cageRescue");Object target=null;
            for(Object p:(ArrayList<Object>)get(g,"pieces"))if((Integer)get(p,"id")==cage.arrowId())target=p;
            assertNotNull(target);g.grantBooster(1,"cage-eraser");g.beginBooster(1);
            ((ArrayList<Object>)get(g,"boosterTargets")).add(target);set(g,"boosterTargeted",true);
            call(g,"confirmBooster",new Class[]{});assertEquals(false,get(target,"removed"));
            assertEquals(1,g.boosterCount(1));
            call(g,"updateAnimations",new Class[]{float.class,long.class},0f,0L);assertFalse(cage.boxOpened());
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
            assertEquals(RescueCharacterArt.HOPEFUL,call(g,"rescueExpression",new Class[]{long.class},0L));
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
            assertEquals(RescueCharacterArt.HOPEFUL,call(g,"rescueExpression",new Class[]{long.class},hurtUntil+1));
            call(g,"tapPiece",new Class[]{safe.getClass()},safe);
            long encouragedUntil=(Long)get(g,"rescueEncouragementUntil");
            assertEquals(RescueCharacterArt.HOPEFUL,call(g,"rescueExpression",new Class[]{long.class},encouragedUntil-1));
            render(g,720,1560,"v41-character-hopeful");
            for(Object p:pieces)if(!(Boolean)get(p,"removed")&&!(Boolean)get(p,"moving")){set(p,"removed",true);break;}
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
            assertEquals(RescueCharacterArt.HOPEFUL,call(g,"rescueExpression",new Class[]{long.class},0L));
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
                if(length<=18)shortPaths++;if(length>=24)longPaths++;
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
    @Test public void tutorialHasThreeSimpleArrowsAroundAllFourSides()throws Exception{
        ArrowGameView g=game();try{
            int cx=(Integer)get(g,"gridW")/2,cy=(Integer)get(g,"gridH")/2;boolean left=false,right=false,top=false,bottom=false;
            ArrayList<Object> all=(ArrayList<Object>)get(g,"pieces");assertEquals(3,all.size());
            for(Object p:all)for(Point q:(ArrayList<Point>)get(p,"pts")){left|=q.x<cx-4;right|=q.x>cx+4;top|=q.y<cy-4;bottom|=q.y>cy+4;}
            assertTrue(left&&right&&top&&bottom);render(g,720,1560,"water-easy-tutorial");
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
    @Test public void v55EarnedRewardPreviewUsesRealLegalEscapesAndCreditedWallet()throws Exception{
        Wallet w=new Wallet(new PreferenceWalletStorage(context));ArrowGameView g=new ArrowGameView(context,new Host(),w);
        try{render(g,720,1560,null);int before=w.balance();
            for(int step=0;step<10&&!(Boolean)get(g,"finished");step++){
                Object chosen=null;CageRescue cage=(CageRescue)get(g,"cageRescue");int remaining=(Integer)call(g,"remaining",new Class[]{});
                for(Object p:(ArrayList<?>)get(g,"pieces"))if(!(Boolean)get(p,"removed")&&!(Boolean)get(p,"moving")&&((Integer)get(p,"id")!=cage.arrowId()||remaining==1)&&(Boolean)call(g,"isClear",new Class[]{p.getClass()},p)){chosen=p;break;}
                assertNotNull(chosen);call(g,"tapPiece",new Class[]{chosen.getClass()},chosen);assertEquals(true,get(chosen,"moving"));
                set(chosen,"moveT",1f);call(g,"updateAnimations",new Class[]{float.class,long.class},3f,10000L+step*3000L);
            }
            assertEquals(true,get(g,"finished"));assertEquals(25,get(g,"winReward"));assertEquals(before+25,w.balance());render(g,720,1560,"v55-earned-reward");
        }finally{g.release();}
    }
}
