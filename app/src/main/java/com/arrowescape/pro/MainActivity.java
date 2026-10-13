package com.arrowescape.pro;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
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
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;
import com.google.android.ump.*;
import java.util.UUID;

public class MainActivity extends ComponentActivity implements ArrowGameView.Host {
    private ArrowGameView game;
    private LinearLayout routeNav;
    private int onboardingStep = -1;
    private Wallet wallet;
    private SharedPreferences settings;
    private AmbientMusic music;
    private int pendingBooster=-1;
    private AlertDialog boosterLoading;
    private final android.os.Handler boosterHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable boosterTimeout=()->{if(pendingBooster>=0){cancelPendingBooster();toast("Ad unavailable. Please try again.");}};
    private boolean foreground;
    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private ConsentInformation consent;
    private AdView banner;
    private AdView gameBanner;
    private AdView resultBanner;
    private FrameLayout root, gameBannerSlot, resultBannerSlot, menuBannerSlot;
    private ScrollView activeMenuScroll;
    private LinearLayout activeMenuNav;
    private boolean gameplayBannerRequested;
    private int gameBannerHeightPx, resultBannerHeightPx;
    private int bannerRetryCount;
    private static final int MAX_BANNER_RETRIES = 3;
    private static final long BANNER_RETRY_DELAY_MS = 60000L;
    private AlertDialog menu;
    private TextView menuBalance, rewardStatus;
    private Button watchButton;
    private AlertDialog reminder;
    private int storeTab = 0, achievementTab = 0;
    private boolean playGamesSignedIn, playGamesSignInBusy;
    private String playGamesName="Arrow Player";
    private TextView playGamesPlayerLabel, playGamesStatusLabel;
    private android.widget.Button playGamesButton;
    private int currentNav = 0; // 0 Home · 1 Levels · 2 Daily · 3 Store · 4 Me
    private boolean adsStarted, loadingReward, loadingInterstitial, showingAd, pausedForAd;
    private long rewardedLoadedAt, interstitialLoadedAt;
    private int completedSinceAd;
    private static final int INTERSTITIAL_EVERY_LEVELS = 2;
    private static final long DAILY_CHALLENGE_REMINDER_DELAY_MS = 150000L; // 2.5 minutes
    private static final long DAILY_CHALLENGE_REMINDER_RETRY_MS = 30000L;
    private boolean dailyReminderScheduled;
    private final Runnable dailyReminderTask = this::tryShowDailyChallengeReminder;

    private final int INK = 0xFF020817;
    private final int PANEL = 0xFF08162E;
    private final int PANEL_2 = 0xFF10264A;
    private final int CYAN = 0xFF22D3EE;
    private final int BLUE = 0xFF2F8DF3;
    private final int PURPLE = 0xFF8B5CF6;
    private final int TEXT = 0xFFF8FBFF;
    private final int MUTED = 0xFF8FA7C8;
    private final int GOLD = 0xFFFFC83D;
    private final int GREEN = 0xFF34D399;
    private final int RED = 0xFFFF5C78;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        configurePlatformEdgeToEdge(getWindow());
        configureFamiliesAdPolicy();
        settings = getSharedPreferences("arrow_escape_pro", MODE_PRIVATE);
        music = new AmbientMusic(this);
        music.setEnabled(settings.getBoolean("music", true));
        wallet = new Wallet(new PreferenceWalletStorage(this));
        game = new ArrowGameView(this, this, wallet);
        game.finishGuidedTutorial();game.openLevels();
        root = new FrameLayout(this);
        root.setBackgroundColor(INK);
        applySystemBarInsets(root);
        // The banner has its own measured row, never an overlay over the canvas.
        LinearLayout gameColumn = new LinearLayout(this);
        gameColumn.setOrientation(LinearLayout.VERTICAL);
        root.addView(gameColumn,new FrameLayout.LayoutParams(-1,-1));
        gameColumn.addView(game,new LinearLayout.LayoutParams(-1,0,1f));
        routeNav = navigationBar(0);
        gameColumn.addView(routeNav,new LinearLayout.LayoutParams(-1,dp(64)));
        gameBannerSlot = new FrameLayout(this);
        gameBannerSlot.setBackgroundColor(INK);
        gameBannerSlot.setVisibility(View.GONE);
        gameColumn.addView(gameBannerSlot,new LinearLayout.LayoutParams(-1,-2));
        resultBannerSlot = new FrameLayout(this);
        resultBannerSlot.setBackgroundColor(INK);
        resultBannerSlot.setVisibility(View.GONE);
        gameColumn.addView(resultBannerSlot,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        installBackHandler();
        game.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            if(settings.contains("route_onboarding_step"))showOnboarding(settings.getInt("route_onboarding_step",0));else if(settings.getBoolean("route_onboarding_complete",false))showHome();else showOnboarding(0);
        }, 450L);
        consent = UserMessagingPlatform.getConsentInformation(this);
        requestConsentAndStartAds();
    }

    private RequestConfiguration familiesAdConfiguration;

    /**
     * Families-policy safety net supplied to the Next-Gen GMA SDK at initialization.
     * Every ad request is child-directed and capped at G-rated creative.
     */
    private void configureFamiliesAdPolicy() {
        familiesAdConfiguration = new RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G)
            .setAgeRestrictedTreatment(AgeRestrictedTreatment.CHILD)
            .build();
    }

    private void requestConsentAndStartAds() {
        if (isDestroyed()) return;
        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(this, parameters,
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, error -> {
                if (error != null) Log.w("ArrowAds","Consent form: "+error.getMessage());
                startAdsIfAllowed();
            }),
            error -> {
                Log.w("ArrowAds","Consent update failed: "+error.getMessage());
                startAdsIfAllowed();
                if (!adsStarted && game != null) {
                    game.postDelayed(() -> {
                        if (!adsStarted && !isDestroyed()) requestConsentAndStartAds();
                    }, 30000L);
                }
            });
        startAdsIfAllowed();
    }

    private void startAdsIfAllowed() {
        if (adsStarted || consent == null || !consent.canRequestAds() || isDestroyed()) return;
        adsStarted = true;
        final android.content.Context appContext = getApplicationContext();
        new Thread(() -> {
            try {
                InitializationConfig initConfig = new InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID)
                    .setRequestConfiguration(familiesAdConfiguration)
                    .build();
                MobileAds.initialize(appContext, initConfig, status -> runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loadInterstitial(); loadRewarded();
                    if (gameplayBannerRequested && (menu == null || !menu.isShowing())) showGameBanner();
                    else showVisibleMenuBanner();
                }));
            } catch (Throwable t) {
                Log.e("ArrowAds","Next-Gen GMA init failed",t);
                runOnUiThread(() -> adsStarted = false);
            }
        }, "ArrowAdsInit").start();
    }
    private boolean canRequestAds() { return adsStarted && consent != null && consent.canRequestAds() && !isDestroyed(); }
    private void loadInterstitial() {
        if (!canRequestAds() || loadingInterstitial || interstitial != null) return;
        loadingInterstitial = true;
        InterstitialAd.load(new AdRequest.Builder(BuildConfig.ADMOB_INTERSTITIAL_ID).build(), new AdLoadCallback<InterstitialAd>() {
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
        RewardedAd.load(new AdRequest.Builder(BuildConfig.ADMOB_REWARDED_ID).build(), new AdLoadCallback<RewardedAd>() {
            @Override public void onAdLoaded(RewardedAd ad) {
                loadingReward = false;
                if (!isDestroyed()) { rewarded = ad; rewardedLoadedAt = SystemClock.elapsedRealtime(); updateRewardStatus(); deliverPendingBoosterAd(); }
            }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                loadingReward = false; rewarded = null; updateRewardStatus(); if(pendingBooster>=0){cancelPendingBooster();toast("Ad unavailable. No booster used.");}
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
    @Override public void onLevelCompleted() { showResultBanner(); }
    @Override public void onContinueAfterWin(Runnable proceed) {
        hideResultBanner();
        completedSinceAd++;
        long now = SystemClock.elapsedRealtime();
        if (interstitial != null && now - interstitialLoadedAt > 3300000L) interstitial = null;
        if (completedSinceAd < INTERSTITIAL_EVERY_LEVELS || interstitial == null || showingAd || !canRequestAds()) {
            proceed.run(); enterGameplay(); loadInterstitial(); return;
        }
        InterstitialAd ad = interstitial; interstitial = null;
        showingAd = true; music.suspend(); game.setPaused(true);
        ad.setAdEventCallback(new InterstitialAdEventCallback() {
            boolean handled;
            private void finish() {
                if (handled) return; handled = true; showingAd = false;if(foreground&&music!=null)music.resume();
                if (!isDestroyed()) { game.setPaused(false); proceed.run(); enterGameplay(); loadInterstitial(); }
            }
            @Override public void onAdShowedFullScreenContent() { completedSinceAd = 0; }
            @Override public void onAdDismissedFullScreenContent() { finish(); }
            @Override public void onAdFailedToShowFullScreenContent(FullScreenContentError error) { finish(); }
        });
        ad.show(this);
    }
    private void showRewarded(Runnable earned) {
        if (showingAd || isFinishing() || isDestroyed()) return;
        if (!canRequestAds() || !rewardReady()) {
            toast("Ad unavailable. Please try again shortly."); loadRewarded(); return;
        }
        RewardedAd ad = rewarded; rewarded = null; showingAd = true; music.suspend();
        pausedForAd = menu != null && menu.isShowing(); game.setPaused(true);
        ad.setAdEventCallback(new RewardedAdEventCallback() {
            boolean finished;
            private void finish() {
                if (finished) return; finished = true; showingAd = false;if(foreground&&music!=null)music.resume();
                if (!isDestroyed()) { game.setPaused(pausedForAd && menu != null && menu.isShowing()); loadRewarded(); updateRewardStatus(); }
            }
            @Override public void onAdDismissedFullScreenContent() { finish(); }
            @Override public void onAdFailedToShowFullScreenContent(FullScreenContentError error) { finish(); toast("Ad could not open. No reward was charged."); }
        });
        final boolean[] granted = {false};
        ad.show(this, rewardItem -> {
            if (granted[0]) return; granted[0] = true;
            earned.run();
            if (!isDestroyed()) { game.invalidate(); updateBalance(); }
        });
    }
    private LinearLayout boosterCard(int kind,String title,String detail,String price,int accent){
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(gradient(0xFF142D49,0xFF0B1C32,18,accent));
        ImageView icon=new ImageView(this);icon.setImageDrawable(new BoosterIcon(kind,accent));icon.setPadding(dp(7),dp(7),dp(7),dp(7));icon.setBackground(background(0xFF173954,15,accent));card.addView(icon,new LinearLayout.LayoutParams(dp(55),dp(55)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(6),0);TextView name=text(title,16,TEXT);name.setTypeface(AppTypography.bold(this));copy.addView(name);copy.addView(text(detail,12,MUTED));card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView cost=text(price,12,accent);cost.setTypeface(AppTypography.bold(this));cost.setGravity(Gravity.CENTER);card.addView(cost,new LinearLayout.LayoutParams(dp(60),dp(46)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));card.setLayoutParams(lp);card.setMinimumHeight(dp(80));card.setContentDescription(title+", "+detail+", "+price+(kind<=3?". Preview before confirming.":". Activate with one saved booster."));card.setOnClickListener(v->{menu.dismiss();if(kind<=3)game.beginBooster(kind);else game.useInstantBooster(kind);});return card;
    }
    @Override public void openBoosters() {
        if(menu!=null&&menu.isShowing())menu.dismiss();destroyBanner();clearMenuBannerSlot();game.setPaused(true);
        LinearLayout body=panel();body.setTag("booster_sheet");body.setPadding(dp(18),dp(16),dp(18),dp(18));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("BOOSTERS",22,TEXT);title.setTypeface(AppTypography.bold(this));heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));heading.addView(text("● "+wallet.balance(),15,GOLD));body.addView(heading);
        body.addView(text("Tap a booster to use saved stock, or watch a rewarded ad when empty.",13,MUTED));
        body.addView(boosterCard(1,"Precise eraser","Remove one selected arrow","×"+game.boosterCount(1),0xFF67D8F6));
        body.addView(boosterCard(2,"Area bomb","Limited area · 4-cell radius","×"+game.boosterCount(2),0xFFB295FF));
        body.addView(boosterCard(3,"Row sweep","Clear arrows touching one row","×"+game.boosterCount(3),0xFF47E4D4));
        body.addView(section("REWARDED-AD BOOSTERS"));
        body.addView(boosterCard(4,"Safe Scan","Reveal every legal arrow for 8 seconds","×"+game.boosterCount(4),0xFFFFCE69));
        body.addView(boosterCard(5,"Mistake Shield","Protect one wrong tap from heart loss","×"+game.boosterCount(5),0xFF76EDAA));
        body.addView(boosterCard(6,"Triple Escape","Automatically release up to 3 legal arrows","×"+game.boosterCount(6),0xFFDA9BFF));
        body.addView(text("Existing targeted tools require confirmation. New boosters activate instantly, and never spend coins.",12,MUTED));
        body.addView(button("Back to puzzle",()->menu.dismiss(),false));
        ScrollView boosterScroll=new ScrollView(this);
        boosterScroll.setFillViewport(false);
        boosterScroll.setBackgroundColor(INK);
        boosterScroll.addView(body);
        menu=new AlertDialog.Builder(this).setView(boosterScroll).create();
        menu.setOnDismissListener(d->{game.setPaused(false);game.invalidate();});
        menu.show();
        if(menu.getWindow()!=null){menu.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)); menu.getWindow().setGravity(Gravity.BOTTOM);menu.getWindow().setLayout(-1,Math.min(dp(700),Math.max(dp(320),getResources().getDisplayMetrics().heightPixels-dp(95))));}
    }
    @Override public void shareChallenge(String text) {
        android.content.Intent send=new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");send.putExtra(android.content.Intent.EXTRA_TEXT,text);
        try{startActivity(android.content.Intent.createChooser(send,"Challenge a friend"));}
        catch(android.content.ActivityNotFoundException e){toast("No sharing app available");}
    }
    @Override public void requestRewardedErase() { requestRewardedBooster(1); }
    @Override public void requestRewardedBooster(int kind) {
        if(kind<0||kind>=BoosterInventory.COUNT||showingAd||pendingBooster>=0)return;
        if(!canRequestAds()){toast("Reward ad unavailable. No booster used.");return;}
        pendingBooster=kind;
        if(rewardReady()){deliverPendingBoosterAd();return;}
        boosterLoading=new AlertDialog.Builder(this).setTitle("Loading ad · +1 "+BoosterInventory.name(kind))
            .setMessage("Complete the rewarded ad for one saved use. You can cancel while it loads.")
            .setNegativeButton("Cancel",(d,w)->cancelPendingBooster()).create();
        boosterLoading.setOnCancelListener(d->cancelPendingBooster());boosterLoading.show();
        boosterHandler.postDelayed(boosterTimeout,20000);loadRewarded();
    }
    private void deliverPendingBoosterAd(){
        if(pendingBooster<0||!foreground||showingAd||!rewardReady())return;
        int kind=pendingBooster;cancelPendingBooster();String receipt=UUID.randomUUID().toString();
        showRewarded(()->game.grantBooster(kind,receipt));
    }
    private void cancelPendingBooster(){
        pendingBooster=-1;boosterHandler.removeCallbacks(boosterTimeout);
        if(boosterLoading!=null){boosterLoading.dismiss();boosterLoading=null;}
    }
    @Override public void requestRewardedRevive() { showRewarded(() -> game.grantRevive()); }
    @Override public void requestRewardedCoins() {
        String token = UUID.randomUUID().toString();
        showRewarded(() -> {
            int coins = wallet.rewardAd(token);
            toast(coins > 0 ? "+75 coins added" : "Could not save reward. Please check device storage.");
        });
    }
    @Override public void openWallet() { hideResultBanner(); showPremiumStore(); }
    @Override public void openSettings() { hideResultBanner(); showMenu(false); }
    @Override public void openHome() { hideResultBanner(); stopGameplayBanner(); showHome(); }

    private void configurePlatformEdgeToEdge(android.view.Window window) {
        if (window == null) return;
        // PhoneWindow.getInsetsController dereferences mDecor on API 30+.
        // onCreate runs before setContentView, so explicitly create the decor
        // before asking for the controller (also safe for dialog windows).
        window.getDecorView();
        // Use the AndroidX compatibility entry point so edge-to-edge is explicitly
        // enabled on pre-Android-15 devices as well. This call does not require the
        // deprecated status/navigation bar color APIs.
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if(settings!=null&&settings.getBoolean("reduce_motion",false))window.setWindowAnimations(0);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            window.setAttributes(attrs);
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                    0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        }
    }

    private void applySystemBarInsets(View target) {
        ViewCompat.setOnApplyWindowInsetsListener(target, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            // Root owns safe-area padding. Sending the same bars to the canvas
            // would add the top/bottom spacing twice.
            return WindowInsetsCompat.CONSUMED;
        });
        target.post(() -> ViewCompat.requestApplyInsets(target));
    }

    private void installBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (reminder != null && reminder.isShowing()) {
                    reminder.dismiss();
                    return;
                }
                if (menu != null && menu.isShowing()) {
                    menu.dismiss();
                    return;
                }
                if (game != null && game.handleBack()) return;
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable background(int color, int radius, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        if (strokeColor != 0) bg.setStroke(dp(1), strokeColor);
        return bg;
    }
    private GradientDrawable gradient(int startColor,int endColor,int radius,int strokeColor) {
        GradientDrawable bg=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{startColor,endColor});
        bg.setCornerRadius(dp(radius));
        if(strokeColor!=0)bg.setStroke(dp(1),strokeColor);
        return bg;
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(color); v.setTextSize(size);v.setTypeface(AppTypography.regular(this));
        v.setPadding(0,dp(6),0,dp(6));
        return v;
    }
    private TextView section(String value) {
        TextView v=text(value,11,CYAN);
        v.setTypeface(AppTypography.bold(this));
        v.setLetterSpacing(.14f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(dp(2),dp(20),0,dp(7));v.setLayoutParams(lp);
        return v;
    }
    private Button button(String label, Runnable action, boolean primary) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(AppTypography.bold(this));
        b.setLetterSpacing(.01f);
        b.setTextColor(primary ? 0xFF00131D : TEXT);
        b.setBackground(primary
            ? gradient(0xFF21F1E7,0xFF05BDD6,26,0xFF57FFF6)
            : gradient(0xFF122B48,0xFF091A31,20,0xFF2B5278));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(54));
        lp.setMargins(0,dp(7),0,dp(5)); b.setLayoutParams(lp);
        b.setElevation(dp(primary?5:2));
        b.setOnTouchListener((v,event)->{if(settings.getBoolean("reduce_motion",false))return false;if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)v.animate().scaleX(.97f).scaleY(.97f).setDuration(90).start();else if(event.getAction()==android.view.MotionEvent.ACTION_UP||event.getAction()==android.view.MotionEvent.ACTION_CANCEL)v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();return false;});
        b.setOnClickListener(v -> action.run()); return b;
    }
    private LinearLayout panel() {
        LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16),dp(15),dp(16),dp(15));
        p.setBackground(gradient(0xFF0D2142,0xFF07152C,20,0xFF1E4F87));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(7),0,dp(8));p.setLayoutParams(lp);
        return p;
    }
    private void addProduct(LinearLayout body,String iconText,String name,String feature,String status,String actionLabel,Runnable action,boolean selected) {
        LinearLayout card=panel();
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text(iconText,27,selected?CYAN:PURPLE);
        icon.setGravity(Gravity.CENTER);icon.setBackground(gradient(0xFF102A55,0xFF07172F,15,selected?CYAN:0xFF2C4E83));
        row.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,dp(8),0);
        TextView title=text(name,16,TEXT);title.setTypeface(AppTypography.bold(this));copy.addView(title);
        copy.addView(text(feature,12,MUTED));
        TextView state=text(status,11,selected?GREEN:GOLD);state.setTypeface(AppTypography.bold(this));copy.addView(state);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        Button buy=new Button(this);buy.setAllCaps(false);buy.setText(actionLabel);buy.setTextSize(12);buy.setTypeface(AppTypography.bold(this));
        buy.setTextColor(selected?INK:TEXT);
        buy.setBackground(selected?gradient(CYAN,BLUE,14,0):gradient(PURPLE,0xFF5C38D8,14,0));
        buy.setEnabled(!selected);buy.setOnClickListener(v->action.run());
        row.addView(buy,new LinearLayout.LayoutParams(dp(96),dp(48)));
        card.addView(row);
        Button preview=button("PREVIEW",()->showProductPreview(name,feature,status,actionLabel,action),false);
        card.addView(preview);body.addView(card);
    }
    private void showProductPreview(String name,String feature,String status,String actionLabel,Runnable action){
        int arrow=0,theme=0;String[] names=game.arrowTypeNames();
        for(int i=0;i<names.length;i++)if(names[i].equals(name))arrow=i;
        names=game.boardThemeNames();for(int i=0;i<names.length;i++)if(names[i].equals(name))theme=i;
        LinearLayout body=panel();body.addView(text(name,22,TEXT));body.addView(text(feature,14,MUTED));
        body.addView(new ArrowPreviewView(this,arrow,theme),new LinearLayout.LayoutParams(-1,dp(190)));
        body.addView(text(status,14,GOLD));body.addView(text("Preview only · your coins and equipped style stay unchanged",13,MUTED));
        AlertDialog preview=new AlertDialog.Builder(this).setView(body).setNegativeButton("Close",null).setPositiveButton(actionLabel,(d,w)->action.run()).create();
        preview.show();if(preview.getWindow()!=null)preview.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
    }

    private void updateBalance() { if (menuBalance != null) menuBalance.setText("● " + wallet.balance()); }

    private void refreshStore() {
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); }
        showPremiumStore();
    }


    private Button tileButton(String label, int accent, Runnable action) {
        Button b=new Button(this);
        b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(AppTypography.bold(this));
        b.setTextColor(TEXT);b.setGravity(Gravity.CENTER);
        b.setBackground(gradient(0xFF0C2142,0xFF08162E,20,accent));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(118),1f);lp.setMargins(dp(5),dp(6),dp(5),dp(6));b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());return b;
    }

    private Button tabButton(String label, boolean selected, Runnable action) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTypeface(AppTypography.bold(this));
        b.setTextColor(selected?0xFF00131D:MUTED);
        b.setBackground(selected?gradient(CYAN,BLUE,14,0):background(0xFF0A1934,14,0xFF193F70));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1f);lp.setMargins(dp(3),0,dp(3),0);b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());return b;
    }

    private void presentFullScreen(LinearLayout body) { presentFullScreen(body,currentNav); }

    private void presentFullScreen(LinearLayout body,int nav) {
        currentNav=nav;
        if(isDestroyed()||showingAd)return;
        if(menu!=null){menu.setOnDismissListener(null);menu.dismiss();}
        destroyBanner();suspendGameBanner();menuBalance=null;rewardStatus=null;watchButton=null;
        game.setPaused(true);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(INK);scroll.addView(body);
        FrameLayout shell=menuShell(scroll);
        menu=new AlertDialog.Builder(this).setView(shell).create();
        menu.setOnDismissListener(d->{destroyBanner();clearMenuBannerSlot();menuBalance=null;rewardStatus=null;watchButton=null;if(!showingAd){game.setPaused(false);}game.invalidate();});
        menu.show();
        scroll.post(() -> scroll.scrollTo(0,0));
        if(onboardingStep<0)showVisibleMenuBanner();
        if(menu.getWindow()!=null){
            mountFullScreenMenu(shell);
        }
    }

    private void mountFullScreenMenu(FrameLayout shell){
        android.view.Window window=menu.getWindow();if(window==null)return;
        if(shell.getParent() instanceof android.view.ViewGroup parent)parent.removeView(shell);
        window.setContentView(shell);configurePlatformEdgeToEdge(window);
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(INK));window.setLayout(-1,-1);applySystemBarInsets(shell);
    }

    private LinearLayout premiumBody() {
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(16),dp(16),dp(22));
        body.setBackground(gradient(0xFF020817,0xFF04112A,0,0));
        return body;
    }

    private void addPremiumHeader(LinearLayout body,String title,String subtitle) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=text("‹",30,CYAN);icon.setGravity(Gravity.CENTER);icon.setContentDescription("Back to Route");icon.setBackground(background(0xFF10284A,14,0xFF245681));icon.setOnClickListener(v->{if(onboardingStep>=0)finishOnboarding();else showHome();});
        row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(10),0,0,0);
        TextView t=text(title,19,TEXT);t.setTypeface(AppTypography.bold(this));copy.addView(t);
        TextView sub=text(subtitle,12,MUTED);sub.setLetterSpacing(.06f);copy.addView(sub);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        TextView coin=text("● "+wallet.balance(),12,GOLD);coin.setGravity(Gravity.CENTER);
        coin.setTypeface(AppTypography.bold(this));coin.setBackground(background(0xFF221B09,17,0xFF6E5512));
        menuBalance=coin;
        row.addView(coin,new LinearLayout.LayoutParams(dp(76),dp(38)));
        body.addView(row);
    }

    private void dismissMenuForRoute() {
        if(menu!=null){menu.setOnDismissListener(null);menu.dismiss();menu=null;}
        destroyBanner();clearMenuBannerSlot();
    }

    private void showHome() {
        dismissMenuForRoute();hideResultBanner();stopGameplayBanner();currentNav=0;
        game.setPaused(false);game.openLevels();onRouteOpened();
    }
    @Override public void onRouteOpened(){
        hideResultBanner();stopGameplayBanner();currentNav=0;
        if(routeNav!=null)routeNav.setVisibility(View.VISIBLE);
        if(root!=null)root.requestLayout();
    }
    @Override public void onGameplayStarted(){
        hideResultBanner();
        if(routeNav!=null)routeNav.setVisibility(View.GONE);
        stopGameplayBanner();if(root!=null)root.requestLayout();
    }

    private void finishOnboarding(){
        settings.edit().putBoolean("route_onboarding_complete",true).remove("route_onboarding_step").apply();
        getSharedPreferences("arrow_puzzle_faithful",MODE_PRIVATE).edit().putBoolean("tutorialSeen",true).apply();
        game.finishGuidedTutorial();onboardingStep=-1;showHome();
    }

    private void showOnboarding(int step){
        if(isDestroyed()||showingAd)return;
        onboardingStep=Math.max(0,Math.min(2,step));
        settings.edit().putInt("route_onboarding_step",onboardingStep).apply();
        dismissMenuForRoute();hideResultBanner();stopGameplayBanner();game.setPaused(true);routeNav.setVisibility(View.GONE);
        LinearLayout body=premiumBody();
        Button skip=button("Skip tutorial  ›",this::finishOnboarding,false);LinearLayout.LayoutParams skipLp=new LinearLayout.LayoutParams(dp(150),dp(48));skipLp.gravity=Gravity.END;skip.setLayoutParams(skipLp);body.addView(skip);
        String[] titles={"WELCOME TO ARROW ESCAPE","YOUR ESCAPE TOOLS","LEARN TO ESCAPE"};
        TextView title=text(titles[onboardingStep],26,TEXT);title.setTypeface(AppTypography.bold(this));body.addView(title);
        body.addView(text("STEP "+(onboardingStep+1)+" OF 3",12,CYAN));
        if(onboardingStep==0){
            body.addView(text("Clear the arrows. Find your escape.",20,TEXT));
            body.addView(new ArrowPreviewView(this,4,0),new LinearLayout.LayoutParams(-1,dp(190)));
            body.addView(text("400 levels · Hard, Super Hard and Extreme checkpoints",15,CYAN));
            body.addView(text("Tap an arrow only when its full escape path is clear. Its body follows through the bends; other arrows and its own tail can block it.",15,MUTED));
            body.addView(button("NEXT  ›",()->showOnboarding(1),true));
        }else if(onboardingStep==1){
            body.addView(text("Hint · highlights a safe move",18,GOLD));
            body.addView(text("1 free hint every level attempt. At zero, tap a tool to watch an ad for one use.\nPrecise eraser · removes your selected arrow\nArea bomb · clears arrows in a limited area\nRow sweep · clears arrows touching one row",15,TEXT));
            body.addView(text("Choose a target and preview it before confirming. Cancel is free; prices are shown before coins are spent.",15,MUTED));
            body.addView(text("Route is your home. Daily has a separate challenge; Store has cosmetic previews; Awards tracks achievements and streaks; Me has your preferences.",15,CYAN));
            body.addView(button("TRY THE PRACTICE  ›",()->showOnboarding(2),true));
            body.addView(button("‹  BACK",()->showOnboarding(0),false));
        }else{
            body.addView(text("Clear the purple arrow first, then the cyan arrow.",16,TEXT));
            body.addView(new TutorialPracticeView(this,this::finishOnboarding),new LinearLayout.LayoutParams(-1,dp(260)));
            body.addView(text("Practice is free: no hearts, coins or campaign progress are used. In a real level, a blocked tap costs one heart. Pinch to zoom dense boards.",14,MUTED));
            body.addView(button("‹  BACK",()->showOnboarding(1),false));
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);
        FrameLayout shell=new FrameLayout(this);shell.addView(scroll,new FrameLayout.LayoutParams(-1,-1));applySystemBarInsets(shell);
        menu=new AlertDialog.Builder(this).setView(shell).create();menu.setOnCancelListener(d->finishOnboarding());menu.show();
        if(menu.getWindow()!=null)mountFullScreenMenu(shell);
    }

    private void showPremiumStore() {
        LinearLayout body=premiumBody();
        addPremiumHeader(body,"STORE","Preview · unlock · equip");

        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tabButton("Arrows",storeTab==0,()->{storeTab=0;showPremiumStore();}));
        tabs.addView(tabButton("Themes",storeTab==1,()->{storeTab=1;showPremiumStore();}));
        tabs.addView(tabButton("Rewards",storeTab==2,()->{storeTab=2;showPremiumStore();}));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,dp(42));tlp.setMargins(0,dp(12),0,dp(8));tabs.setLayoutParams(tlp);body.addView(tabs);
        if(storeTab<2){
            LinearLayout hero=panel();
            int arrow=game.currentArrowTypeIndex(),theme=game.currentBoardThemeIndex();
            hero.addView(text(storeTab==0?game.arrowTypeNames()[arrow]:game.boardThemeNames()[theme],20,TEXT));
            hero.addView(new ArrowPreviewView(this,arrow,theme),new LinearLayout.LayoutParams(-1,dp(140)));
            hero.addView(text("✓  EQUIPPED · cosmetic changes only",12,CYAN));body.addView(hero);
        }

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
            body.addView(section("YOUR BOOSTERS"));
            for(int i=0;i<BoosterInventory.COUNT;i++){final int kind=i;body.addView(button(BoosterInventory.name(i)+"  ×"+game.boosterCount(i)+"  ·  WATCH AD +1",()->requestRewardedBooster(kind),false));}
            body.addView(text("Gifts arrive at selected campaign checkpoints. Replaying a checkpoint never duplicates its gift. Coins unlock cosmetic styles.",12,MUTED));
            body.addView(section("COINS & REWARDS"));
            LinearLayout reward=panel();
            TextView big=text("Earn coins without paying",18,TEXT);big.setTypeface(AppTypography.bold(this));reward.addView(big);
            reward.addView(text("Play levels, build your daily streak, beat the Daily Challenge, or watch a rewarded ad.",12,MUTED));
            body.addView(reward);
            watchButton=button("▶  WATCH AD  ·  +75 COINS",()->{if(rewardReady())requestRewardedCoins();else loadRewarded();},true);
            body.addView(watchButton);rewardStatus=text("",12,MUTED);body.addView(rewardStatus);updateRewardStatus();loadRewarded();
            body.addView(button("🏆  DAILY CHALLENGE  ·  +200 COINS",()->showDailyChallengePanel(),false));
            body.addView(button("🔥  DAILY STREAK REWARDS",()->{achievementTab=1;showAchievements();},false));
        }
        presentFullScreen(body,2);
    }

    private void addAchievementCard(LinearLayout body,String icon,String name,String goal,String progress,boolean done) {
        LinearLayout card=panel();LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge=text(icon,24,done?GREEN:PURPLE);badge.setGravity(Gravity.CENTER);badge.setBackground(background(0xFF0A1733,14,done?GREEN:0xFF2C4E83));
        row.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);
        TextView title=text(name,15,TEXT);title.setTypeface(AppTypography.bold(this));copy.addView(title);copy.addView(text(goal,12,MUTED));
        copy.addView(text(progress,11,done?GREEN:CYAN));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));card.addView(row);body.addView(card);
    }

    private void showAchievements() {
        LinearLayout body=premiumBody();addPremiumHeader(body,"AWARDS","Track progress, trophies and your daily streak");
        menuBalance=text(game.progressSummary(),13,CYAN);menuBalance.setGravity(Gravity.CENTER);body.addView(menuBalance);

        LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tabButton("Achievements",achievementTab==0,()->{achievementTab=0;showAchievements();}));
        tabs.addView(tabButton("Streak",achievementTab==1,()->{achievementTab=1;showAchievements();}));
        tabs.addView(tabButton("Chapters",achievementTab==2,()->{achievementTab=2;showAchievements();}));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,dp(42));tlp.setMargins(0,dp(12),0,dp(8));tabs.setLayoutParams(tlp);body.addView(tabs);

        if(achievementTab==2){
            String[][] chapters=game.chapterAwardCards();
            for(String[] chapter:chapters)addAchievementCard(body,"◆",chapter[0],chapter[1],chapter[2],chapter[3].equals("true"));
        }else if(achievementTab==0){
            int complete=game.completedLevelCount(),perfect=game.perfectLevelCount(),stars=game.totalStarCount();
            addAchievementCard(body,"★","First Escape","Complete your first level",Math.min(complete,1)+" / 1",complete>=1);
            addAchievementCard(body,"◎","Perfect Run","Earn a 3★ clear",Math.min(perfect,1)+" / 1",perfect>=1);
            addAchievementCard(body,"♛","Puzzle Master","Complete 50 levels",Math.min(complete,50)+" / 50",complete>=50);
            addAchievementCard(body,"✦","Star Collector","Earn 300 stars",Math.min(stars,300)+" / 300",stars>=300);
            addAchievementCard(body,"◆","Perfectionist","Get 3★ on 50 levels",Math.min(perfect,50)+" / 50",perfect>=50);
            addAchievementCard(body,"🏆","Boss Hunter","Clear all 13 Extreme Hard checkpoints",game.allBossesComplete()?"Unlocked":"Keep climbing",game.allBossesComplete());
            int streak=wallet.streak();
            addAchievementCard(body,"🔥","Streak Starter","Reach a 3-day daily streak",Math.min(streak,3)+" / 3",streak>=3);
            addAchievementCard(body,"🔥","Week Warrior","Reach a 7-day daily streak",Math.min(streak,7)+" / 7",streak>=7);
            addAchievementCard(body,"♨","Streak Legend","Reach a 30-day daily streak",Math.min(streak,30)+" / 30",streak>=30);
        }else{
            long now=System.currentTimeMillis();int streak=wallet.streak();int next=wallet.nextDailyStreak(now);int reward=wallet.nextDailyReward(now);
            LinearLayout hero=panel();
            TextView h=text("🔥  CURRENT STREAK  ·  "+streak+" DAYS",20,TEXT);h.setTypeface(AppTypography.bold(this));hero.addView(h);
            hero.addView(text(wallet.canClaimDaily(now)?"Today's reward is ready: "+reward+" coins":"Today's streak reward is already claimed.",13,MUTED));body.addView(hero);

            HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);
            LinearLayout ladder=new LinearLayout(this);ladder.setOrientation(LinearLayout.HORIZONTAL);int[] rewards={10,15,20,30,40,50,75};
            for(int i=0;i<7;i++){
                LinearLayout dayCard=new LinearLayout(this);dayCard.setOrientation(LinearLayout.VERTICAL);dayCard.setGravity(Gravity.CENTER);
                dayCard.setPadding(dp(10),dp(10),dp(10),dp(10));
                dayCard.setBackground(background((next==i+1&&wallet.canClaimDaily(now))?0xFF17385B:PANEL,15,(next==i+1&&wallet.canClaimDaily(now))?CYAN:0xFF244E87));
                TextView d=text("DAY "+(i+1),11,MUTED);d.setGravity(Gravity.CENTER);dayCard.addView(d);
                TextView c=text("● "+rewards[i],16,GOLD);c.setTypeface(AppTypography.bold(this));c.setGravity(Gravity.CENTER);dayCard.addView(c);
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
        presentFullScreen(body,3);updatePlayGamesControls();refreshPlayGamesStatus();
    }

    private String challengeResetText() {
        long now=System.currentTimeMillis();long next=((now/Wallet.DAY_MS)+1L)*Wallet.DAY_MS;long seconds=Math.max(0,(next-now)/1000L);
        long h=seconds/3600L,m=(seconds%3600L)/60L,s=seconds%60L;
        return String.format(java.util.Locale.US,"%02d:%02d:%02d",h,m,s);
    }

    private void showDailyChallengePanel() {
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        settings.edit().putLong("daily_challenge_prompt_day",day).apply();
        dailyReminderScheduled=false;
        if(game!=null)game.removeCallbacks(dailyReminderTask);
        LinearLayout body=premiumBody();addPremiumHeader(body,"DAILY CHALLENGE","Separate from your 400-level route");
        LinearLayout preview=panel();preview.addView(new ArrowPreviewView(this,0,0),new LinearLayout.LayoutParams(-1,dp(120)));body.addView(preview);
        boolean rewardReady=wallet.canRewardDailyChallenge(day);
        LinearLayout hero=panel();
        TextView crown=text("♛  DAILY CHALLENGE #"+game.dailyPuzzleNumber(),20,GOLD);crown.setGravity(Gravity.CENTER);crown.setTypeface(AppTypography.bold(this));hero.addView(crown);
        TextView hard=text("SUPER HARD",13,0xFFFF667F);hard.setGravity(Gravity.CENTER);hard.setTypeface(AppTypography.bold(this));hero.addView(hard);
        hero.addView(text("Daily Puzzle "+game.dailyPuzzleNumber()+"  ·  Never taken from the 400 campaign levels",13,MUTED));
        body.addView(hero);
        LinearLayout reward=panel();TextView amount=text(rewardReady?"●  REWARD  200 COINS":"✓  TODAY'S 200 COINS CLAIMED",22,rewardReady?GOLD:GREEN);
        amount.setGravity(Gravity.CENTER);amount.setTypeface(AppTypography.bold(this));reward.addView(amount);
        reward.addView(text("Resets in "+challengeResetText(),12,MUTED));body.addView(reward);
        LinearLayout week=panel();
        TextView streakTitle=text("🔥  CURRENT STREAK  ·  "+wallet.streak()+" DAYS",15,TEXT);streakTitle.setTypeface(AppTypography.bold(this));week.addView(streakTitle);
        LinearLayout dots=new LinearLayout(this);dots.setGravity(Gravity.CENTER);
        for(int i=1;i<=7;i++){
            TextView d=text(String.valueOf(i),12,i<=Math.min(7,wallet.streak())?INK:MUTED);
            d.setGravity(Gravity.CENTER);d.setTypeface(AppTypography.bold(this));
            d.setBackground(background(i<=Math.min(7,wallet.streak())?GOLD:0xFF0A1934,14,i<=Math.min(7,wallet.streak())?0:0xFF23466E));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1f);lp.setMargins(dp(3),dp(8),dp(3),0);dots.addView(d,lp);
        }
        week.addView(dots);body.addView(week);
        body.addView(button("▶  PLAY TODAY'S CHALLENGE",()->{if(menu!=null)menu.dismiss();game.startDailyChallenge();enterGameplay();},true));
        body.addView(text("Separate from campaign progress · reward once per day · replay anytime to improve stars.",12,MUTED));
        presentFullScreen(body,1);
    }

    private android.widget.Switch preferenceToggle(String label,String key,boolean defaultValue){
        android.widget.Switch toggle=new android.widget.Switch(this);toggle.setText(label);toggle.setTextSize(16);toggle.setTextColor(TEXT);toggle.setMinHeight(dp(52));
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));toggle.setTrackTintList(android.content.res.ColorStateList.valueOf(0xFF245A75));
        toggle.setChecked(settings.getBoolean(key,defaultValue));toggle.setOnCheckedChangeListener((v,checked)->{settings.edit().putBoolean(key,checked).apply();if(key.equals("music")){music.setEnabled(checked);if(foreground&&!showingAd)music.resume();}game.invalidate();});return toggle;
    }
    private void showProfile() {
        currentNav=4;LinearLayout body=premiumBody();addPremiumHeader(body,"ME","Your profile and preferences");
        LinearLayout identity=panel(),row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar=text("➜",30,CYAN);avatar.setGravity(Gravity.CENTER);avatar.setBackground(gradient(0xFF153D70,0xFF0A1D3A,30,CYAN));row.addView(avatar,new LinearLayout.LayoutParams(dp(60),dp(60)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);
        playGamesPlayerLabel=text(playGamesName,20,TEXT);playGamesPlayerLabel.setTypeface(AppTypography.bold(this));copy.addView(playGamesPlayerLabel);
        copy.addView(text("Level "+game.currentLevelNumber()+" · "+game.totalStarCount()+" stars",13,MUTED));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));identity.addView(row);body.addView(identity);
        LinearLayout account=panel();account.addView(text("GOOGLE PLAY GAMES",14,CYAN));
        playGamesStatusLabel=text(playGamesSignedIn?"Connected":"Optional · play without signing in",13,MUTED);account.addView(playGamesStatusLabel);
        playGamesButton=button(playGamesSignedIn?"✓  Connected":"Sign in with Google Play Games",this::signInWithPlayGames,true);account.addView(playGamesButton);
        account.addView(text("Progress saved on this device",12,MUTED));body.addView(account);
        LinearLayout preferences=panel();preferences.addView(preferenceToggle("Music","music",true));preferences.addView(preferenceToggle("Sound effects","sound",true));preferences.addView(preferenceToggle("Vibration","haptics",true));body.addView(preferences);
        body.addView(button("⚙  More settings",()->showMenu(false),false));body.addView(button("Replay tutorial",()->showOnboarding(0),false));body.addView(button("Help & privacy",this::showPrivacyPolicy,false));
        body.addView(button("★  Rate Arrow Escape",this::openPlayStoreRating,false));presentFullScreen(body,4);updatePlayGamesControls();refreshPlayGamesStatus();
        playGamesButton.setAlpha(playGamesButton.isEnabled()?1f:.55f);
    }

    private void refreshPlayGamesStatus() {
        if(!ArrowApplication.playGamesReady||isDestroyed()||playGamesSignInBusy)return;
        com.google.android.gms.games.PlayGames.getGamesSignInClient(this).isAuthenticated()
            .addOnCompleteListener(task->{
                if(isDestroyed()||isFinishing())return;
                playGamesSignedIn=task.isSuccessful()&&task.getResult()!=null&&task.getResult().isAuthenticated();
                updatePlayGamesProfile();
            });
    }
    private void signInWithPlayGames() {
        if(!ArrowApplication.playGamesReady||playGamesSignInBusy||playGamesSignedIn)return;
        playGamesSignInBusy=true;updatePlayGamesControls();
        com.google.android.gms.games.PlayGames.getGamesSignInClient(this).signIn()
            .addOnCompleteListener(task->{
                if(isDestroyed()||isFinishing())return;
                playGamesSignInBusy=false;
                playGamesSignedIn=task.isSuccessful()&&task.getResult()!=null&&task.getResult().isAuthenticated();
                if(!playGamesSignedIn)toast("Sign-in was not completed. You can try again.");
                updatePlayGamesProfile();
            });
    }
    private void updatePlayGamesProfile() {
        if(isDestroyed())return;
        if(!playGamesSignedIn){playGamesName="Arrow Player";updatePlayGamesControls();return;}
        com.google.android.gms.games.PlayGames.getPlayersClient(this).getCurrentPlayer()
            .addOnCompleteListener(task->{
                if(isDestroyed()||isFinishing())return;
                if(task.isSuccessful()&&task.getResult()!=null)playGamesName=task.getResult().getDisplayName();
                updatePlayGamesControls();
            });
    }
    private void updatePlayGamesControls() {
        if(isDestroyed())return;
        if(playGamesPlayerLabel!=null)playGamesPlayerLabel.setText(playGamesName);
        if(playGamesStatusLabel!=null)playGamesStatusLabel.setText(playGamesSignedIn?"Connected to Google Play Games":ArrowApplication.playGamesReady?"Connect your Play Games profile":"Google Play Games is not available yet");
        if(playGamesButton!=null){
            playGamesButton.setText(playGamesSignInBusy?"Connecting…":playGamesSignedIn?"✓  Google Play Games connected":"Sign in with Google Play Games");
            playGamesButton.setEnabled(ArrowApplication.playGamesReady&&!playGamesSignInBusy&&!playGamesSignedIn);
        }
    }

    private LinearLayout profileStat(String value,String label) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);
        box.setBackground(gradient(0xFF0D2142,0xFF07152C,18,0xFF1E4F87));
        TextView v=text(value,20,TEXT);v.setGravity(Gravity.CENTER);v.setTypeface(AppTypography.bold(this));box.addView(v);
        TextView l=text(label,11,MUTED);l.setGravity(Gravity.CENTER);box.addView(l);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(78),1f);lp.setMargins(dp(4),dp(6),dp(4),dp(6));box.setLayoutParams(lp);
        return box;
    }

    private void scheduleDailyChallengeReminder() {
        if(dailyReminderScheduled || game==null)return;
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        if(!wallet.canRewardDailyChallenge(day))return;
        if(settings.getLong("daily_challenge_prompt_day",-1L)==day)return;
        dailyReminderScheduled=true;
        game.removeCallbacks(dailyReminderTask);
        game.postDelayed(dailyReminderTask,DAILY_CHALLENGE_REMINDER_DELAY_MS);
    }

    private void tryShowDailyChallengeReminder() {
        if(isFinishing()||isDestroyed()||game==null){dailyReminderScheduled=false;return;}
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        if(!wallet.canRewardDailyChallenge(day)||settings.getLong("daily_challenge_prompt_day",-1L)==day){
            dailyReminderScheduled=false;
            return;
        }
        if(showingAd||(menu!=null&&menu.isShowing())||(reminder!=null&&reminder.isShowing())){
            game.postDelayed(dailyReminderTask,DAILY_CHALLENGE_REMINDER_RETRY_MS);
            return;
        }
        dailyReminderScheduled=false;
        maybeShowDailyChallengeReminder();
    }

    private void maybeShowDailyChallengeReminder() {
        long day=System.currentTimeMillis()/Wallet.DAY_MS;
        if(!wallet.canRewardDailyChallenge(day))return;
        if(settings.getLong("daily_challenge_prompt_day",-1L)==day)return;
        settings.edit().putLong("daily_challenge_prompt_day",day).apply();
        if(reminder!=null&&reminder.isShowing())reminder.dismiss();

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(20),dp(22),dp(20));body.setBackgroundColor(INK);
        TextView icon=text("♛",42,GOLD);icon.setGravity(Gravity.CENTER);body.addView(icon);
        TextView title=text("Your Daily Challenge is ready!",22,TEXT);title.setGravity(Gravity.CENTER);title.setTypeface(AppTypography.bold(this));body.addView(title);
        TextView copy=text("Complete today's SUPER HARD puzzle to earn 200 coins.",14,MUTED);copy.setGravity(Gravity.CENTER);body.addView(copy);
        LinearLayout streak=panel();TextView st=text("🔥  Current streak: "+wallet.streak()+" days",16,TEXT);st.setTypeface(AppTypography.bold(this));streak.addView(st);
        streak.addView(text("Today's streak reward: "+wallet.nextDailyReward(System.currentTimeMillis())+" coins",12,GOLD));body.addView(streak);
        body.addView(button("▶  PLAY NOW",()->{if(reminder!=null)reminder.dismiss();showDailyChallengePanel();},true));
        body.addView(button("Later",()->{if(reminder!=null)reminder.dismiss();},false));

        reminder=new AlertDialog.Builder(this).setView(body).create();reminder.setCanceledOnTouchOutside(false);
        reminder.setOnDismissListener(d->{if(!showingAd&&(menu==null||!menu.isShowing()))game.setPaused(false);});
        game.setPaused(true);reminder.show();
        if(reminder.getWindow()!=null)reminder.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);
    }

    private void showMenu(boolean store) {
        currentNav=store?2:4;
        if (isDestroyed() || showingAd) return;
        if (menu != null) { menu.setOnDismissListener(null); menu.dismiss(); }
        destroyBanner(); suspendGameBanner(); menuBalance = null; rewardStatus = null; watchButton = null;
        game.setPaused(true);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(18),dp(20),dp(22));
        body.setBackgroundColor(INK);

        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setImageResource(com.arrowescape.pro.R.mipmap.ic_launcher);
        brand.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(dp(12),0,0,0);
        TextView title=text(store?"STORE":"SETTINGS",24,TEXT);title.setTypeface(AppTypography.bold(this));heading.addView(title);
        heading.addView(text(store?"Upgrade your style. Keep gameplay fair.":"Game preferences & support",12,MUTED));
        brand.addView(heading,new LinearLayout.LayoutParams(0,-2,1f));
        body.addView(brand);

        if (store) {
            menuBalance=text("COINS  "+wallet.balance(),18,GOLD);menuBalance.setTypeface(AppTypography.bold(this));
            menuBalance.setGravity(Gravity.CENTER);
            menuBalance.setBackground(background(0xFF221C0D,16,0xFF6B5213));
            LinearLayout.LayoutParams balanceLp=new LinearLayout.LayoutParams(-1,dp(48));balanceLp.setMargins(0,dp(14),0,dp(6));menuBalance.setLayoutParams(balanceLp);
            body.addView(menuBalance);

            LinearLayout hero=panel();
            TextView heroTitle=text("MAKE EVERY ESCAPE YOURS",18,TEXT);heroTitle.setTypeface(AppTypography.bold(this));hero.addView(heroTitle);
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

            body.addView(section("YOUR BOOSTERS"));
            for(int i=0;i<BoosterInventory.COUNT;i++){final int kind=i;body.addView(button(BoosterInventory.name(i)+"  ×"+game.boosterCount(i)+"  ·  WATCH AD +1",()->requestRewardedBooster(kind),false));}
            body.addView(text("Gifts arrive at selected campaign checkpoints. Replaying a checkpoint never duplicates its gift. Coins unlock cosmetic styles.",12,MUTED));
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
            String[] labels={"Soothing background music","Sound effects","Vibration","High contrast arrows","Reduce motion"};
            String[] keys={"music","sound","haptics","contrast","reduce_motion"};
            for(int i=0;i<keys.length;i++){
                String key=keys[i],label=labels[i];
                android.widget.Switch toggle=new android.widget.Switch(this);
                toggle.setText(label);toggle.setTextSize(16);toggle.setTextColor(TEXT);toggle.setMinHeight(dp(54));
                toggle.setChecked(settings.getBoolean(key,!key.equals("contrast")&&!key.equals("reduce_motion")));
                toggle.setOnCheckedChangeListener((v,checked)->{settings.edit().putBoolean(key,checked).apply();if(key.equals("music")){music.setEnabled(checked);if(foreground&&!showingAd)music.resume();}game.invalidate();});
                body.addView(toggle);
            }
            LinearLayout note=panel();
            note.addView(text("Visual purchases moved to Store",15,CYAN));
            note.addView(text("Arrow types, arrow colors and board themes now live in the Store — Settings is only for preferences.",12,MUTED));
            body.addView(note);

            body.addView(section("GAME"));
            body.addView(button("Replay tutorial",()->showOnboarding(0),false));
            body.addView(button("Restart this level",()->{
                new AlertDialog.Builder(this).setTitle("Restart level?").setMessage("Your coins stay safe. This puzzle starts again.")
                    .setNegativeButton("Keep playing",null).setPositiveButton("Restart",(d,w)->{menu.dismiss();game.restartCurrentLevel();}).show();
            },false));

            body.addView(section("PRIVACY & SUPPORT"));
            body.addView(button("★  Rate Arrow Escape",this::openPlayStoreRating,false));
            body.addView(button("Privacy policy",this::showPrivacyPolicy,false));
            if(consent!=null && consent.getPrivacyOptionsRequirementStatus()==ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                body.addView(button("Privacy choices",()-> UserMessagingPlatform.showPrivacyOptionsForm(this,error->{
                    if(error!=null)toast("Privacy choices are unavailable right now.");
                    if(!consent.canRequestAds()){interstitial=null;rewarded=null;destroyBanner();}else startAdsIfAllowed();
                }),false));
            }
            body.addView(text("Version "+BuildConfig.VERSION_NAME,12,MUTED));
        }

        body.addView(button(store?"BACK TO PUZZLE":"‹  BACK TO ROUTE",()->{if(store)menu.dismiss();else showHome();},true));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setBackgroundColor(INK);scroll.addView(body);
        FrameLayout shell=menuShell(scroll);
        menu=new AlertDialog.Builder(this).setView(shell).create();
        menu.setOnDismissListener(d->{destroyBanner();clearMenuBannerSlot();menuBalance=null;rewardStatus=null;watchButton=null;if(!showingAd){game.setPaused(false);}game.invalidate();});
        menu.show();
        scroll.post(() -> scroll.scrollTo(0,0));
        if(onboardingStep<0)showVisibleMenuBanner();
        if(menu.getWindow()!=null){
            mountFullScreenMenu(shell);
        }
    }

    private void showProgressHall() {
        TextView content=text(game.progressDetails(),14,TEXT);
        content.setPadding(dp(22),dp(12),dp(22),dp(18));content.setLineSpacing(0,1.18f);content.setBackgroundColor(INK);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(INK);scroll.addView(content);
        AlertDialog hall=new AlertDialog.Builder(this).setTitle("Progression Hall").setView(scroll).setPositiveButton("Back",null).create();
        hall.show();if(hall.getWindow()!=null)hall.getWindow().setBackgroundDrawableResource(com.arrowescape.pro.R.drawable.dialog_background);
    }

    private AdSize adaptiveBannerSize(int horizontalMarginDp) {
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
        int widthPx=Math.max(dp(200),metrics.widthPixels-dp(horizontalMarginDp));
        int widthDp=Math.max(200,Math.round(widthPx/metrics.density));
        return AdSize.getLargeAnchoredAdaptiveBannerAdSize(this,widthDp);
    }

    private AdView createAdaptiveBanner(FrameLayout slot,int horizontalMarginDp) {
        if(!canRequestAds()||slot==null)return null;
        AdSize size=adaptiveBannerSize(horizontalMarginDp);
        int height=Math.max(dp(50),size.getHeightInPixels(this));
        android.view.ViewGroup.LayoutParams lp=slot.getLayoutParams();
        if(lp!=null){lp.height=height+dp(24);slot.setLayoutParams(lp);}
        slot.setPadding(0,dp(24),0,0);
        slot.setMinimumHeight(0);
        slot.setBackground(new AdRailBackdrop(dp(10),dp(16)));
        slot.removeAllViews();
        // Reserve the requested footprint before loading, so controls never move into
        // the ad area while the creative arrives. Buffer stays non-interactive.
        slot.setVisibility(View.VISIBLE);
        AdView ad=new AdView(this);ad.setVisibility(View.INVISIBLE);
        slot.addView(ad,new FrameLayout.LayoutParams(-2,height,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        BannerAdRequest request=new BannerAdRequest.Builder(BuildConfig.ADMOB_BANNER_ID,size).build();
        ad.loadAd(request,new AdLoadCallback<BannerAd>() {
            @Override public void onAdLoaded(BannerAd loadedAd) {
                runOnUiThread(() -> {
                if(!bannerRequestCurrent(ad,slot))return;
                bannerRetryCount=0;ad.setVisibility(View.VISIBLE);
                loadedAd.setAdEventCallback(new BannerAdEventCallback() {});
                slot.setVisibility(View.VISIBLE);
                if(slot==gameBannerSlot){
                    gameBannerHeightPx=height;
                    updateGameBottomInset();
                }else if(slot==resultBannerSlot){
                    resultBannerHeightPx=height;
                    updateGameBottomInset();
                }else if(slot==menuBannerSlot){
                    setMenuBannerSpace(height,true);
                }
                Log.d("ArrowAds","Banner loaded");
                });
            }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                runOnUiThread(() -> {
                if(!bannerRequestCurrent(ad,slot))return;
                slot.setVisibility(View.GONE);ad.setVisibility(View.INVISIBLE);
                if(slot==gameBannerSlot){
                    gameBannerHeightPx=0; updateGameBottomInset();
                }else if(slot==resultBannerSlot){
                    resultBannerHeightPx=0; updateGameBottomInset();
                }else if(slot==menuBannerSlot){
                    setMenuBannerSpace(0,false);
                }
                Log.w("ArrowAds","Banner failed code="+error.getCode()+" message="+error.getMessage());
                if (bannerRetryCount >= MAX_BANNER_RETRIES || isDestroyed()) return;
                bannerRetryCount++;
                slot.postDelayed(() -> {
                    if (!bannerRequestCurrent(ad,slot) || !canRequestAds()) return;
                    if (slot == gameBannerSlot) {
                        if (gameBanner != null) { gameBanner.destroy(); gameBanner=null; }
                        showGameBanner();
                    } else if (slot == resultBannerSlot) {
                        if (resultBanner != null) { resultBanner.destroy(); resultBanner=null; }
                        showResultBanner();
                    } else if (slot == menuBannerSlot) {
                        if (banner != null) { banner.destroy(); banner=null; }
                        showVisibleMenuBanner();
                    }
                }, BANNER_RETRY_DELAY_MS);
                });
            }
        });
        return ad;
    }

    private boolean bannerRequestCurrent(View ad,FrameLayout slot){
        if(isDestroyed()||ad.getParent()!=slot)return false;
        if(slot==resultBannerSlot)return game!=null&&game.isResultScreen();
        if(slot==gameBannerSlot)return gameplayBannerRequested;
        return slot==menuBannerSlot&&menu!=null&&menu.isShowing();
    }

    private void updateGameBottomInset() {
        // LinearLayout measures the visible creative row; no cached-height
        // margin can leave the game under a large or resized adaptive banner.
        if(root!=null)root.requestLayout();
        if(game!=null)game.invalidate();
    }

    private void enterGameplay() {onGameplayStarted();}

    private void showGameBanner() {
        if(onboardingStep>=0||!gameplayBannerRequested||!canRequestAds()||showingAd||isFinishing()||isDestroyed())return;
        if(menu!=null&&menu.isShowing())return;
        if(resultBannerSlot!=null&&resultBannerSlot.getVisibility()==View.VISIBLE)return;
        if(gameBanner!=null)return;
        gameBanner=createAdaptiveBanner(gameBannerSlot,0);
    }

    private void suspendGameBanner() {
        if(gameBanner!=null){gameBanner.destroy();gameBanner=null;}
        if(gameBannerSlot!=null){gameBannerSlot.removeAllViews();gameBannerSlot.setVisibility(View.GONE);}
        gameBannerHeightPx=0;updateGameBottomInset();
    }

    private void stopGameplayBanner() {
        gameplayBannerRequested=false;
        suspendGameBanner();
    }

    private FrameLayout menuShell(ScrollView scroll) {
        FrameLayout shell=new FrameLayout(this);
        shell.setBackgroundColor(INK);
        activeMenuScroll=scroll;
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);
        shell.addView(column,new FrameLayout.LayoutParams(-1,-1));
        column.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout nav=navigationBar(currentNav);
        activeMenuNav=nav;
        column.addView(nav,new LinearLayout.LayoutParams(-1,dp(68)));
        menuBannerSlot=new FrameLayout(this);
        menuBannerSlot.setBackgroundColor(INK);
        menuBannerSlot.setVisibility(View.GONE);
        column.addView(menuBannerSlot,new LinearLayout.LayoutParams(-1,-2));
        return shell;
    }

    private LinearLayout navigationBar(int selected){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(6),dp(4),dp(6),dp(4));
        nav.setBackground(gradient(0xFF091B33,0xFF041020,0,0xFF1A466A));
        String[] labels={"Route","Daily","Store","Awards","Me"};
        for(int i=0;i<labels.length;i++)nav.addView(navButton(labels[i],i));
        return nav;
    }

    private TextView navButton(String label,int index) {
        boolean selected=currentNav==index;
        TextView b=text(label,11,selected?CYAN:MUTED);
        b.setPadding(0,dp(3),0,dp(3));
        RouteNavIcon icon=new RouteNavIcon(index,selected?CYAN:MUTED);icon.setBounds(0,0,dp(23),dp(23));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(dp(3));
        b.setGravity(Gravity.CENTER);
        b.setTypeface(selected?AppTypography.bold(this):AppTypography.regular(this));
        b.setBackground(selected?background(0xFF102A52,15,0xFF1F5C96):background(0x00000000,15,0));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1f);lp.setMargins(dp(2),0,dp(2),0);b.setLayoutParams(lp);
        b.setOnClickListener(v->openNav(index));
        return b;
    }

    private void setMenuBannerSpace(int adHeight,boolean visible) {
        // Scroll area, navigation and ad each own a row in the same column.
        if(activeMenuNav!=null)activeMenuNav.requestLayout();
        if(activeMenuScroll!=null)activeMenuScroll.requestLayout();
    }

    private void openNav(int index) {
        if(index==currentNav&&index!=0)return;
        onboardingStep=-1;
        if(index==0){showHome();return;}
        if(index==1){showDailyChallengePanel();return;}
        if(index==2){showPremiumStore();return;}
        if(index==3){showAchievements();return;}
        showProfile();
    }

    private void showVisibleMenuBanner() {
        if(onboardingStep>=0||menuBannerSlot==null||menu==null||!menu.isShowing()||banner!=null)return;
        if(!canRequestAds()){
            menuBannerSlot.setVisibility(View.INVISIBLE);
            setMenuBannerSpace(0,false);
            return;
        }
        banner=createAdaptiveBanner(menuBannerSlot,24);
    }

    private void clearMenuBannerSlot() {
        if(menuBannerSlot!=null){menuBannerSlot.removeAllViews();menuBannerSlot=null;}
        activeMenuScroll=null;activeMenuNav=null;
    }

    private void showResultBanner() {
        runOnUiThread(() -> {
            if(resultBannerSlot==null||game==null||!game.isResultScreen()||!canRequestAds()||isFinishing()||isDestroyed())return;
            hideResultBanner();
            suspendGameBanner();
            resultBanner=createAdaptiveBanner(resultBannerSlot,0);
        });
    }

    private void hideResultBanner() {
        if(resultBanner!=null){resultBanner.destroy();resultBanner=null;}
        if(resultBannerSlot!=null){resultBannerSlot.removeAllViews();resultBannerSlot.setVisibility(View.GONE);}
        resultBannerHeightPx=0;
        updateGameBottomInset();
    }

    private void destroyBanner() { if(banner!=null){banner.destroy();banner=null;} }
    private void openPlayStoreRating() {
        String packageName=getPackageName();
        try {
            android.content.Intent marketIntent=new android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id="+packageName));
            marketIntent.setPackage("com.android.vending");
            startActivity(marketIntent);
        } catch (android.content.ActivityNotFoundException error) {
            try {
                startActivity(new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id="+packageName)));
            } catch (android.content.ActivityNotFoundException ignored) {
                toast("Play Store is unavailable on this device.");
            }
        }
    }
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
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus&&foreground&&!showingAd&&music!=null)music.resume();}
    @Override protected void onPause(){cancelPendingBooster();foreground=false;if(music!=null)music.suspend();super.onPause();game.saveProgress();game.setPaused(true);}
    @Override protected void onResume(){super.onResume();foreground=true;if(music!=null&&!showingAd)music.resume();refreshPlayGamesStatus();if(game!=null)game.setPaused(showingAd||(menu!=null&&menu.isShowing())||(reminder!=null&&reminder.isShowing()));if(gameplayBannerRequested&&gameBanner==null&&(menu==null||!menu.isShowing()))showGameBanner();else if(menu!=null&&menu.isShowing())showVisibleMenuBanner();}
    @Override protected void onDestroy(){cancelPendingBooster();if(music!=null)music.release();destroyBanner();stopGameplayBanner();hideResultBanner();if(game!=null)game.removeCallbacks(dailyReminderTask);if(reminder!=null)reminder.dismiss();if(menu!=null)menu.dismiss();if(game!=null)game.release();super.onDestroy();}
}
