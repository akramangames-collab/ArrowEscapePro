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
    @Test public void onlyScanAndTripleCanBeGrantedAndRetiredMistakeShieldIsBlocked(){
        BoosterInventory stock=new BoosterInventory(prefs);
        assertEquals(7,BoosterInventory.COUNT);
        assertEquals("Safe Scan",BoosterInventory.name(BoosterInventory.SCAN));
        assertEquals("Triple Escape",BoosterInventory.name(BoosterInventory.TRIPLE));
        assertFalse(BoosterInventory.available(BoosterInventory.RETIRED_MISTAKE_SHIELD));
        assertFalse(stock.rewardAd(BoosterInventory.RETIRED_MISTAKE_SHIELD,"shield-receipt"));
        assertFalse(stock.consume(BoosterInventory.RETIRED_MISTAKE_SHIELD));
        for(int kind:new int[]{BoosterInventory.SCAN,BoosterInventory.TRIPLE}){
            String receipt="test-"+kind;
            assertTrue(stock.rewardAd(kind,receipt));
            assertFalse(stock.rewardAd(kind,receipt));
            assertEquals(1,new BoosterInventory(prefs).count(kind));
            assertTrue(stock.consume(kind));
            assertEquals(0,stock.count(kind));
            assertFalse(stock.consume(kind));
        }
        // Old v46 stock in slot 6 remains valid; slot 5 is unreachable.
        prefs.edit().putInt("boost_stock_5",7).putInt("boost_stock_6",2).commit();
        assertFalse(stock.consume(5));
        assertEquals(7,stock.count(5));
        assertEquals(2,stock.count(6));
        assertTrue(stock.consume(6));
        assertEquals(1,stock.count(6));
    }
    @Test public void sparseGiftsAreOncePerLevelAndExtremeClearGivesTwoBombs(){
        BoosterInventory inventory=new BoosterInventory(prefs);int checkpoints=0;
        for(int level=1;level<=1000;level++){
            int gift=inventory.rewardLevel(level);assertEquals(BoosterInventory.giftAmount(level),gift);
            assertEquals(0,inventory.rewardLevel(level));if(gift>0)checkpoints++;
        }
        assertEquals(200,checkpoints);assertEquals(2,BoosterInventory.giftAmount(30));assertEquals(2,BoosterInventory.giftKind(30));
        assertEquals(0,BoosterInventory.giftAmount(6));assertEquals(0,BoosterInventory.giftAmount(1001));
        assertEquals(inventory.count(2),new BoosterInventory(prefs).count(2));
    }
}
