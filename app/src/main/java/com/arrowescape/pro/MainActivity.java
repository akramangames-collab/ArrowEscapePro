package com.arrowescape.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.*;
import com.google.android.gms.ads.rewarded.*;
import com.google.android.ump.*;
import java.util.UUID;

public class MainActivity extends Activity implements ArrowGameView.Host {
    private ArrowGameView game;
    private Wallet wallet;
    private SharedPreferences settings;
    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private ConsentInformation consent;
    private AdView banner;
    private AlertDialog menu;
    private TextView menuBalance, rewardStatus;
    private Button watchButton;
    private AlertDialog reminder;
    private int storeTab = 0, achievementTab = 0;
    private boolean adsStarted, loadingReward, loadingInterstitial, showingAd, pausedForAd;
    private long rewardedLoadedAt, interstitialLoadedAt;
    private int completedSinceAd;
    private long lastInterstitialAt = SystemClock.elapsedRealtime();

    private final int INK = 0xFF050B1E;
    private final int PANEL = 0xFF0D1E40;
    private final int PANEL_2 = 0xFF102A59;
    private final int CYAN = 0xFF17C7FF;
    private final int PURPLE = 0xFF8B5CFF;
    private final int TEXT = 0xFFF1F7FF;
    private final int MUTED = 0xFF9CB3D5;
    private final int GOLD = 0xFFFFC83D;
    private final int GREEN = 0xFF32D49B;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = getSharedPreferences("arrow_escape_pro", MODE_PRIVATE);
        wallet = new Wallet(new PreferenceWalletStorage(this));
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        game = new ArrowGameView(this, this, wallet);
        setContentView(game);
        game.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            showHome();
            maybeShowDailyChallengeReminder();
        }, 450L);
        consent = UserMessagingPlatform.getConsentInformation(this);
        if (BuildConfig.DEBUG) { startAdsIfAllowed(); return; }
        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(this, parameters,
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, error -> startAdsIfAllowed()),
            error -> startAdsIfAllowed());
        startAdsIfAllowed();
    }

    private void startAdsIfAllowed() {
        if (adsStarted || (!BuildConfig.DEBUG && !consent.canRequestAds()) || isDestroyed()) return;
        adsStarted = true;
        MobileAds.initialize(this, status -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            loadInterstitial(); loadRewarded();
        }));
    }
    private boolean canRequestAds() { return adsStarted && consent != null && (BuildConfig.DEBUG || consent.canRequestAds()) && !isDestroyed(); }
    private void loadInterstitial() {
        if (!canRequestAds() || loadingInterstitial || interstitial != null) return;
        loadingInterstitial = true;
        InterstitialAd.load(this, BuildConfig.ADMOB_INTERSTITIAL_ID, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) {
                loadingInterstitial = false;
                if (!isDestroyed()) { interstitial = ad; interstitialLoadedAt = SystemClock.elapsedRealtime(); }
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { loadingInterstitial = false; interstitial = null; }
        });
    }
    private void loadRewarded() {
        if (!canRequestAds() || loadingReward || rewarded != null) return;
        loadingReward = true; updateRewardStatus();
        RewardedAd.load(this, BuildConfig.ADMOB_REWARDED_ID, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) {
                loadingReward = false;
                if (!isDestroyed()) { rewarded = ad; rewardedLoadedAt = SystemClock.elapsedRealtime(); updateRewardStatus(); }
            }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                loadingReward = false; rewarded = null; updateRewardStatus();
            }
        });
    }
    private boolean rewardReady() {
        if (rewarded != null && SystemClock.elapsedRealtime() - rewardedLoadedAt > 3300000L) rewarded = null;
        return rewarded != null && !showingAd;
    }
    private void updateRewardStatus() {
        if (rewardStatus != null) rewardStatus.setText(rewardReady() ? "Reward ready · watch a short ad for 75 coins" : loadingReward ? "Loading reward ad…" : "Reward ad unavailable · tap to retry");
        if (watchButton != null) watchButton.setText(rewardReady() ? "WATCH AD  ·  +75 COINS" : loadingReward ? "LOADING AD…" : "TRY REWARD AD");
        if (watchButton != null) watchButton.setEnabled(!loadingReward && !showingAd);
    }
    @Override public void onLevelCompleted() { completedSinceAd++; }
    @Override public void onContinueAfterWin(Runnable proceed) {
        long now = SystemClock.elapsedRealtime();
        if (interstitial != null && now - interstitialLoadedAt > 3300000L) interstitial = null;
        if (completedSinceAd < 5 || now - lastInterstitialAt < 120000L || interstitial == null || showingAd || !canRequestAds()) {
            proceed.run(); loadInterstitial(); return;
        }
        InterstitialAd ad = interstitial; interstitial = null;
        showingAd = true; game.setPaused(true);
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            boolean handled;
            private void finish() {
                if (handled) return; handled = true; showingAd = false;
                if (!isDestroyed()) { game.setPaused(false); proceed.run(); loadInterstitial(); }
            }
            @Override public void onAdShowedFullScreenContent() { completedSinceAd = 0; lastInterstitialAt = SystemClock.elapsedRealtime(); }
            @Override public void onAdDismissedFullScreenContent() { finish(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) { finish(); }
        });
        ad.show(this);
    }
    private void showRewarded(Runnable earned) {
        if (showingAd || isFinishing() || isDestroyed()) return;
        if (!canRequestAds() || !rewardReady()) {
            toast("Ad unavailable. Please try again shortly."); loadRewarded(); return;
        }
        RewardedAd ad = rewarded; rewarded = null; showingAd = true;
        pausedForAd = menu != null && menu.isShowing(); game.setPaused(true);
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            boolean finished;
            private void finish() {
                if (finished) return; finished = true; showingAd = false;
                if (!isDestroyed()) { game.setPaused(pausedForAd && menu != null && menu.isShowing()); loadRewarded(); updateRewardStatus(); }
            }
            @Override public void onAdDismissedFullScreenContent() { finish(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) { finish(); toast("Ad could not open. No reward was charged."); }
        });
        final boolean[] granted = {false};
        ad.show(this, rewardItem -> {
            if (granted[0]) return; granted[0] = true;
            earned.run();
            if (!isDestroyed()) { game.invalidate(); updateBalance(); }
        });
    }
    @Override public void requestRewardedErase() { showRewarded(() -> game.grantEraser()); }
    @Override public void requestRewardedRevive() { showRewarded(() -> game.grantRevive()); }
    @Override public void requestRewardedCoins() {
        String token = UUID.randomUUID().toString();
        showRewarded(() -> {
            int coins = wallet.rewardAd(token);
            toast(coins > 0 ? "+75 coins added" : "Could not save reward. Please check device storage.");
        });
    }
    @Override public void openWallet() { showPremiumStore(); }
    @Override public void openSettings() { showMenu(false); }
    @Override public void openHome() { showHome(); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable background(int color, int radius, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        if (strokeColor != 0) bg.setStroke(dp(1), strokeColor);
        return bg;
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(color); v.setTextSize(size);
        v.setPadding(0,dp(6),0,dp(6));
        return v;
    }
    private TextView section(String value) {
        TextView v=text(value,12,CYAN);
        v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setLetterSpacing(.10f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(18),0,dp(6));v.setLayoutParams(lp);
        return v;
    }
    private Button button(String label, Runnable action, boolean primary) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? INK : TEXT);
        b.setBackground(background(primary ? CYAN : PANEL_2,16,primary ? 0 : 0xFF244E87));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(50));
        lp.setMargins(0,dp(6),0,dp(4)); b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout panel() {
        LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16),dp(14),dp(16),dp(14));
        p.setBackground(background(PANEL,18,0xFF1E467C));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(7));p.setLayoutParams(lp);
        return p;
    }
    private void addProduct(LinearLayout body,String iconText,String name,String feature,String status,String actionLabel,Runnable action,boolean selected) {
        LinearLayout card=panel();
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text(iconText,26,selected?CYAN:PURPLE);
        icon.setGravity(Gravity.CENTER);icon.setBackground(background(0xFF0A1733,14,selected?CYAN:0xFF2C4E83));
        row.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(8),0);
        TextView title=text(name,16,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);copy.addView(title);
        copy.addView(text(feature,12,MUTED));
        TextView state=text(status,11,selected?GREEN:GOLD);state.setTypeface(Typeface.DEFAULT,Typeface.BOLD);copy.addView(state);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        card.addView(row);
        Button buy=button(actionLabel,action,selected);
        LinearLayout.LayoutParams blp=(LinearLayout.LayoutParams)buy.getLayoutParams();blp.height=dp(44);blp.topMargin=dp(10);buy.setLayoutParams(blp);
        card.addView(buy);body.addView(card);
    }
    private void updateBalance() { if (menuBalance != null) menuBalance.setText("COINS  " + wallet.balance()); }

    private void refreshStore() {
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); }
        showPremiumStore();
    }


    private Button tileButton(String label, int accent, Runnable action) {
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(TEXT);b.setGravity(Gravity.CENTER);
        b.setBackground(background(PANEL,18,accent));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(112),1f);lp.setMargins(dp(5),dp(6),dp(5),dp(6));b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());return b;
    }

    private Button tabButton(String label, boolean selected, Runnable action) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(selected?INK:TEXT);b.setBackground(background(selected?CYAN:PANEL_2,14,selected?0:0xFF244E87));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1f);lp.setMargins(dp(3),0,dp(3),0);b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());return b;
    }

    private void presentFullScreen(LinearLayout body) {
        if(isDestroyed()||showingAd)return;
        if(menu!=null){menu.setOnDismissListener(null);menu.dismiss();}
        destroyBanner();menuBalance=null;rewardStatus=null;watchButton=null;
        game.setPaused(true);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(INK);scroll.addView(body);
        menu=new AlertDialog.Builder(this).setView(scroll).create();
        menu.setOnDismissListener(d->{destroyBanner();menuBalance=null;rewardStatus=null;watchButton=null;if(!showingAd)game.setPaused(false);game.invalidate();});
        menu.show();
        if(menu.getWindow()!=null){
            menu.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);
            menu.getWindow().setStatusBarColor(INK);menu.getWindow().setNavigationBarColor(INK);
            menu.getWindow().setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,android.view.WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private LinearLayout premiumBody() {
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(18),dp(18),dp(24));body.setBackgroundColor(INK);return body;
    }

    private void addPremiumHeader(LinearLayout body,String title,String subtitle) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this);icon.setImageResource(com.arrowescape.pro.R.mipmap.ic_launcher);
        row.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);
        TextView t=text(title,23,TEXT);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);copy.addView(t);copy.addView(text(subtitle,12,MUTED));
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));body.addView(row);
    }

    private void showHome() {
        LinearLayout body=premiumBody();
        addPremiumHeader(body,"ARROW ESCAPE PRO","THINK  ·  PLAN  ·  SLIDE  ·  ESCAPE");

        menuBalance=text("COINS  "+wallet.balance(),16,GOLD);menuBalance.setGravity(Gravity.CENTER);menuBalance.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        menuBalance.setBackground(background(0xFF221C0D,16,0xFF6B5213));
        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-1,dp(46));blp.setMargins(0,dp(12),0,dp(6));menuBalance.setLayoutParams(blp);body.addView(menuBalance);

        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        if(wallet.canRewardDailyChallenge(day)){
            body.addView(button("🏆  DAILY CHALLENGE IS LIVE  ·  WIN 200 COINS",()->showDailyChallengePanel(),false));
        }

        LinearLayout hero=panel();
        TextView levelTitle=text("Continue Level "+game.currentLevelNumber(),20,TEXT);levelTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hero.addView(levelTitle);
        hero.addView(text(game.currentShapeName()+" world  ·  "+game.progressSummary(),12,MUTED));
        hero.addView(button("▶  PLAY",()->{if(menu!=null)menu.dismiss();game.openPlay();},true));body.addView(hero);

        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(tileButton("▥\nLEVELS\n200 PUZZLES",CYAN,()->{if(menu!=null)menu.dismiss();game.openLevels();}));
        row1.addView(tileButton("▣\nDAILY CHALLENGE\n200 COINS",PURPLE,()->showDailyChallengePanel()));body.addView(row1);

        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(tileButton("🏆\nACHIEVEMENTS\nTRACK PROGRESS",GOLD,()->showAchievements()));
        row2.addView(tileButton("🛒\nSTORE\nARROWS & THEMES",PURPLE,()->showPremiumStore()));body.addView(row2);

        body.addView(button("⚙  Settings",()->{if(menu!=null)menu.dismiss();showMenu(false);},false));
        presentFullScreen(body);
    }

    private void showPremiumStore() {
        LinearLayout body=premiumBody();
        addPremiumHeader(body,"STORE","Arrows, themes and rewards in one premium space");
        menuBalance=text("COINS  "+wallet.balance(),17,GOLD);menuBalance.setGravity(Gravity.CENTER);menuBalance.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        menuBalance.setBackground(background(0xFF221C0D,16,0xFF6B5213));body.addView(menuBalance);

        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tabButton("Arrow Garage",storeTab==0,()->{storeTab=0;showPremiumStore();}));
        tabs.addView(tabButton("Themes",storeTab==1,()->{storeTab=1;showPremiumStore();}));
        tabs.addView(tabButton("Coins",storeTab==2,()->{storeTab=2;showPremiumStore();}));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,dp(42));tlp.setMargins(0,dp(12),0,dp(8));tabs.setLayoutParams(tlp);body.addView(tabs);

        if(storeTab==0){
            body.addView(section("ARROW GARAGE"));
            body.addView(text("Collect visual arrow types. Purchases are permanent and never affect puzzle difficulty.",12,MUTED));
            String[] names=game.arrowTypeNames();
            for(int i=0;i<names.length;i++){
                final int which=i;boolean owned=game.isArrowTypeOwned(which),selected=game.currentArrowTypeIndex()==which;int price=game.arrowTypePrice(which);
                String status=selected?"EQUIPPED":owned?"OWNED":"LOCKED · "+price+" COINS";
                String action=selected?"EQUIPPED":owned?"EQUIP":price+" COINS";
                String glyph=which==4?"✦":which==3?"»":which==2?"➤":which==1?"→":"➜";
                addProduct(body,glyph,names[which],game.arrowTypeFeature(which),status,action,()->{
                    if(game.isArrowTypeOwned(which)){game.unlockAndSelectArrowType(which);toast(names[which]+" equipped");refreshStore();return;}
                    new AlertDialog.Builder(this).setTitle("Unlock "+names[which]+"?")
                        .setMessage(game.arrowTypeFeature(which)+"\n\nPrice: "+price+" coins\nBalance: "+wallet.balance()+" coins")
                        .setNegativeButton("Not now",null).setPositiveButton("Unlock",(d,w)->{
                            if(game.unlockAndSelectArrowType(which)){toast(names[which]+" unlocked");refreshStore();}
                            else toast("Need "+Math.max(0,price-wallet.balance())+" more coins");
                        }).show();
                },selected);
            }
            body.addView(button("Arrow color  ·  "+game.currentArrowStyle()+"  ·  FREE",()->{toast("Arrow color: "+game.cycleArrowStyle());refreshStore();},false));
        }else if(storeTab==1){
            body.addView(section("BOARD THEMES"));
            body.addView(text("Change the atmosphere of every puzzle without changing gameplay.",12,MUTED));
            String[] names=game.boardThemeNames();
            for(int i=0;i<names.length;i++){
                final int which=i;boolean owned=game.isBoardThemeOwned(which),selected=game.currentBoardThemeIndex()==which;int price=game.boardThemePrice(which);
                String status=selected?"ACTIVE":owned?"OWNED":"LOCKED · "+price+" COINS";
                String action=selected?"ACTIVE":owned?"APPLY":price+" COINS";
                String glyph=which==5?"◆":which==4?"✧":which==3?"●":which==2?"☼":which==1?"❄":"▦";
                addProduct(body,glyph,names[which],game.boardThemeFeature(which),status,action,()->{
                    if(game.isBoardThemeOwned(which)){game.unlockAndSelectBoardTheme(which);toast(names[which]+" theme applied");refreshStore();return;}
                    new AlertDialog.Builder(this).setTitle("Unlock "+names[which]+" theme?")
                        .setMessage(game.boardThemeFeature(which)+"\n\nPrice: "+price+" coins\nBalance: "+wallet.balance()+" coins")
                        .setNegativeButton("Not now",null).setPositiveButton("Unlock",(d,w)->{
                            if(game.unlockAndSelectBoardTheme(which)){toast(names[which]+" unlocked");refreshStore();}
                            else toast("Need "+Math.max(0,price-wallet.balance())+" more coins");
                        }).show();
                },selected);
            }
        }else{
            body.addView(section("COINS & REWARDS"));
            LinearLayout reward=panel();
            TextView big=text("Earn coins without paying",18,TEXT);big.setTypeface(Typeface.DEFAULT,Typeface.BOLD);reward.addView(big);
            reward.addView(text("Play levels, build your daily streak, beat the Daily Challenge, or watch a rewarded ad.",12,MUTED));
            body.addView(reward);
            watchButton=button("▶  WATCH AD  ·  +75 COINS",()->{if(rewardReady())requestRewardedCoins();else loadRewarded();},true);
            body.addView(watchButton);rewardStatus=text("",12,MUTED);body.addView(rewardStatus);updateRewardStatus();loadRewarded();
            body.addView(button("🏆  DAILY CHALLENGE  ·  +200 COINS",()->showDailyChallengePanel(),false));
            body.addView(button("🔥  DAILY STREAK REWARDS",()->{achievementTab=1;showAchievements();},false));
        }
        body.addView(button("⌂  Back to Home",this::showHome,true));
        presentFullScreen(body);
    }

    private void addAchievementCard(LinearLayout body,String icon,String name,String goal,String progress,boolean done) {
        LinearLayout card=panel();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge=text(icon,24,done?GREEN:PURPLE);badge.setGravity(Gravity.CENTER);badge.setBackground(background(0xFF0A1733,14,done?GREEN:0xFF2C4E83));
        row.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);
        TextView title=text(name,15,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);copy.addView(title);copy.addView(text(goal,12,MUTED));
        copy.addView(text(progress,11,done?GREEN:CYAN));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));card.addView(row);body.addView(card);
    }

    private void showAchievements() {
        LinearLayout body=premiumBody();addPremiumHeader(body,"ACHIEVEMENTS","Track progress, trophies and your daily streak");
        menuBalance=text(game.progressSummary(),13,CYAN);menuBalance.setGravity(Gravity.CENTER);body.addView(menuBalance);

        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tabButton("All Achievements",achievementTab==0,()->{achievementTab=0;showAchievements();}));
        tabs.addView(tabButton("Daily Streak",achievementTab==1,()->{achievementTab=1;showAchievements();}));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,dp(42));tlp.setMargins(0,dp(12),0,dp(8));tabs.setLayoutParams(tlp);body.addView(tabs);

        if(achievementTab==0){
            int complete=game.completedLevelCount(),perfect=game.perfectLevelCount(),stars=game.totalStarCount();
            addAchievementCard(body,"★","First Escape","Complete your first level",Math.min(complete,1)+" / 1",complete>=1);
            addAchievementCard(body,"◎","Perfect Run","Earn a 3★ clear",Math.min(perfect,1)+" / 1",perfect>=1);
            addAchievementCard(body,"♛","Puzzle Master","Complete 50 levels",Math.min(complete,50)+" / 50",complete>=50);
            addAchievementCard(body,"✦","Star Collector","Earn 300 stars",Math.min(stars,300)+" / 300",stars>=300);
            addAchievementCard(body,"◆","Perfectionist","Get 3★ on 50 levels",Math.min(perfect,50)+" / 50",perfect>=50);
            addAchievementCard(body,"🏆","Boss Hunter","Clear all five boss milestones",game.allBossesComplete()?"5 / 5":"Keep climbing",game.allBossesComplete());
            int streak=wallet.streak();
            addAchievementCard(body,"🔥","Streak Starter","Reach a 3-day daily streak",Math.min(streak,3)+" / 3",streak>=3);
            addAchievementCard(body,"🔥","Week Warrior","Reach a 7-day daily streak",Math.min(streak,7)+" / 7",streak>=7);
            addAchievementCard(body,"♨","Streak Legend","Reach a 30-day daily streak",Math.min(streak,30)+" / 30",streak>=30);
        }else{
            long now=System.currentTimeMillis();int streak=wallet.streak();int next=wallet.nextDailyStreak(now);int reward=wallet.nextDailyReward(now);
            LinearLayout hero=panel();
            TextView h=text("🔥  CURRENT STREAK  ·  "+streak+" DAYS",20,TEXT);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hero.addView(h);
            hero.addView(text(wallet.canClaimDaily(now)?"Today's reward is ready: "+reward+" coins":"Today's streak reward is already claimed.",13,MUTED));body.addView(hero);

            HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);
            LinearLayout ladder=new LinearLayout(this);ladder.setOrientation(LinearLayout.HORIZONTAL);int[] rewards={10,15,20,30,40,50,75};
            for(int i=0;i<7;i++){
                LinearLayout dayCard=new LinearLayout(this);dayCard.setOrientation(LinearLayout.VERTICAL);dayCard.setGravity(Gravity.CENTER);
                dayCard.setPadding(dp(10),dp(10),dp(10),dp(10));
                dayCard.setBackground(background((next==i+1&&wallet.canClaimDaily(now))?0xFF17385B:PANEL,15,(next==i+1&&wallet.canClaimDaily(now))?CYAN:0xFF244E87));
                TextView d=text("DAY "+(i+1),11,MUTED);d.setGravity(Gravity.CENTER);dayCard.addView(d);
                TextView c=text("● "+rewards[i],16,GOLD);c.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.setGravity(Gravity.CENTER);dayCard.addView(c);
                LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(dp(86),dp(76));dlp.setMargins(dp(4),0,dp(4),0);ladder.addView(dayCard,dlp);
            }
            horizontal.addView(ladder);body.addView(horizontal);
            Button claim=button(wallet.canClaimDaily(now)?"CLAIM TODAY  ·  +"+reward+" COINS":"TODAY CLAIMED",()->{
                int earned=wallet.claimDaily(System.currentTimeMillis());
                toast(earned>0?"+"+earned+" coins · streak "+wallet.streak()+" days":"Today's reward is already claimed");
                game.invalidate();achievementTab=1;showAchievements();
            },true);claim.setEnabled(wallet.canClaimDaily(now));body.addView(claim);
            body.addView(text("Rewards grow with your streak: 10 → 15 → 20 → 30 → 40 → 50 → 75 coins. After day 7, each continuing day earns 75 coins.",12,MUTED));
        }
        body.addView(button("⌂  Back to Home",this::showHome,true));presentFullScreen(body);
    }

    private String challengeResetText() {
        long now=System.currentTimeMillis();long next=((now/Wallet.DAY_MS)+1L)*Wallet.DAY_MS;long seconds=Math.max(0,(next-now)/1000L);
        long h=seconds/3600L,m=(seconds%3600L)/60L,s=seconds%60L;
        return String.format(java.util.Locale.US,"%02d:%02d:%02d",h,m,s);
    }

    private void showDailyChallengePanel() {
        LinearLayout body=premiumBody();addPremiumHeader(body,"DAILY CHALLENGE","One new SUPER HARD puzzle every UTC day");
        long day=System.currentTimeMillis()/Wallet.DAY_MS;boolean rewardReady=wallet.canRewardDailyChallenge(day);
        LinearLayout hero=panel();
        TextView crown=text("♛  DAILY CHALLENGE",24,GOLD);crown.setGravity(Gravity.CENTER);crown.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hero.addView(crown);
        TextView hard=text("SUPER HARD",13,0xFFFF667F);hard.setGravity(Gravity.CENTER);hard.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hero.addView(hard);
        hero.addView(text("Puzzle "+game.dailyPuzzleNumber()+"  ·  Same challenge for the whole day",13,MUTED));
        body.addView(hero);
        LinearLayout reward=panel();TextView amount=text(rewardReady?"●  REWARD  200 COINS":"✓  TODAY'S 200 COINS CLAIMED",22,rewardReady?GOLD:GREEN);
        amount.setGravity(Gravity.CENTER);amount.setTypeface(Typeface.DEFAULT,Typeface.BOLD);reward.addView(amount);
        reward.addView(text("Resets in "+challengeResetText(),12,MUTED));body.addView(reward);
        body.addView(button("▶  PLAY DAILY CHALLENGE",()->{if(menu!=null)menu.dismiss();game.startDailyChallenge();},true));
        body.addView(text("The Daily Challenge always uses the full-clearance rule, including self-tail blocking. One coin reward per day.",12,MUTED));
        body.addView(button("⌂  Back to Home",this::showHome,false));presentFullScreen(body);
    }

    private void maybeShowDailyChallengeReminder() {
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        if(!wallet.canRewardDailyChallenge(day))return;
        if(settings.getLong("daily_challenge_prompt_day",-1L)==day)return;
        settings.edit().putLong("daily_challenge_prompt_day",day).apply();
        if(reminder!=null&&reminder.isShowing())reminder.dismiss();

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(20),dp(22),dp(20));body.setBackgroundColor(INK);
        TextView icon=text("♛",42,GOLD);icon.setGravity(Gravity.CENTER);body.addView(icon);
        TextView title=text("Your Daily Challenge is ready!",22,TEXT);title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(title);
        TextView copy=text("Complete today's SUPER HARD puzzle to earn 200 coins.",14,MUTED);copy.setGravity(Gravity.CENTER);body.addView(copy);
        LinearLayout streak=panel();TextView st=text("🔥  Current streak: "+wallet.streak()+" days",16,TEXT);st.setTypeface(Typeface.DEFAULT,Typeface.BOLD);streak.addView(st);
        streak.addView(text("Today's streak reward: "+wallet.nextDailyReward(System.currentTimeMillis())+" coins",12,GOLD));body.addView(streak);
        body.addView(button("▶  PLAY NOW",()->{if(reminder!=null)reminder.dismiss();showDailyChallengePanel();},true));
        body.addView(button("Later",()->{if(reminder!=null)reminder.dismiss();},false));

        reminder=new AlertDialog.Builder(this).setView(body).create();reminder.setCanceledOnTouchOutside(false);reminder.show();
        if(reminder.getWindow()!=null){reminder.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);reminder.getWindow().setStatusBarColor(INK);}
    }

    private void showMenu(boolean store) {
        if (isDestroyed() || showingAd) return;
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); }
        destroyBanner(); menuBalance = null; rewardStatus = null; watchButton = null;
        game.setPaused(true);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(18),dp(20),dp(22));
        body.setBackgroundColor(INK);

        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setImageResource(com.arrowescape.pro.R.mipmap.ic_launcher);
        brand.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(dp(12),0,0,0);
        TextView title=text(store?"STORE":"SETTINGS",24,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.addView(title);
        heading.addView(text(store?"Upgrade your style. Keep gameplay fair.":"Game preferences & support",12,MUTED));
        brand.addView(heading,new LinearLayout.LayoutParams(0,-2,1f));
        body.addView(brand);

        if (store) {
            menuBalance=text("COINS  "+wallet.balance(),18,GOLD);menuBalance.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            menuBalance.setGravity(Gravity.CENTER);
            menuBalance.setBackground(background(0xFF221C0D,16,0xFF6B5213));
            LinearLayout.LayoutParams balanceLp=new LinearLayout.LayoutParams(-1,dp(48));balanceLp.setMargins(0,dp(14),0,dp(6));menuBalance.setLayoutParams(balanceLp);
            body.addView(menuBalance);

            LinearLayout hero=panel();
            TextView heroTitle=text("MAKE EVERY ESCAPE YOURS",18,TEXT);heroTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hero.addView(heroTitle);
            hero.addView(text("Buy visual upgrades with coins you earn by playing. Purchases stay unlocked.",13,MUTED));
            body.addView(hero);

            body.addView(section("ARROW GARAGE"));
            String[] arrowNames=game.arrowTypeNames();
            for(int i=0;i<arrowNames.length;i++){
                final int which=i;
                boolean owned=game.isArrowTypeOwned(which);
                boolean selected=game.currentArrowTypeIndex()==which;
                int price=game.arrowTypePrice(which);
                String status=selected?"EQUIPPED":owned?"OWNED":"LOCKED · "+price+" COINS";
                String actionLabel=selected?"EQUIPPED":owned?"EQUIP":price+" COINS";
                String glyph=which==4?"✦":which==3?"»":which==2?"➤":which==1?"→":"➜";
                addProduct(body,glyph,arrowNames[which],game.arrowTypeFeature(which),status,actionLabel,()->{
                    if(game.isArrowTypeOwned(which)){
                        game.unlockAndSelectArrowType(which);toast(arrowNames[which]+" equipped");refreshStore();return;
                    }
                    new AlertDialog.Builder(this)
                        .setTitle("Unlock "+arrowNames[which]+"?")
                        .setMessage(game.arrowTypeFeature(which)+"\n\nPrice: "+price+" coins\nBalance: "+wallet.balance()+" coins")
                        .setNegativeButton("Not now",null)
                        .setPositiveButton("Unlock",(d,w)->{
                            if(game.unlockAndSelectArrowType(which)){toast(arrowNames[which]+" unlocked");refreshStore();}
                            else toast("Need "+Math.max(0,price-wallet.balance())+" more coins");
                        }).show();
                },selected);
            }

            body.addView(button("Arrow color  ·  "+game.currentArrowStyle()+"  ·  FREE",()->{
                toast("Arrow color: "+game.cycleArrowStyle());refreshStore();
            },false));

            body.addView(section("BOARD THEMES"));
            String[] themeNames=game.boardThemeNames();
            for(int i=0;i<themeNames.length;i++){
                final int which=i;
                boolean owned=game.isBoardThemeOwned(which);
                boolean selected=game.currentBoardThemeIndex()==which;
                int price=game.boardThemePrice(which);
                String status=selected?"ACTIVE":owned?"OWNED":"LOCKED · "+price+" COINS";
                String actionLabel=selected?"ACTIVE":owned?"APPLY":price+" COINS";
                String glyph=which==5?"◆":which==4?"✧":which==3?"●":which==2?"☼":which==1?"❄":"▦";
                addProduct(body,glyph,themeNames[which],game.boardThemeFeature(which),status,actionLabel,()->{
                    if(game.isBoardThemeOwned(which)){
                        game.unlockAndSelectBoardTheme(which);toast(themeNames[which]+" theme applied");refreshStore();return;
                    }
                    new AlertDialog.Builder(this)
                        .setTitle("Unlock "+themeNames[which]+" theme?")
                        .setMessage(game.boardThemeFeature(which)+"\n\nPrice: "+price+" coins\nBalance: "+wallet.balance()+" coins")
                        .setNegativeButton("Not now",null)
                        .setPositiveButton("Unlock",(d,w)->{
                            if(game.unlockAndSelectBoardTheme(which)){toast(themeNames[which]+" unlocked");refreshStore();}
                            else toast("Need "+Math.max(0,price-wallet.balance())+" more coins");
                        }).show();
                },selected);
            }

            body.addView(section("COINS & REWARDS"));
            final Button[] daily=new Button[1];
            daily[0]=button(wallet.canClaimDaily(System.currentTimeMillis())?"CLAIM DAILY  ·  +100 COINS":"DAILY REWARD CLAIMED",()->{
                int earned=wallet.claimDaily(System.currentTimeMillis());
                toast(earned>0?"+100 coins · daily reward claimed":"Daily reward already claimed");
                updateBalance();game.invalidate();daily[0].setEnabled(wallet.canClaimDaily(System.currentTimeMillis()));
                daily[0].setText(wallet.canClaimDaily(System.currentTimeMillis())?"CLAIM DAILY  ·  +100 COINS":"DAILY REWARD CLAIMED");
            },true);
            daily[0].setEnabled(wallet.canClaimDaily(System.currentTimeMillis()));body.addView(daily[0]);
            body.addView(text("Daily streak: "+wallet.streak()+" · resets at 00:00 UTC",12,MUTED));

            watchButton=button("WATCH AD  ·  +75 COINS",()->{if(rewardReady())requestRewardedCoins();else loadRewarded();},false);
            body.addView(watchButton);rewardStatus=text("",12,MUTED);body.addView(rewardStatus);updateRewardStatus();loadRewarded();

            body.addView(section("CHALLENGES"));
            long day=System.currentTimeMillis()/Wallet.DAY_MS;
            body.addView(button(wallet.canRewardDailyChallenge(day)?"DAILY CHALLENGE  ·  UP TO +150":"REPLAY DAILY CHALLENGE",()->{menu.dismiss();game.startDailyChallenge();},false));
            long week=day/7L;
            body.addView(button(wallet.canRewardWeeklyChallenge(week)?"WEEKLY CHALLENGE  ·  UP TO +350":"REPLAY WEEKLY CHALLENGE",()->{menu.dismiss();game.startWeeklyChallenge();},false));
            if(game.canBuyHeart())body.addView(button("RESTORE HEART  ·  40 COINS",()->{if(game.buyHeart()){updateBalance();toast("Heart restored");}else toast("Not enough coins");},false));
            if(game.needsRevive())body.addView(button("CONTINUE RUN  ·  60 COINS",()->{if(game.buyContinue()){menu.dismiss();toast("Back in the maze");}else toast("Not enough coins");},false));

            body.addView(section("PROGRESSION"));
            LinearLayout progress=panel();progress.addView(text(game.progressSummary(),13,TEXT));body.addView(progress);
            body.addView(button("Achievements & chapter trophies",this::showProgressHall,false));
        } else {
            body.addView(section("GAMEPLAY"));
            String[] labels={"Sound effects","Vibration","High contrast arrows"};
            String[] keys={"sound","haptics","contrast"};
            for(int i=0;i<keys.length;i++){
                String key=keys[i],label=labels[i];
                android.widget.Switch toggle=new android.widget.Switch(this);
                toggle.setText(label);toggle.setTextSize(16);toggle.setTextColor(TEXT);toggle.setMinHeight(dp(54));
                toggle.setChecked(settings.getBoolean(key,!key.equals("contrast")));
                toggle.setOnCheckedChangeListener((v,checked)->{settings.edit().putBoolean(key,checked).apply();game.invalidate();});
                body.addView(toggle);
            }
            LinearLayout note=panel();
            note.addView(text("Visual purchases moved to Store",15,CYAN));
            note.addView(text("Arrow types, arrow colors and board themes now live in the Store — Settings is only for preferences.",12,MUTED));
            body.addView(note);

            body.addView(section("GAME"));
            body.addView(button("How to play",()->{menu.dismiss();game.showTutorialAgain();},false));
            body.addView(button("Restart this level",()->{
                new AlertDialog.Builder(this).setTitle("Restart level?").setMessage("Your coins stay safe. This puzzle starts again.")
                    .setNegativeButton("Keep playing",null).setPositiveButton("Restart",(d,w)->{menu.dismiss();game.restartCurrentLevel();}).show();
            },false));

            body.addView(section("PRIVACY & SUPPORT"));
            body.addView(button("Privacy policy",this::showPrivacyPolicy,false));
            if(consent!=null && consent.getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                body.addView(button("Privacy choices",()-> UserMessagingPlatform.showPrivacyOptionsForm(this,error->{
                    if(error!=null)toast("Privacy choices are unavailable right now.");
                    if(!consent.canRequestAds()){interstitial=null;rewarded=null;destroyBanner();}else startAdsIfAllowed();
                }),false));
            }
            body.addView(text("Version "+BuildConfig.VERSION_NAME+(BuildConfig.DEBUG?" · Test ads":""),12,MUTED));
        }

        body.addView(button(store?"BACK TO PUZZLE":"⌂  BACK TO HOME",()->{if(store)menu.dismiss();else showHome();},true));

        FrameLayout bannerSlot=new FrameLayout(this);
        LinearLayout.LayoutParams slotParams=new LinearLayout.LayoutParams(-1,dp(66));slotParams.topMargin=dp(16);body.addView(bannerSlot,slotParams);
        if(canRequestAds()){
            banner=new AdView(this);banner.setAdSize(AdSize.BANNER);banner.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            FrameLayout.LayoutParams bannerParams=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);bannerSlot.addView(banner,bannerParams);banner.loadAd(new AdRequest.Builder().build());
        } else bannerSlot.setVisibility(View.GONE);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setBackgroundColor(INK);scroll.addView(body);
        menu=new AlertDialog.Builder(this).setView(scroll).create();
        menu.setOnDismissListener(d->{destroyBanner();menuBalance=null;rewardStatus=null;watchButton=null;if(!showingAd)game.setPaused(false);game.invalidate();});
        menu.show();
        if(menu.getWindow()!=null){
            menu.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);
            menu.getWindow().setStatusBarColor(INK);
            menu.getWindow().setNavigationBarColor(INK);
        }
    }

    private void showProgressHall() {
        TextView content=text(game.progressDetails(),14,TEXT);
        content.setPadding(dp(22),dp(12),dp(22),dp(18));content.setLineSpacing(0,1.18f);content.setBackgroundColor(INK);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(INK);scroll.addView(content);
        AlertDialog hall=new AlertDialog.Builder(this).setTitle("Progression Hall").setView(scroll).setPositiveButton("Back",null).create();
        hall.show();if(hall.getWindow()!=null)hall.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);
    }

    private void destroyBanner() { if(banner!=null){banner.destroy();banner=null;} }
    private void showPrivacyPolicy() {
        StringBuilder policy=new StringBuilder();
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open("privacy-policy.txt"),java.nio.charset.StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null)policy.append(line).append('\n');
        }catch(java.io.IOException error){toast("Privacy policy could not be opened.");return;}
        TextView content=text(policy.toString(),14,TEXT);content.setPadding(dp(22),dp(12),dp(22),dp(16));content.setBackgroundColor(INK);
        android.text.util.Linkify.addLinks(content,android.text.util.Linkify.WEB_URLS);content.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(INK);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Privacy policy").setView(scroll).setPositiveButton("Back",null).create();
        dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);
    }
    private void toast(String message) { if(!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_SHORT).show(); }
    @Override protected void onPause(){super.onPause();game.saveProgress();game.setPaused(true);if(banner!=null)banner.pause();}
    @Override protected void onResume(){super.onResume();if(game!=null)game.setPaused(showingAd||(menu!=null&&menu.isShowing())||(reminder!=null&&reminder.isShowing()));if(banner!=null)banner.resume();}
    @Override protected void onDestroy(){destroyBanner();if(reminder!=null)reminder.dismiss();if(menu!=null)menu.dismiss();if(game!=null)game.release();super.onDestroy();}
    @Override public void onBackPressed(){if(reminder!=null&&reminder.isShowing())reminder.dismiss();else if(menu!=null&&menu.isShowing())menu.dismiss();else if(game.handleBack()){}else super.onBackPressed();}
}
