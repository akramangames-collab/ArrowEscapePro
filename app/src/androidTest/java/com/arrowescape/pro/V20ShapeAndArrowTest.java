package com.arrowescape.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V20ShapeAndArrowTest {
    private interface Checked { void run() throws Exception; }
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static void onMain(Checked action) throws Exception {
        Throwable[] error={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try { action.run(); } catch(Throwable t) { error[0]=t; }
        });
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private static Object get(Object o,String key)throws Exception{
        Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);
    }
    private static void set(Object o,String key,Object value)throws Exception{
        Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);
    }
    private static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{
        Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);
    }
    private static class Host implements ArrowGameView.Host {
        public void onLevelCompleted(){} public void requestRewardedErase(){} public void requestRewardedRevive(){}
        public void requestRewardedCoins(){} public void openWallet(){} public void openSettings(){}
        public void onContinueAfterWin(Runnable p){p.run();}
    }

    @Test public void campaignCyclesLettersNumbersAndSpecialShapes() throws Exception { onMain(()->{
        SharedPreferences gamePrefs=context().getSharedPreferences("arrow_puzzle_faithful",0);
        SharedPreferences appPrefs=context().getSharedPreferences("arrow_escape_pro",0);
        gamePrefs.edit().clear().putBoolean("tutorialSeen",true).commit();
        appPrefs.edit().clear().commit();
        ArrowGameView game=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        game.setPaused(true);
        try{
            assertEquals("A",game.shapeNameForLevel(1));
            assertEquals("Z",game.shapeNameForLevel(26));
            assertEquals("0",game.shapeNameForLevel(27));
            assertEquals("9",game.shapeNameForLevel(36));
            assertEquals("Heart",game.shapeNameForLevel(37));
            assertEquals("Flower",game.shapeNameForLevel(46));
            assertEquals("A",game.shapeNameForLevel(47));

            for(int lv=1;lv<=46;lv++){
                call(game,"startLevel",new Class[]{int.class},lv);
                call(game,"ensureShapeMask",new Class[]{});
                @SuppressWarnings("unchecked")
                ArrayList<float[]> rows=(ArrayList<float[]>)get(game,"shapeMaskIntervals");
                int drawable=0;
                for(float[] row:rows)if(row.length>=2)drawable++;
                assertTrue("Shape mask must have drawable rows for level "+lv+" ("+game.shapeNameForLevel(lv)+")",drawable>=18);
                Path path=(Path)get(game,"normalizedShapePath");
                assertFalse("Shape outline must not be empty at level "+lv,path.isEmpty());
            }
        }finally{game.release();}
    });}

    @Test public void premiumThemeUnlockSpendsCoinsAndPersists() throws Exception { onMain(()->{
        SharedPreferences gamePrefs=context().getSharedPreferences("arrow_puzzle_faithful",0);
        SharedPreferences appPrefs=context().getSharedPreferences("arrow_escape_pro",0);
        gamePrefs.edit().clear().putBoolean("tutorialSeen",true).commit();
        appPrefs.edit().clear()
            .putBoolean("wallet_v16_ready",true)
            .putInt("coins",1500)
            .putInt("wallet_theme_mask",1)
            .commit();

        Wallet wallet=new Wallet(new PreferenceWalletStorage(context()));
        ArrowGameView game=new ArrowGameView(context(),new Host(),wallet);
        game.setPaused(true);
        assertFalse(game.isBoardThemeOwned(5));
        assertEquals(650,game.boardThemePrice(5));
        assertEquals("Ember sparks",game.boardThemeFeature(5));
        assertTrue(game.unlockAndSelectBoardTheme(5));
        assertEquals("Lava",game.currentBoardTheme());
        assertEquals(850,wallet.balance());
        game.release();

        Wallet restoredWallet=new Wallet(new PreferenceWalletStorage(context()));
        ArrowGameView restored=new ArrowGameView(context(),new Host(),restoredWallet);
        restored.setPaused(true);
        try{
            assertTrue(restored.isBoardThemeOwned(5));
            assertEquals("Lava",restored.currentBoardTheme());
            assertTrue(restored.unlockAndSelectBoardTheme(5));
            assertEquals(850,restoredWallet.balance());
        }finally{restored.release();}
    });}

    @Test public void fiveFastCorrectMovesEarnFlowShieldAndItSavesAHeart() throws Exception { onMain(()->{
        SharedPreferences gamePrefs=context().getSharedPreferences("arrow_puzzle_faithful",0);
        SharedPreferences appPrefs=context().getSharedPreferences("arrow_escape_pro",0);
        gamePrefs.edit().clear().putBoolean("tutorialSeen",true).commit();
        appPrefs.edit().clear().commit();
        ArrowGameView game=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        game.setPaused(true);
        try{
            for(int step=0;step<5;step++){
                @SuppressWarnings("unchecked")
                ArrayList<Object> pieces=(ArrayList<Object>)get(game,"pieces");
                Object safe=null;
                for(Object p:pieces){
                    if((Boolean)get(p,"removed")||(Boolean)get(p,"moving"))continue;
                    boolean clear=(Boolean)call(game,"isClear",new Class[]{p.getClass()},p);
                    if(clear){safe=p;break;}
                }
                assertNotNull("Need a safe arrow for flow step "+step,safe);
                call(game,"tapPiece",new Class[]{safe.getClass()},safe);
                set(safe,"moving",false);set(safe,"removed",true);
            }
            assertTrue((Boolean)get(game,"flowShield"));
            assertTrue((Integer)get(game,"combo")>=5);
            int hearts=(Integer)get(game,"hearts");

            @SuppressWarnings("unchecked")
            ArrayList<Object> pieces=(ArrayList<Object>)get(game,"pieces");
            Object blocked=null;
            for(Object p:pieces){
                if((Boolean)get(p,"removed")||(Boolean)get(p,"moving"))continue;
                boolean clear=(Boolean)call(game,"isClear",new Class[]{p.getClass()},p);
                if(!clear){blocked=p;break;}
            }
            assertNotNull("Need a blocked arrow to consume Flow Shield",blocked);
            call(game,"tapPiece",new Class[]{blocked.getClass()},blocked);
            assertEquals("Flow Shield must protect one heart",hearts,(int)get(game,"hearts"));
            assertFalse((Boolean)get(game,"flowShield"));
            assertEquals(0,(int)get(game,"combo"));
        }finally{game.release();}
    });}

    @Test public void premiumArrowUnlockSpendsCoinsAndPersists() throws Exception { onMain(()->{
        SharedPreferences gamePrefs=context().getSharedPreferences("arrow_puzzle_faithful",0);
        SharedPreferences appPrefs=context().getSharedPreferences("arrow_escape_pro",0);
        gamePrefs.edit().clear().putBoolean("tutorialSeen",true).commit();
        appPrefs.edit().clear()
            .putBoolean("wallet_v16_ready",true)
            .putInt("coins",2000)
            .putInt("wallet_arrow_type_mask",1)
            .commit();

        Wallet wallet=new Wallet(new PreferenceWalletStorage(context()));
        ArrowGameView game=new ArrowGameView(context(),new Host(),wallet);
        game.setPaused(true);
        assertFalse(game.isArrowTypeOwned(4));
        assertEquals(800,game.arrowTypePrice(4));
        assertEquals("Glow + sparks",game.arrowTypeFeature(4));
        assertTrue(game.unlockAndSelectArrowType(4));
        assertTrue(game.isArrowTypeOwned(4));
        assertEquals("Neon",game.currentArrowType());
        assertEquals(1200,wallet.balance());
        game.release();

        Wallet restoredWallet=new Wallet(new PreferenceWalletStorage(context()));
        ArrowGameView restored=new ArrowGameView(context(),new Host(),restoredWallet);
        restored.setPaused(true);
        try{
            assertTrue(restored.isArrowTypeOwned(4));
            assertEquals("Neon",restored.currentArrowType());
            assertEquals(1200,restoredWallet.balance());
            assertTrue(restored.unlockAndSelectArrowType(4));
            assertEquals("Selecting an owned arrow must not spend twice",1200,restoredWallet.balance());
        }finally{restored.release();}
    });}
}
