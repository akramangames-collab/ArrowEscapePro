import com.arrowescape.pro.*;
import java.time.*;
public class PremiumCoreTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Store implements Wallet.Storage{Wallet.State s=new Wallet.State();public Wallet.State load(){return s.copy();}public boolean save(Wallet.State n){s=n.copy();return true;}}
 public static void main(String[] args){
  ZoneId india=ZoneId.of("Asia/Kolkata");long t=Instant.parse("2026-10-25T19:00:00Z").toEpochMilli();
  check(GameDay.day(t,india)==LocalDate.of(2026,10,26).toEpochDay());
  ZoneId ny=ZoneId.of("America/New_York");long start=ZonedDateTime.of(2026,3,8,0,0,0,0,ny).toInstant().toEpochMilli();
  check(GameDay.nextMidnight(start,ny)-start==23*3600000L);
  Store store=new Store();Wallet w=new Wallet(store);check(w.rewardLevel("a",1,3)==25);check(w.rewardLevel("b",2,3)==25);check(new Wallet(store).rewardLevel("different-run",1,3)==0);
  check(w.rewardDailyChallenge(42,3)==200);check(new Wallet(store).rewardDailyChallenge(42,3)==0);check(w.rewardDailyChallenge(41,3)==0);
  for(int i=1;i<=1000;i++)check(RescueHazard.forLevel(i)!=null);
  System.out.println("PASS: local midnight, daylight-saving, replay receipts, daily rollback, 1000 hazard assignments");
 }
}
