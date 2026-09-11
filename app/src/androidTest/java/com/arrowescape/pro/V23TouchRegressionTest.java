package com.arrowescape.pro;

import android.content.Context;
import android.graphics.Point;
import android.view.MotionEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V23TouchRegressionTest {
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
    @SuppressWarnings("unchecked") private static ArrayList<Object> pieces(ArrowGameView g) throws Exception {return (ArrayList<Object>)get(g,"pieces");}
    @SuppressWarnings("unchecked") private static ArrayList<Point> points(Object p) throws Exception {return (ArrayList<Point>)get(p,"pts");}
    private static boolean clear(ArrowGameView g,Object p) throws Exception {return (Boolean)call(g,"isClear",new Class[]{p.getClass()},p);}

    @Test public void level2MarkedArrowIsClearAfterItsTwoVisibleBlockersAreGone() throws Exception {onMain(()->{
        context().getSharedPreferences("arrow_puzzle_faithful",0).edit().clear().commit();
        context().getSharedPreferences("arrow_escape_pro",0).edit().clear().commit();
        ArrowGameView g=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        try {
            set(g,"tutorial",false);
            call(g,"startLevel",new Class[]{int.class},2);
            ArrayList<Object> all=pieces(g);
            assertEquals(24,all.size());
            Object blockerA=all.get(9), blockerB=all.get(14), target=all.get(22);
            set(blockerA,"removed",true); set(blockerB,"removed",true);
            assertTrue("The marked right-side up arrow must be clear after pieces 9 and 14 are removed",clear(g,target));
            int hearts=(Integer)get(g,"hearts");
            call(g,"tapPiece",new Class[]{target.getClass()},target);
            assertEquals("A valid escape must never cost a heart",hearts,get(g,"hearts"));
            assertTrue("The intended arrow must start moving",(Boolean)get(target,"moving"));
        } finally { g.release(); }
    });}

    @Test public void actionUpDriftCannotRetargetToNeighbouringBlockedArrow() throws Exception {onMain(()->{
        context().getSharedPreferences("arrow_puzzle_faithful",0).edit().clear().commit();
        context().getSharedPreferences("arrow_escape_pro",0).edit().clear().commit();
        ArrowGameView g=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        try {
            set(g,"tutorial",false);
            call(g,"startLevel",new Class[]{int.class},2);
            ArrayList<Object> all=pieces(g);
            set(all.get(9),"removed",true); set(all.get(14),"removed",true);
            Object target=all.get(22), neighbour=all.get(5);
            assertTrue(clear(g,target)); assertFalse(clear(g,neighbour));

            // Match the screenshot's grid geometry. ACTION_DOWN is directly on the
            // clear arrow. ACTION_UP drifts one grid cell left, where old code could
            // re-hit-test to a blocked neighbour and wrongly remove a heart.
            set(g,"cell",30f); set(g,"boardLeft",70f); set(g,"boardTop",360f);
            Point tip=points(target).get(points(target).size()-1);
            float downX=70f+tip.x*30f, downY=360f+(tip.y+1f)*30f;
            float upX=downX-30f, upY=downY;
            long now=android.os.SystemClock.uptimeMillis();
            g.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,downX,downY,0));
            g.onTouchEvent(MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,upX,upY,0));
            assertEquals("Finger drift must not charge a heart for a different arrow",3,get(g,"hearts"));
            assertTrue("ACTION_UP must use the arrow captured on ACTION_DOWN",(Boolean)get(target,"moving"));
            assertFalse("Blocked neighbour must not be selected",(Boolean)get(neighbour,"moving"));
        } finally { g.release(); }
    });}

    @Test public void movingBlockersAlreadyEscapingCannotChargeHeart() throws Exception {onMain(()->{
        context().getSharedPreferences("arrow_puzzle_faithful",0).edit().clear().commit();
        context().getSharedPreferences("arrow_escape_pro",0).edit().clear().commit();
        ArrowGameView g=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        try {
            set(g,"tutorial",false);
            call(g,"startLevel",new Class[]{int.class},2);
            ArrayList<Object> all=pieces(g);
            Object blockerA=all.get(9), blockerB=all.get(14), target=all.get(22);

            // Match real gameplay: moving arrows retain their full snake body until
            // their animated tail actually vacates a cell.
            int stepsA=(Integer)call(g,"snakeTravelSteps",new Class[]{blockerA.getClass()},blockerA);
            int stepsB=(Integer)call(g,"snakeTravelSteps",new Class[]{blockerB.getClass()},blockerB);
            set(blockerA,"moving",true); set(blockerA,"moveSteps",stepsA); set(blockerA,"moveT",0f);
            set(blockerB,"moving",true); set(blockerB,"moveSteps",stepsB); set(blockerB,"moveT",0f);
            assertFalse("Moving bodies must still block while visibly occupying the lane",clear(g,target));

            set(blockerA,"moveT",0.90f);
            set(blockerB,"moveT",0.90f);
            assertTrue("Once moving tails visibly clear the lane, the target must be allowed",clear(g,target));
            int hearts=(Integer)get(g,"hearts");
            call(g,"tapPiece",new Class[]{target.getClass()},target);
            assertEquals("A visually clear escape must never cost a heart because earlier arrows are still animating",hearts,get(g,"hearts"));
            assertTrue("The target arrow must start moving immediately",(Boolean)get(target,"moving"));
        } finally { g.release(); }
    });}
}
