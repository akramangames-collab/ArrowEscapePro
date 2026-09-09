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
    private boolean adsStarted, loadingReward, loadingInterstitial, showingAd, pausedForAd;
    private long rewardedLoadedAt, interstitialLoadedAt;
    private int completedSinceAd;
    private long lastInterstitialAt = SystemClock.elapsedRealtime();
    private final int NAVY = 0xFF081D49, BLUE = 0xFF217FE7, MUTED = 0xFF64748B;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = getSharedPreferences("arrow_escape_pro", MODE_PRIVATE);
        wallet = new Wallet(new PreferenceWalletStorage(this));
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        game = new ArrowGameView(this, this, wallet);
        setContentView(game);
        consent = UserMessagingPlatform.getConsentInformation(this);
        // Demo ad units do not depend on the publisher's production UMP setup.
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
        if (rewardStatus != null) rewardStatus.setText(rewardReady() ? "Watch a short ad to earn 75 coins." : loadingReward ? "Loading an ad… You can keep playing." : "No ad available right now. Tap to try again.");
        if (watchButton != null) watchButton.setText(rewardReady() ? "Watch ad  ·  +75 coins" : loadingReward ? "Loading ad…" : "Try loading an ad");
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
    @Override public void openWallet() { showMenu(true); }
    @Override public void openSettings() { showMenu(false); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextColor(color); v.setTextSize(size); v.setPadding(0,dp(6),0,dp(6)); return v;
    }
    private Button button(String label, Runnable action, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : NAVY);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(primary ? BLUE : 0xFFEFF5FD); bg.setCornerRadius(dp(16)); b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(50)); lp.setMargins(0,dp(6),0,dp(4)); b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void updateBalance() { if (menuBalance != null) menuBalance.setText(wallet.balance() + " coins"); }
    private void showMenu(boolean rewards) {
        if (isDestroyed() || showingAd) return;
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); }
        destroyBanner(); menuBalance = null; rewardStatus = null; watchButton = null;
        game.setPaused(true);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(22),dp(16),dp(22),dp(20));
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setImageResource(com.arrowescape.pro.R.mipmap.ic_launcher);
        brand.addView(icon,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView title = text("Arrow Escape\nPuzzle Maze",20,NAVY); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setPadding(dp(12),0,0,0); brand.addView(title); body.addView(brand);
        menuBalance = text(wallet.balance()+" coins",30,NAVY); menuBalance.setTypeface(Typeface.DEFAULT,Typeface.BOLD); body.addView(menuBalance);
        if (rewards) {
            body.addView(text("YOUR REWARDS",12,MUTED));
            final Button[] daily = new Button[1];
            final TextView streak = text("Daily streak: "+wallet.streak()+"  ·  Resets at 00:00 UTC",12,MUTED);
            daily[0] = button(wallet.canClaimDaily(System.currentTimeMillis()) ? "Claim daily reward  ·  +100" : "Daily reward claimed", () -> {
                int earned = wallet.claimDaily(System.currentTimeMillis());
                toast(earned > 0 ? "+100 coins · Come back tomorrow" : "Daily reward already claimed or could not be saved");
                updateBalance(); game.invalidate();
                boolean available = wallet.canClaimDaily(System.currentTimeMillis());
                daily[0].setText(available ? "Claim daily reward  ·  +100" : "Daily reward claimed"); daily[0].setEnabled(available);
                streak.setText("Daily streak: "+wallet.streak()+"  ·  Resets at 00:00 UTC");
            },true);
            daily[0].setEnabled(wallet.canClaimDaily(System.currentTimeMillis())); body.addView(daily[0]); body.addView(streak);

            long challengeDay = System.currentTimeMillis() / Wallet.DAY_MS;
            boolean challengeRewardAvailable = wallet.canRewardDailyChallenge(challengeDay);
            int challengeStars = game.bestDailyStarsToday();
            String challengeLabel = challengeRewardAvailable ? "Daily Challenge  ·  up to +150" : "Replay Daily Challenge  ·  "+(challengeStars>0?challengeStars+"★":"reward claimed");
            body.addView(button(challengeLabel,()->{ menu.dismiss(); game.startDailyChallenge(); },true));
            body.addView(text(challengeRewardAvailable ? "One deterministic SUPER HARD puzzle each UTC day. Everyone gets the same challenge." : "Today's coin reward is claimed. Replay it to improve your stars.",12,MUTED));

            long challengeWeek = challengeDay / 7L;
            boolean weeklyRewardAvailable = wallet.canRewardWeeklyChallenge(challengeWeek);
            int weeklyStars = game.bestWeeklyStarsThisWeek();
            String weeklyLabel = weeklyRewardAvailable ? "Weekly Challenge  ·  up to +350" : "Replay Weekly Challenge  ·  "+(weeklyStars>0?weeklyStars+"★":"reward claimed");
            body.addView(button(weeklyLabel,()->{ menu.dismiss(); game.startWeeklyChallenge(); },true));
            body.addView(text(weeklyRewardAvailable ? "One elite milestone puzzle stays fixed for the UTC week. Clear it for 250–350 coins." : "This week's reward is claimed. Replay to improve your weekly stars.",12,MUTED));

            body.addView(text("PROGRESSION HALL",12,MUTED));
            body.addView(text(game.progressSummary(),13,NAVY));
            body.addView(button("Achievements & chapter trophies",this::showProgressHall,false));

            watchButton = button("Watch ad  ·  +75 coins",() -> { if (rewardReady()) requestRewardedCoins(); else loadRewarded(); },false);
            body.addView(watchButton); rewardStatus = text("",13,MUTED); body.addView(rewardStatus); updateRewardStatus(); loadRewarded();
            body.addView(text("1–3 stars on every level · 3★ means no mistakes and no assists\nClear: +15 · 3★ bonus: +10\nSUPER HARD milestone: +75 bonus · Boss milestone: +150 bonus\nDaily Challenge: up to +150 · Weekly Challenge: up to +350\nChapter trophies: Bronze / Silver / Gold · Two free hints, then 25 coins.",14,MUTED));
            if (game.canBuyHeart()) body.addView(button("Add one heart  ·  40 coins",() -> { if (game.buyHeart()) { updateBalance(); toast("Heart restored"); } else toast("Not enough coins"); },false));
            if (game.needsRevive()) body.addView(button("Continue  ·  60 coins",() -> { if (game.buyContinue()) { menu.dismiss(); toast("Back in the maze"); } else toast("Not enough coins"); },false));
        } else {
            body.addView(text("MAKE IT YOURS",12,MUTED));
            String[] labels={"Sound effects","Vibration","High contrast arrows"};
            String[] keys={"sound","haptics","contrast"};
            for(int i=0;i<keys.length;i++) {
                String key=keys[i],label=labels[i];
                android.widget.Switch toggle=new android.widget.Switch(this); toggle.setText(label); toggle.setTextSize(16); toggle.setTextColor(NAVY); toggle.setMinHeight(dp(50));
                toggle.setChecked(settings.getBoolean(key,!key.equals("contrast")));
                toggle.setOnCheckedChangeListener((v,checked)->{settings.edit().putBoolean(key,checked).apply();game.invalidate();});body.addView(toggle);
            }
            body.addView(text("Current puzzle shape · "+game.currentShapeName(),13,MUTED));
            final Button[] arrowTypeButton=new Button[1];
            arrowTypeButton[0]=button("Arrow type · "+game.currentArrowType(),()->{
                String[] options=game.arrowTypeOptionLabels();
                new AlertDialog.Builder(this)
                    .setTitle("Arrow Garage")
                    .setSingleChoiceItems(options,game.currentArrowTypeIndex(),(dialog,which)->{
                        if(game.isArrowTypeOwned(which)){
                            game.unlockAndSelectArrowType(which);
                            arrowTypeButton[0].setText("Arrow type · "+game.currentArrowType());
                            toast(game.currentArrowType()+" selected · "+game.arrowTypeFeature(which));
                            dialog.dismiss();
                            return;
                        }
                        int price=game.arrowTypePrice(which);
                        String name=game.arrowTypeNames()[which];
                        String feature=game.arrowTypeFeature(which);
                        dialog.dismiss();
                        new AlertDialog.Builder(this)
                            .setTitle("Unlock "+name+"?")
                            .setMessage(feature+"\n\nPrice: "+price+" coins\nYour balance: "+wallet.balance()+" coins")
                            .setNegativeButton("Not now",null)
                            .setPositiveButton("Unlock",(buy,w)->{
                                if(game.unlockAndSelectArrowType(which)){
                                    updateBalance();
                                    arrowTypeButton[0].setText("Arrow type · "+game.currentArrowType());
                                    toast(name+" unlocked and selected");
                                }else{
                                    int need=Math.max(0,price-wallet.balance());
                                    toast(need>0?"Need "+need+" more coins":"Could not save purchase");
                                }
                            })
                            .show();
                    })
                    .setNegativeButton("Cancel",null)
                    .show();
            },false);
            body.addView(arrowTypeButton[0]);
            body.addView(text("Classic is free. Slim 200 · Bold 350 · Chevron 500 · Neon 800 coins. Purchased types stay unlocked.",12,MUTED));

            final Button[] arrowStyleButton=new Button[1];
            arrowStyleButton[0]=button("Arrow color · "+game.currentArrowStyle(),()->{
                String name=game.cycleArrowStyle();
                arrowStyleButton[0].setText("Arrow color · "+name);
                toast("Arrow color: "+name);
            },false);
            body.addView(arrowStyleButton[0]);
            final Button[] boardThemeButton=new Button[1];
            boardThemeButton[0]=button("Board theme · "+game.currentBoardTheme(),()->{
                String[] options=game.boardThemeOptionLabels();
                new AlertDialog.Builder(this)
                    .setTitle("Theme Shop")
                    .setSingleChoiceItems(options,game.currentBoardThemeIndex(),(dialog,which)->{
                        if(game.isBoardThemeOwned(which)){
                            game.unlockAndSelectBoardTheme(which);
                            boardThemeButton[0].setText("Board theme · "+game.currentBoardTheme());
                            toast(game.currentBoardTheme()+" selected · "+game.boardThemeFeature(which));
                            dialog.dismiss();
                            return;
                        }
                        int price=game.boardThemePrice(which);
                        String name=game.boardThemeNames()[which];
                        String feature=game.boardThemeFeature(which);
                        dialog.dismiss();
                        new AlertDialog.Builder(this)
                            .setTitle("Unlock "+name+" theme?")
                            .setMessage(feature+"\n\nPrice: "+price+" coins\nYour balance: "+wallet.balance()+" coins")
                            .setNegativeButton("Not now",null)
                            .setPositiveButton("Unlock",(buy,w)->{
                                if(game.unlockAndSelectBoardTheme(which)){
                                    updateBalance();
                                    boardThemeButton[0].setText("Board theme · "+game.currentBoardTheme());
                                    toast(name+" theme unlocked");
                                }else{
                                    int need=Math.max(0,price-wallet.balance());
                                    toast(need>0?"Need "+need+" more coins":"Could not save purchase");
                                }
                            })
                            .show();
                    })
                    .setNegativeButton("Cancel",null)
                    .show();
            },false);
            body.addView(boardThemeButton[0]);
            body.addView(text("Clean is free. Ice 250 · Sunset 300 · Mint 350 · Midnight 500 · Lava 650 coins.",12,MUTED));
            body.addView(button("How to play",()->{menu.dismiss();game.showTutorialAgain();},false));
            body.addView(button("Privacy policy",this::showPrivacyPolicy,false));
            body.addView(button("Restart this level",()->{menu.dismiss();new AlertDialog.Builder(this).setTitle("Restart level?").setMessage("Your coin balance is kept. This puzzle starts again.").setNegativeButton("Keep playing",null).setPositiveButton("Restart",(d,w)->game.restartCurrentLevel()).show();},false));
            if(consent.getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                body.addView(button("Privacy choices",()-> UserMessagingPlatform.showPrivacyOptionsForm(this,error->{
                    if(error!=null)toast("Privacy choices are unavailable right now.");
                    if(!consent.canRequestAds()){interstitial=null;rewarded=null;destroyBanner();}else startAdsIfAllowed();
                }),false));
            }
            body.addView(text("Version "+BuildConfig.VERSION_NAME+(BuildConfig.DEBUG ? " · Test ads" : ""),12,MUTED));
        }
        body.addView(button("Back to puzzle",()->menu.dismiss(),true));
        // Banner is attached only inside the menu; never on the puzzle board.
        FrameLayout bannerSlot = new FrameLayout(this); LinearLayout.LayoutParams slotParams=new LinearLayout.LayoutParams(-1,dp(66)); slotParams.topMargin=dp(16); body.addView(bannerSlot,slotParams);
        if(canRequestAds()) {
            banner = new AdView(this); banner.setAdSize(AdSize.BANNER); banner.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            FrameLayout.LayoutParams bannerParams=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);bannerSlot.addView(banner,bannerParams);banner.loadAd(new AdRequest.Builder().build());
        } else bannerSlot.setVisibility(View.GONE);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.addView(body);
        menu = new AlertDialog.Builder(this).setView(scroll).create();
        menu.setOnDismissListener(d->{destroyBanner();menuBalance=null;rewardStatus=null;watchButton=null;if(!showingAd)game.setPaused(false);game.invalidate();});
        menu.show();
        if(menu.getWindow()!=null) menu.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);
    }
    private void showProgressHall() {
        TextView content=text(game.progressDetails(),14,NAVY);
        content.setPadding(dp(22),dp(12),dp(22),dp(18));
        content.setLineSpacing(0,1.18f);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog hall=new AlertDialog.Builder(this)
            .setTitle("Progression Hall")
            .setView(scroll)
            .setPositiveButton("Back",null)
            .create();
        hall.show();
        if(hall.getWindow()!=null)hall.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);
    }

    private void destroyBanner() { if(banner!=null){banner.destroy();banner=null;} }
    private void showPrivacyPolicy() {
        StringBuilder policy = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open("privacy-policy.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) policy.append(line).append('\n');
        } catch (java.io.IOException error) { toast("Privacy policy could not be opened."); return; }
        TextView content = text(policy.toString(),14,NAVY);
        content.setPadding(dp(22),dp(12),dp(22),dp(16));
        android.text.util.Linkify.addLinks(content,android.text.util.Linkify.WEB_URLS);
        content.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Privacy policy").setView(scroll).setPositiveButton("Back",null).show();
    }
    private void toast(String message) { if(!isDestroyed()) Toast.makeText(this,message,Toast.LENGTH_SHORT).show(); }
    @Override protected void onPause(){ super.onPause();game.saveProgress();game.setPaused(true);if(banner!=null)banner.pause(); }
    @Override protected void onResume(){super.onResume();if(game!=null)game.setPaused(showingAd || (menu!=null&&menu.isShowing()));if(banner!=null)banner.resume();}
    @Override protected void onDestroy(){destroyBanner();if(menu!=null)menu.dismiss();if(game!=null)game.release();super.onDestroy();}
    @Override public void onBackPressed(){if(menu!=null&&menu.isShowing())menu.dismiss();else if(game.handleBack()){}else super.onBackPressed();}
}
