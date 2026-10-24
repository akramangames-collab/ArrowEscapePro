import com.arrowescape.pro.CampaignRules;
import com.arrowescape.pro.BoosterGeometry;
import com.arrowescape.pro.Wallet;

public class CampaignBoosterTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if(!ok)throw new AssertionError("Check "+checks); }
    public static void main(String[] args) {
        int[] counts=new int[4];
        for(int lv=1;lv<=400;lv++)counts[CampaignRules.tier(lv)]++;
        check(counts[0]==334&&counts[1]==26&&counts[2]==27&&counts[3]==13);
        check(CampaignRules.tier(6)==1&&CampaignRules.tier(12)==2);
        check(CampaignRules.tier(30)==3&&CampaignRules.tier(60)==3);
        check(CampaignRules.tier(390)==3&&CampaignRules.tier(396)==2&&CampaignRules.tier(400)==0);
        check(BoosterGeometry.circleTouchesSegment(5,5,4,0,5,10,5));
        check(BoosterGeometry.circleTouchesSegment(5,5,4,1,0,1,10)); // tangent
        check(!BoosterGeometry.circleTouchesSegment(5,5,4,0,0,0,10));
        check(BoosterGeometry.circleTouchesSegment(0,0,4,4,0,4,0)); // endpoint
        check(!BoosterGeometry.circleTouchesSegment(0,0,4,5,0,5,0));
        check(BoosterGeometry.rowTouchesSegment(5,.5f,0,10));
        check(BoosterGeometry.rowTouchesSegment(5,.5f,5.5f,5.5f));
        check(!BoosterGeometry.rowTouchesSegment(5,.5f,6,9));
        WalletTest.Storage s=new WalletTest.Storage();Wallet w=new Wallet(s);
        check(w.rewardLevel("last",400,3)==25);
        check(w.rewardLevel("last",400,3)==0); // duplicate callback
        check(w.rewardLevel("invalid",1001,3)==0);
        check(w.rewardLevel("extreme",390,3)==175);
        check(w.rewardLevel("hard",6,3)==60);
        check(w.rewardLevel("super",12,3)==100);
        int balance=w.balance();s.fail=true;
        check(!w.spend(60)&&w.balance()==balance); // failed persistence cannot spend
        System.out.println("Campaign/booster geometry: "+checks+" checks passed.");
    }
}
