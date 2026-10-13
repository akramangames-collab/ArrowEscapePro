package com.arrowescape.pro;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class BoosterInventoryRuntimeTest {
    private SharedPreferences prefs;
    @Before public void reset(){prefs=RuntimeEnvironment.getApplication().getSharedPreferences("boost_test",Context.MODE_PRIVATE);prefs.edit().clear().commit();}
    @Test public void adReceiptGrantsExactlyOneAndStockSurvivesRecreation(){
        BoosterInventory inventory=new BoosterInventory(prefs);
        assertTrue(inventory.rewardAd(2,"receipt"));assertFalse(inventory.rewardAd(2,"receipt"));
        inventory=new BoosterInventory(prefs);assertEquals(1,inventory.count(2));
        assertTrue(inventory.consume(2));assertFalse(inventory.consume(2));assertEquals(0,inventory.count(2));
        assertFalse(inventory.rewardAd(2,"receipt"));
    }
    @Test public void allThreeNewRewardedBoostersPersistAndRequireUniqueReceipts(){
        BoosterInventory inventory=new BoosterInventory(prefs);
        assertEquals(7,BoosterInventory.COUNT);
        assertEquals("Safe Scan",BoosterInventory.name(BoosterInventory.SCAN));
        assertEquals("Shield",BoosterInventory.name(BoosterInventory.SHIELD));
        assertEquals("Triple Escape",BoosterInventory.name(BoosterInventory.TRIPLE));
        for(int kind=BoosterInventory.SCAN;kind<=BoosterInventory.TRIPLE;kind++){
            String token="unique-"+kind;
            assertTrue(inventory.rewardAd(kind,token));
            assertFalse(inventory.rewardAd(kind,token));
            assertEquals(1,new BoosterInventory(prefs).count(kind));
            assertTrue(inventory.consume(kind));
            assertEquals(0,inventory.count(kind));
            assertFalse(inventory.consume(kind));
        }
        // Receipts are unique across all booster types, not just within a type.
        assertFalse(inventory.rewardAd(BoosterInventory.SCAN,"unique-5"));
        assertEquals(0,inventory.count(BoosterInventory.SCAN));
    }
    @Test public void newBoostersDoNotAlterExistingCheckpointGifts(){
        for(int level=1;level<=400;level++){
            int kind=BoosterInventory.giftKind(level);
            assertTrue(kind<BoosterInventory.SCAN);
        }
    }
    @Test public void sparseGiftsAreOncePerLevelAndExtremeClearGivesTwoBombs(){
        BoosterInventory inventory=new BoosterInventory(prefs);int checkpoints=0;
        for(int level=1;level<=400;level++){
            int gift=inventory.rewardLevel(level);assertEquals(BoosterInventory.giftAmount(level),gift);
            assertEquals(0,inventory.rewardLevel(level));if(gift>0)checkpoints++;
        }
        assertEquals(80,checkpoints);assertEquals(2,BoosterInventory.giftAmount(30));assertEquals(2,BoosterInventory.giftKind(30));
        assertEquals(0,BoosterInventory.giftAmount(6));assertEquals(0,BoosterInventory.giftAmount(401));
        assertEquals(inventory.count(2),new BoosterInventory(prefs).count(2));
    }
}
