package com.arrowescape.pro;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=ArrowApplication.class)
public class ChapterRewardRuntimeTest {
    @Test public void legacyCampaignStarsUnlockGiftOnlyAfterAllFortyClears() {
        Context c=RuntimeEnvironment.getApplication();
        var stars=c.getSharedPreferences("arrow_puzzle_faithful",Context.MODE_PRIVATE);
        var edit=stars.edit();
        // Chapter 25 tests the highest receipt bit and exact 961–1000 boundary.
        for(int level=961;level<1000;level++)edit.putInt("stars_"+level,1);
        assertTrue(edit.commit());
        Wallet wallet=new Wallet(new PreferenceWalletStorage(c));
        int before=wallet.balance();
        assertEquals(0,wallet.claimChapterReward(25));
        assertTrue(stars.edit().putInt("stars_1000",3).commit());
        assertEquals(Wallet.CHAPTER_REWARD,wallet.claimChapterReward(25));
        Wallet recreated=new Wallet(new PreferenceWalletStorage(c));
        assertEquals(before+Wallet.CHAPTER_REWARD,recreated.balance());
        assertTrue(recreated.chapterRewardClaimed(25));
        assertEquals(0,recreated.claimChapterReward(25));
        assertEquals(0,recreated.claimChapterReward(24));
    }
}
