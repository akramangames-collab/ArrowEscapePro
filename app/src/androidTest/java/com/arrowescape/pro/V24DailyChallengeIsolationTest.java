package com.arrowescape.pro;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class V24DailyChallengeIsolationTest {
    private interface Checked { void run() throws Exception; }
    private static void onMain(Checked action) throws Exception {
        Throwable[] error={null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try { action.run(); } catch(Throwable t) { error[0]=t; }
        });
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static ArrayList<String> assetLines(String name) throws Exception {
        ArrayList<String> out=new ArrayList<>();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(context().getAssets().open(name)))){
            String line;while((line=br.readLine())!=null)if(!line.trim().isEmpty())out.add(line.trim());
        }
        return out;
    }

    @Test public void dedicatedPackHas366UniquePuzzlesAndZeroCampaignOverlap() throws Exception {
        ArrayList<String> daily=assetLines("daily_challenges.txt");
        ArrayList<String> normal=assetLines("levels.txt");
        assertEquals(366,daily.size());
        assertEquals(366,new HashSet<>(daily).size());
        HashSet<String> campaign=new HashSet<>(normal);
        for(String puzzle:daily)assertFalse("Daily Challenge must never reuse a campaign level",campaign.contains(puzzle));
    }

    @Test public void dailyChallengeDoesNotChangeCampaignProgress() throws Exception {onMain(()->{
        Context c=context();
        c.getSharedPreferences("arrow_puzzle_faithful",0).edit().clear().putInt("lastLevel",37).putInt("maxUnlocked",37).putBoolean("tutorialSeen",true).commit();
        c.getSharedPreferences("arrow_escape_pro",0).edit().clear().commit();
        ArrowGameView g=new ArrowGameView(c,new ArrowGameView.Host(){
            public void onLevelCompleted(){} public void requestRewardedErase(){} public void requestRewardedRevive(){} public void requestRewardedCoins(){} public void openWallet(){} public void openSettings(){} public void onContinueAfterWin(Runnable p){p.run();}
        },new Wallet(new PreferenceWalletStorage(c)));
        try {
            assertEquals(37,g.currentLevelNumber());
            g.startDailyChallenge();
            int n=g.dailyPuzzleNumber();
            assertTrue(n>=1&&n<=366);
            assertEquals("Daily play must not overwrite the campaign checkpoint",37,c.getSharedPreferences("arrow_puzzle_faithful",0).getInt("lastLevel",-1));
            g.restartCurrentLevel();
            assertEquals(37,c.getSharedPreferences("arrow_puzzle_faithful",0).getInt("lastLevel",-1));
            g.handleBack();
            assertEquals("Leaving Daily Challenge returns to the campaign level",37,g.currentLevelNumber());
        } finally { g.release(); }
    });}
}
