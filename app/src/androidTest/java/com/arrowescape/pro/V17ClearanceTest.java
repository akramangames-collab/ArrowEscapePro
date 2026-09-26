package com.arrowescape.pro;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V17ClearanceTest {
    private interface Checked { void run() throws Exception; }
    private static void onMain(Checked action) throws Exception {
        Throwable[] error={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try { action.run(); } catch(Throwable t) { error[0]=t; }
        });
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private static Object get(Object o,String key) throws Exception {Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String key,Object v) throws Exception {Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,v);}
    private static Object call(Object o,String name,Class<?>[] types,Object... args) throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static class Host implements ArrowGameView.Host {
        public void onLevelCompleted(){} public void requestRewardedErase(){} public void requestRewardedRevive(){} public void requestRewardedCoins(){} public void openWallet(){} public void openSettings(){} public void onContinueAfterWin(Runnable p){p.run();}
    }
    private static ArrowGameView fresh() throws Exception {
        context().getSharedPreferences("arrow_puzzle_faithful",0).edit().clear().commit();
        context().getSharedPreferences("arrow_escape_pro",0).edit().clear().commit();
        ArrowGameView g=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        g.setPaused(true);set(g,"tutorial",false);return g;
    }
    @SuppressWarnings("unchecked") private static ArrayList<Object> pieces(ArrowGameView g) throws Exception {return (ArrayList<Object>)get(g,"pieces");}
    @SuppressWarnings("unchecked") private static ArrayList<Point> points(Object p) throws Exception {return (ArrayList<Point>)get(p,"pts");}
    private static boolean clear(ArrowGameView g,Object p) throws Exception {return (Boolean)call(g,"isClear",new Class[]{p.getClass()},p);}
    private static Object piece(ArrowGameView g,int... coords) throws Exception {
        Class<?> type=Class.forName("com.arrowescape.pro.ArrowGameView$Piece");
        Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object p=ctor.newInstance();
        for(int i=0;i<coords.length;i+=2)points(p).add(new Point(coords[i],coords[i+1]));
        ArrayList<Point> pts=points(p);Point a=pts.get(pts.size()-2),b=pts.get(pts.size()-1);
        set(p,"dx",Integer.compare(b.x,a.x));set(p,"dy",Integer.compare(b.y,a.y));
        call(g,"rebuildOccupancy",new Class[]{type},p);return p;
    }
    private static ArrayList<Point> expand(Object p) throws Exception {
        ArrayList<Point> pts=points(p),walk=new ArrayList<>();walk.add(new Point(pts.get(0)));
        for(int i=1;i<pts.size();i++){
            Point a=pts.get(i-1),b=pts.get(i);int x=a.x,y=a.y,dx=Integer.compare(b.x,x),dy=Integer.compare(b.y,y);
            while(x!=b.x||y!=b.y){x+=dx;y+=dy;walk.add(new Point(x,y));}
        }
        return walk;
    }
    private static boolean simulateSelf(Object p,int w,int h) throws Exception {
        ArrayList<Point> body=expand(p);Point tip=body.get(body.size()-1);int x=tip.x,y=tip.y;
        int dx=(Integer)get(p,"dx"),dy=(Integer)get(p,"dy");
        while(x>=0&&x<=w&&y>=0&&y<=h){
            x+=dx;y+=dy;body.remove(0);Point next=new Point(x,y);
            if(body.contains(next))return false;body.add(next);
        }
        return true;
    }

    @Test public void fullEscapeTimingPenaltiesHintsAndMovingBlockers() throws Exception {onMain(()->{
        ArrowGameView g=fresh();
        try {
            set(g,"gridW",20);set(g,"gridH",20);
            Object trap=piece(g,0,0,4,0,4,2,1,2,1,1,2,1);
            Object simultaneous=piece(g,4,0,4,4,0,4,0,2,2,2);
            Object late=piece(g,0,0,8,0,8,3,0,3,0,1,2,1);
            Object safeHook=piece(g,5,1,5,3,0,3,0,1,2,1);
            for(Object p:new Object[]{trap,simultaneous,late,safeHook}){
                boolean expected=p==safeHook;
                assertEquals(expected,simulateSelf(p,20,20));
                assertEquals(expected,call(g,"hasSelfClearance",new Class[]{p.getClass()},p));
                pieces(g).clear();pieces(g).add(p);assertEquals(expected,clear(g,p));
            }
            pieces(g).clear();pieces(g).add(trap);
            Object safe=piece(g,10,10,12,10);pieces(g).add(safe);
            call(g,"tapPiece",new Class[]{trap.getClass()},trap);
            assertEquals(2,get(g,"hearts"));assertEquals(1,get(g,"mistakes"));
            assertEquals("Tail blocks this escape",get(g,"toast"));
            assertTrue((Long)get(trap,"flashUntil")>SystemClock.elapsedRealtime());
            assertFalse((Boolean)get(trap,"moving"));
            set(g,"hints",0);Wallet wallet=(Wallet)get(g,"wallet");int balance=wallet.balance();
            call(g,"useHint",new Class[]{});
            assertEquals(balance-Wallet.HINT_COST,wallet.balance());
            assertTrue((Long)get(safe,"hintUntil")>SystemClock.elapsedRealtime());
            assertEquals(0L,get(trap,"hintUntil"));
            call(g,"useHint",new Class[]{});assertEquals(balance-Wallet.HINT_COST,wallet.balance());
            pieces(g).remove(safe);int afterHint=wallet.balance();
            call(g,"useHint",new Class[]{});assertEquals(afterHint,wallet.balance());
            call(g,"tapPiece",new Class[]{trap.getClass()},trap);
            call(g,"tapPiece",new Class[]{trap.getClass()},trap);
            assertEquals(0,get(g,"hearts"));assertEquals(3,get(g,"mistakes"));assertEquals(true,get(g,"failed"));
            call(g,"tapPiece",new Class[]{trap.getClass()},trap);assertEquals(0,get(g,"hearts"));

            set(g,"failed",false);set(g,"hearts",3);set(g,"mistakes",0);
            Object target=piece(g,0,6,2,6),blocker=piece(g,7,4,7,6);
            pieces(g).clear();pieces(g).add(target);pieces(g).add(blocker);
            call(g,"tapPiece",new Class[]{target.getClass()},target);
            assertEquals("Blocked by another arrow",get(g,"toast"));assertEquals(2,get(g,"hearts"));
            int blockerSteps=(Integer)call(g,"snakeTravelSteps",new Class[]{blocker.getClass()},blocker);
            set(blocker,"moving",true);set(blocker,"moveSteps",blockerSteps);set(blocker,"moveT",0f);
            assertTrue("A moving arrow must not reserve a lane when its future motion will not collide",clear(g,target));
            set(blocker,"removed",true);assertTrue(clear(g,target));

            Object crossingTarget=piece(g,0,10,2,10),crossing=piece(g,4,4,4,10);
            pieces(g).clear();pieces(g).add(crossingTarget);pieces(g).add(crossing);
            int crossingSteps=(Integer)call(g,"snakeTravelSteps",new Class[]{crossing.getClass()},crossing);
            set(crossing,"moving",true);set(crossing,"moveSteps",crossingSteps);set(crossing,"moveT",0f);
            assertTrue("An accepted moving arrow is logically gone even if its exit animation visually crosses another escape",clear(g,crossingTarget));

            Object bent=piece(g,0,0,0,4,4,4);
            PointF tail=(PointF)call(g,"routePoint",new Class[]{bent.getClass(),float.class},bent,2f);
            PointF head=(PointF)call(g,"routePoint",new Class[]{bent.getClass(),float.class},bent,10f);
            assertEquals(0f,tail.x,.001f);assertEquals(2f,tail.y,.001f);
            assertEquals(6f,head.x,.001f);assertEquals(4f,head.y,.001f);
        } finally {g.release();}
    });}

    @Test public void solveEveryShippedLevelWithIndependentOracle() throws Exception {onMain(()->{
        ArrowGameView g=fresh();StringBuilder report=new StringBuilder();int total=0;
        try {
            for(int lv=1;lv<=200;lv++){
                call(g,"startLevel",new Class[]{int.class},lv);
                int w=(Integer)get(g,"gridW"),h=(Integer)get(g,"gridH");
                ArrayList<Object> all=new ArrayList<>(pieces(g));HashMap<Point,Object> owner=new HashMap<>();
                HashMap<Object,HashSet<Object>> deps=new HashMap<>();
                for(Object p:all){
                    assertTrue("Self clearance at level "+lv,simulateSelf(p,w,h));
                    for(Point q:expand(p))assertNull("Overlapping arrows",owner.put(q,p));
                }
                int openings=0;
                for(Object p:all){
                    HashSet<Object> blocked=new HashSet<>();Point tip=points(p).get(points(p).size()-1);
                    int dx=(Integer)get(p,"dx"),dy=(Integer)get(p,"dy"),x=tip.x+dx,y=tip.y+dy;
                    while(x>=0&&x<=w&&y>=0&&y<=h){Object other=owner.get(new Point(x,y));if(other!=null&&other!=p)blocked.add(other);x+=dx;y+=dy;}
                    deps.put(p,blocked);assertEquals("Runtime/oracle mismatch at level "+lv,blocked.isEmpty(),clear(g,p));
                    if(blocked.isEmpty())openings++;
                }
                int expected=lv%5==0?1:lv==1?3:2;assertEquals("Openings at level "+lv,expected,openings);
                call(g,"useHint",new Class[]{});int highlighted=0;
                for(Object p:all)if((Long)get(p,"hintUntil")>0L){assertTrue(deps.get(p).isEmpty());highlighted++;}
                assertEquals(1,highlighted);
                HashSet<Object> pending=new HashSet<>(all);
                while(!pending.isEmpty()){
                    ArrayList<Object> ready=new ArrayList<>();
                    for(Object p:all)if(pending.contains(p)){
                        HashSet<Object> blocked=new HashSet<>(deps.get(p));blocked.retainAll(pending);
                        if(blocked.isEmpty())ready.add(p);
                    }
                    assertFalse("Unsolvable level "+lv,ready.isEmpty());assertTrue(ready.size()<=expected+1);
                    Object p=ready.get(0);assertTrue("Runtime rejected an oracle escape",clear(g,p));
                    set(p,"removed",true);pending.remove(p);total++;
                }
                report.append("Level ").append(lv).append(": ").append(all.size()).append(" arrows solved; ").append(openings).append(" openings\n");
            }
            report.append("PASS: all 200 levels solved through the Android runtime; ").append(total).append(" validated escapes.\n");
            File dir=new File(context().getExternalFilesDir(null),"screenshots");dir.mkdirs();
            try(FileOutputStream out=new FileOutputStream(new File(dir,"v17-level-validation.txt"))){out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        } finally {g.release();}
    });}

    @Test public void migrateOldBoardWithoutLosingWalletStarsOrDailyChallenge() throws Exception {onMain(()->{
        ArrowGameView g=fresh();Wallet wallet=(Wallet)get(g,"wallet");wallet.spend(25);
        android.content.SharedPreferences prefs=context().getSharedPreferences("arrow_puzzle_faithful",0);
        set(pieces(g).get(0),"removed",true);set(g,"hearts",1);g.saveProgress();g.release();
        org.json.JSONObject old=new org.json.JSONObject(prefs.getString("v16_progress",""));old.remove("packVersion");
        prefs.edit().putString("v16_progress",old.toString()).putInt("maxUnlocked",41).putInt("stars_25",3).commit();
        ArrowGameView restored=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        restored.setPaused(true);
        try {
            assertEquals(3,get(restored,"hearts"));assertEquals(24,call(restored,"remaining",new Class[]{}));
            assertEquals(41,get(restored,"maxUnlocked"));assertEquals(3,prefs.getInt("stars_25",0));
            assertEquals(75,((Wallet)get(restored,"wallet")).balance());
            restored.startDailyChallenge();assertEquals(true,get(restored,"dailyChallenge"));
            assertEquals(0,(Integer)get(restored,"level")%5);int ready=0;
            for(Object p:pieces(restored))if(clear(restored,p))ready++;assertEquals(1,ready);
            restored.saveProgress();
        } finally {restored.release();}
        ArrowGameView daily=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        try {assertEquals(true,get(daily,"dailyChallenge"));assertEquals(75,((Wallet)get(daily,"wallet")).balance());}
        finally {daily.release();}
    });}
}
