package com.arrowescape.pro;

import org.junit.Test;
import static org.junit.Assert.*;

public class CageRescueTest {
    @Test public void onlyMarkedArrowStartsTheBoxKeyDoorSequence(){
        CageRescue c=new CageRescue();c.reset(7);
        c.arrowRemoved(2,false);assertFalse(c.boxOpened());
        c.arrowRemoved(7,false);assertTrue(c.boxOpened());assertFalse(c.cageOpened());
        c.advance(.20f,false);assertTrue(c.lidProgress()>0);assertEquals(0,c.keyProgress(),0);
        c.advance(.40f,false);assertEquals(1,c.lidProgress(),0);assertTrue(c.keyProgress()>0);assertEquals(0,c.doorProgress(),0);
        c.arrowRemoved(7,false); // The trigger cannot reset/restart the flight.
        c.advance(.55f,false);assertTrue(c.doorProgress()>0);assertFalse(c.cageOpened());
        c.advance(.60f,false);assertTrue(c.cageOpened());assertTrue(c.animating());
        c.advance(.70f,false);assertFalse(c.animating());assertTrue(c.readyToFinish());
    }
    @Test public void restartRelocksWhileRestoreAndReducedMotionStayConsistent(){
        CageRescue c=new CageRescue();c.reset(4);c.arrowRemoved(4,true);assertTrue(c.cageOpened());
        c.reset(8);assertFalse(c.boxOpened());assertFalse(c.cageOpened());
        c.restore(true);assertTrue(c.cageOpened());c.restore(false);assertFalse(c.boxOpened());
        c.arrowRemoved(8,false);c.advance(-10,false);assertEquals(0,c.lidProgress(),0);
        c.advance(0,true);assertTrue(c.cageOpened());
        c.reset(-1);c.arrowRemoved(-1,true);assertFalse(c.boxOpened());
    }
}
