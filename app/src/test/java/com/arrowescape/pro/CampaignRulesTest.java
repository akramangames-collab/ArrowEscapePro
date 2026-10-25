package com.arrowescape.pro;
import org.junit.Test;
import static org.junit.Assert.*;
public class CampaignRulesTest {
 @Test public void chaptersCoverEntireCampaignWithoutGaps() {
   assertEquals(50,CampaignRules.CHAPTER_COUNT);
   assertEquals(1,CampaignRules.chapter(1));assertEquals(1,CampaignRules.chapter(20));
   assertEquals(2,CampaignRules.chapter(21));assertEquals(50,CampaignRules.chapter(1000));
   for(int ch=1;ch<=50;ch++){
     assertEquals((ch-1)*20+1,CampaignRules.chapterStart(ch));
     assertEquals(ch*20,CampaignRules.chapterEnd(ch));
     assertEquals(ch,CampaignRules.chapter(CampaignRules.chapterStart(ch)));
     assertFalse(CampaignRules.chapterName(CampaignRules.chapterStart(ch)).isEmpty());
   }
 }
 @Test public void waterRunsFasterPerArrowInLaterChapters() {
   assertEquals(120,CampaignRules.waterSeconds(1,3));
   assertEquals(120,CampaignRules.waterSeconds(5,7));
   assertEquals(79,CampaignRules.waterSeconds(6,31));
   assertTrue(CampaignRules.waterSeconds(40,40)>CampaignRules.waterSeconds(41,40));
   assertTrue(CampaignRules.waterSeconds(160,40)>CampaignRules.waterSeconds(161,40));
   assertTrue(CampaignRules.waterSeconds(400,40)>CampaignRules.waterSeconds(401,40));
   assertTrue(CampaignRules.waterSeconds(700,40)>CampaignRules.waterSeconds(701,40));
   assertEquals(158,CampaignRules.waterSeconds(1000,100));
 }
}
