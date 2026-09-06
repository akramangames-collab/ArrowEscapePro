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
        eq(0,w.spend(76)?1:0);eq(0,w.spend(-10)?1:0);eq(75,w.balance());
        eq(20,w.rewardLevel("run1",1,true));eq(0,w.rewardLevel("run1",1,true));
        eq(45,w.rewardLevel("run5",5,false));eq(50,w.rewardLevel("run10",10,true));
        Wallet restored=new Wallet(s);eq(0,restored.rewardLevel("run10",10,true));eq(190,restored.balance());
        eq(100,w.claimDaily(10*Wallet.DAY_MS));eq(0,restored.claimDaily(10*Wallet.DAY_MS));
        eq(0,w.claimDaily(9*Wallet.DAY_MS));eq(100,w.claimDaily(11*Wallet.DAY_MS));eq(2,w.streak());
        eq(100,w.claimDaily(13*Wallet.DAY_MS));eq(1,w.streak());
        eq(75,w.rewardAd("ad1"));eq(0,new Wallet(s).rewardAd("ad1"));
        eq(0,w.rewardAd(""));eq(0,w.rewardLevel("invalid",201,true));
        s.fail=true;int balance=w.balance();eq(0,w.rewardAd("failed-ad"));eq(balance,w.balance());eq(0,w.spend(25)?1:0);eq(balance,w.balance());
        s.fail=false;eq(75,w.rewardAd("failed-ad"));
        Storage legacy=new Storage();legacy.state.coins=460;legacy.state.dailyDay=20;legacy.state.streak=4;
        Wallet migrated=new Wallet(legacy);eq(460,migrated.balance());eq(0,migrated.claimDaily(20*Wallet.DAY_MS));eq(100,migrated.claimDaily(21*Wallet.DAY_MS));eq(5,migrated.streak());eq(560,new Wallet(legacy).balance());
        System.out.println("Wallet: "+assertions+" assertions passed (persistence, migration, double callbacks, spending, daily clock rollback and storage failure).");
    }
}
