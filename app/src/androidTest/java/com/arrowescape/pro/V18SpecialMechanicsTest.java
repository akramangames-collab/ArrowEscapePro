package com.arrowescape.pro;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V18SpecialMechanicsTest {
    private interface Checked { void run() throws Exception; }
    private static void onMain(Checked action) throws Exception {
        Throwable[] error={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try { action.run(); } catch(Throwable t) { error[0]=t; }
        });
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static Object get(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    private static void set(Object o,String key,Object value)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
    private static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    private static class Host implements ArrowGameView.Host {
        public void onLevelCompleted(){} public void requestRewardedErase(){} public void requestRewardedRevive(){}
        public void requestRewardedCoins(){} public void openWallet(){} public void openSettings(){}
        public void onContinueAfterWin(Runnable p){p.run();}
    }
    @SuppressWarnings("unchecked") private static ArrayList<Object> pieces(ArrowGameView g)throws Exception{return (ArrayList<Object>)get(g,"pieces");}
    @SuppressWarnings("unchecked") private static ArrayList<Object> blockers(ArrowGameView g,Object p)throws Exception{
        return (ArrayList<Object>)call(g,"physicalBlockers",new Class[]{p.getClass()},p);
    }
    private static boolean containsId(ArrayList<Object> list,int id)throws Exception{
        for(Object q:list)if((Integer)get(q,"id")==id)return true;return false;
    }

    @Test public void specialsStayOnRealDependencyEdgesAcrossAll200Levels() throws Exception {onMain(()->{
        context().getSharedPreferences("arrow_puzzle_faithful",0).edit().clear().commit();
        context().getSharedPreferences("arrow_escape_pro",0).edit().clear().commit();
        ArrowGameView g=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        g.setPaused(true);set(g,"tutorial",false);
        int keys=0,locks=0,frozen=0,switches=0,gates=0,links=0,linked=0,treasures=0;
        try{
            for(int lv=1;lv<=200;lv++){
                call(g,"startLevel",new Class[]{int.class},lv);
                int levelTreasure=0;
                for(Object p:pieces(g)){
                    int type=(Integer)get(p,"specialType");
                    boolean treasure=(Boolean)get(p,"treasure");
                    if(treasure){treasures++;levelTreasure++;assertEquals("Treasure must not hide a special badge at level "+lv,0,type);}
                    if(type==1)keys++;else if(type==2)locks++;else if(type==3)frozen++;else if(type==4)switches++;else if(type==5)gates++;else if(type==6)links++;else if(type==7)linked++;
                    if(type==2||type==3||type==5||type==7){
                        ArrayList<Object> physical=blockers(g,p);
                        int a=(Integer)get(p,"prereqA");
                        assertTrue("Special prerequisite A must already block level "+lv,containsId(physical,a));
                        if(type==3){
                            int b=(Integer)get(p,"prereqB");
                            assertTrue("Frozen prerequisite B must already block level "+lv,containsId(physical,b));
                            assertNotEquals(a,b);
                        }
                    }
                }
                assertEquals("Exactly one treasure on each milestone level",lv%5==0?1:0,levelTreasure);
            }
            assertTrue("Keys should appear after level 40",keys>0&&locks>0);
            assertTrue("Frozen arrows should appear in the later game",frozen>0);
            assertTrue("Switches and gates should appear in the later game",switches>0&&gates>0);
            assertTrue("Linked pairs should appear in the final chapters",links>0&&linked>0);
            assertEquals(40,treasures);
        }finally{g.release();}
    });}

    @Test public void treasureCanOnlyPayOncePerMilestoneLevel() throws Exception {
        Wallet.State state=new Wallet.State();
        class Storage implements Wallet.Storage{
            Wallet.State s=state.copy();
            public Wallet.State load(){return s.copy();}
            public boolean save(Wallet.State n){s=n.copy();return true;}
        }
        Wallet w=new Wallet(new Storage());
        int start=w.balance();
        assertEquals(25,w.rewardTreasure(10));
        assertEquals(start+25,w.balance());
        assertEquals(0,w.rewardTreasure(10));
        assertEquals(50,w.rewardTreasure(50));
        assertEquals(0,w.rewardTreasure(11));
    }
}
