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
    static void no(boolean value){eq(0,value?1:0);}
    public static void main(String[] args) {
        Storage s=new Storage();Wallet w=new Wallet(s);eq(100,w.balance());
        yes(w.spend(25));eq(75,new Wallet(s).balance());
        yes(w.ownsArrowType(0));no(w.ownsArrowType(1));
        no(w.purchaseArrowType(1,200));eq(75,w.balance());
        eq(25,w.rewardLevel("run1",1,3));eq(0,w.rewardLevel("run1",1,3));
        eq(90,w.rewardLevel("run5",5,1));eq(100,w.rewardLevel("run10",10,3));
        eq(175,w.rewardLevel("boss25",25,3));eq(465,w.balance());

        eq(10,w.nextDailyReward(10*Wallet.DAY_MS));
        eq(10,w.claimDaily(10*Wallet.DAY_MS));eq(0,new Wallet(s).claimDaily(10*Wallet.DAY_MS));eq(1,w.streak());
        eq(15,w.nextDailyReward(11*Wallet.DAY_MS));eq(15,w.claimDaily(11*Wallet.DAY_MS));eq(2,w.streak());
        eq(20,w.claimDaily(12*Wallet.DAY_MS));eq(3,w.streak());
        eq(30,w.claimDaily(13*Wallet.DAY_MS));eq(4,w.streak());
        eq(40,w.claimDaily(14*Wallet.DAY_MS));eq(5,w.streak());
        eq(50,w.claimDaily(15*Wallet.DAY_MS));eq(6,w.streak());
        eq(75,w.claimDaily(16*Wallet.DAY_MS));eq(7,w.streak());
        eq(75,w.claimDaily(17*Wallet.DAY_MS));eq(8,w.streak());

        yes(w.canRewardDailyChallenge(18));eq(200,w.rewardDailyChallenge(18,3));eq(0,new Wallet(s).rewardDailyChallenge(18,2));
        eq(200,w.rewardDailyChallenge(19,1));
        yes(w.canRewardWeeklyChallenge(7));eq(350,w.rewardWeeklyChallenge(7,3));eq(0,new Wallet(s).rewardWeeklyChallenge(7,3));
        eq(300,w.rewardWeeklyChallenge(8,2));eq(0,w.rewardWeeklyChallenge(8,2));
        eq(75,w.rewardAd("ad1"));eq(0,new Wallet(s).rewardAd("ad1"));
        yes(w.purchaseArrowType(1,200));yes(w.ownsArrowType(1));
        int afterSlim=w.balance();yes(w.purchaseArrowType(1,200));eq(afterSlim,w.balance());
        yes(new Wallet(s).ownsArrowType(1));
        no(w.ownsTheme(5));
        int beforeTheme=w.balance();
        yes(w.purchaseTheme(5,650));yes(w.ownsTheme(5));eq(beforeTheme-650,w.balance());
        int afterTheme=w.balance();yes(w.purchaseTheme(5,650));eq(afterTheme,w.balance());
        s.fail=true;int balance=w.balance();eq(0,w.rewardAd("failed"));eq(balance,w.balance());s.fail=false;eq(75,w.rewardAd("failed"));

        Storage legacy=new Storage();legacy.state.coins=460;legacy.state.dailyDay=20;legacy.state.streak=4;
        Wallet migrated=new Wallet(legacy);eq(460,migrated.balance());eq(40,migrated.claimDaily(21*Wallet.DAY_MS));eq(5,migrated.streak());
        System.out.println("Wallet: "+assertions+" assertions passed.");
    }
}
