package com.arrowescape.pro;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V19ProgressionTest {
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private interface Checked { void run() throws Exception; }
    private static void onMain(Checked action) throws Exception {
        Throwable[] error={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try { action.run(); } catch(Throwable t) { error[0]=t; }
        });
        if(error[0]!=null) throw new AssertionError(error[0]);
    }
    private static Object get(Object o,String key)throws Exception{
        Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);
    }
    private static class Host implements ArrowGameView.Host {
        public void onLevelCompleted(){} public void requestRewardedErase(){} public void requestRewardedRevive(){}
        public void requestRewardedCoins(){} public void openWallet(){} public void openSettings(){}
        public void onContinueAfterWin(Runnable p){p.run();}
    }

    @Test public void weeklyChallengeIsDeterministicAndDoesNotMoveCampaignProgress() throws Exception { onMain(()->{
        SharedPreferences gamePrefs=context().getSharedPreferences("arrow_puzzle_faithful",0);
        SharedPreferences appPrefs=context().getSharedPreferences("arrow_escape_pro",0);
        gamePrefs.edit().clear().putBoolean("tutorialSeen",true).putInt("lastLevel",17).putInt("maxUnlocked",17).commit();
        appPrefs.edit().clear().commit();

        ArrowGameView first=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        first.setPaused(true);
        first.startWeeklyChallenge();
        int weeklyLevel=(Integer)get(first,"level");
        assertTrue((Boolean)get(first,"weeklyChallenge"));
        assertFalse((Boolean)get(first,"dailyChallenge"));
        assertTrue(weeklyLevel>=80&&weeklyLevel<=200&&weeklyLevel%5==0);
        assertEquals(17,gamePrefs.getInt("lastLevel",-1));
        assertEquals(17,gamePrefs.getInt("maxUnlocked",-1));

        first.restartCurrentLevel();
        assertEquals(weeklyLevel,(int)get(first,"level"));
        assertTrue((Boolean)get(first,"weeklyChallenge"));
        first.handleBack();
        assertFalse((Boolean)get(first,"weeklyChallenge"));
        assertEquals(17,gamePrefs.getInt("lastLevel",-1));
        first.release();

        ArrowGameView second=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        second.setPaused(true);
        second.startWeeklyChallenge();
        assertEquals("Same UTC week must select the same puzzle",weeklyLevel,(int)get(second,"level"));
        second.release();
    });}

    @Test public void progressionHallBuildsChapterTrophiesFromSavedStars() throws Exception { onMain(()->{
        SharedPreferences gamePrefs=context().getSharedPreferences("arrow_puzzle_faithful",0);
        SharedPreferences appPrefs=context().getSharedPreferences("arrow_escape_pro",0);
        gamePrefs.edit().clear().putBoolean("tutorialSeen",true).putInt("lastLevel",21).putInt("maxUnlocked",21).commit();
        appPrefs.edit().clear().commit();
        SharedPreferences.Editor edit=gamePrefs.edit();
        for(int lv=1;lv<=20;lv++) edit.putInt("stars_"+lv,3);
        edit.commit();

        ArrowGameView game=new ArrowGameView(context(),new Host(),new Wallet(new PreferenceWalletStorage(context())));
        game.setPaused(true);
        assertEquals(1,game.trophyCount());
        assertTrue(game.achievementCount()>=3);
        String summary=game.progressSummary();
        String details=game.progressDetails();
        assertTrue(summary.contains("60/600★"));
        assertTrue(summary.contains("1/8 chapter trophies"));
        assertTrue(details.contains("CH 1 · First Escape — GOLD TROPHY"));
        assertTrue(details.contains("✓ First Escape"));
        assertTrue(details.contains("✓ Perfect Start"));
        game.release();
    });}
}
