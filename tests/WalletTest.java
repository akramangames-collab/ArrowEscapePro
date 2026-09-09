import com.arrowescape.pro.Wallet;
public class WalletTest {
    static class Storage implements Wallet.Storage {
        Wallet.State state = new Wallet.State(); boolean fail;
        public Wallet.State load(){return state.copy();}
        public boolean save(Wallet.State next){if(fail)return false;state=next.copy();return true;}
    }
    static int assertions;
    static void eq(long expected,long actual){assertions++;if(expected!=actual)throw new AssertionError("Expected "+expected+" but got "+actual);}
    static void yes(boolean value){eq(1,value?1:0);}
    public static void main(String[] args) {
        Storage s=new Storage();Wallet w=new Wallet(s);eq(100,w.balance());
        yes(w.spend(25));eq(75,new Wallet(s).balance());
        eq(25,w.rewardLevel("run1",1,3));eq(0,w.rewardLevel("run1",1,3));
        eq(90,w.rewardLevel("run5",5,1));eq(100,w.rewardLevel("run10",10,3));
        eq(175,w.rewardLevel("boss25",25,3));eq(465,w.balance());
        eq(100,w.claimDaily(10*Wallet.DAY_MS));eq(0,new Wallet(s).claimDaily(10*Wallet.DAY_MS));
        eq(100,w.claimDaily(11*Wallet.DAY_MS));eq(2,w.streak());
        yes(w.canRewardDailyChallenge(13));eq(150,w.rewardDailyChallenge(13,3));eq(0,new Wallet(s).rewardDailyChallenge(13,3));
        eq(125,w.rewardDailyChallenge(14,2));
        yes(w.canRewardWeeklyChallenge(7));eq(350,w.rewardWeeklyChallenge(7,3));eq(0,new Wallet(s).rewardWeeklyChallenge(7,3));
        eq(300,w.rewardWeeklyChallenge(8,2));eq(0,w.rewardWeeklyChallenge(8,2));
        eq(25,w.rewardTreasure(10));eq(0,new Wallet(s).rewardTreasure(10));
        eq(50,w.rewardTreasure(25));eq(0,w.rewardTreasure(25));
        eq(0,w.rewardTreasure(11));
        eq(75,w.rewardAd("ad1"));eq(0,new Wallet(s).rewardAd("ad1"));
        s.fail=true;int balance=w.balance();eq(0,w.rewardAd("failed"));eq(balance,w.balance());s.fail=false;eq(75,w.rewardAd("failed"));
        Storage legacy=new Storage();legacy.state.coins=460;legacy.state.dailyDay=20;legacy.state.streak=4;
        Wallet migrated=new Wallet(legacy);eq(460,migrated.balance());eq(100,migrated.claimDaily(21*Wallet.DAY_MS));eq(5,migrated.streak());
        System.out.println("Wallet: "+assertions+" assertions passed.");
    }
}
