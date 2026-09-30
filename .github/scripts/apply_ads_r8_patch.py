from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# Keep the public version name at 1.0.3, but use a fresh Play versionCode.
gradle_path = Path("app/build.gradle.kts")
g = gradle_path.read_text(encoding="utf-8")
g = replace_once(g, "versionCode = 4", "versionCode = 5", "versionCode")
if 'versionName = "1.0.3"' not in g:
    raise SystemExit("Expected versionName 1.0.3")

# User explicitly requested real production AdMob IDs in every build variant.
g = g.replace("ca-app-pub-3940256099942544~3347511713", "ca-app-pub-2475015099415787~6197424406")
g = g.replace("ca-app-pub-3940256099942544/6300978111", "ca-app-pub-2475015099415787/6590130477")
g = g.replace("ca-app-pub-3940256099942544/1033173712", "ca-app-pub-2475015099415787/3782285105")
g = g.replace("ca-app-pub-3940256099942544/5224354917", "ca-app-pub-2475015099415787/2469203439")
if "ca-app-pub-3940256099942544" in g:
    raise SystemExit("A Google test ad ID is still present")

release_old = '''        getByName("release") {
            isDebuggable = false
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")'''
release_new = '''        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("release")'''
g = replace_once(g, release_old, release_new, "release R8 config")
gradle_path.write_text(g, encoding="utf-8")

Path("app/proguard-rules.pro").write_text(
    "# Arrow Escape production R8 rules.\n"
    "# Google Mobile Ads and UMP provide consumer ProGuard rules.\n"
    "-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod\n"
    "-keep public class com.arrowescape.pro.MainActivity { public <init>(); }\n",
    encoding="utf-8",
)

main_path = Path("app/src/main/java/com/arrowescape/pro/MainActivity.java")
s = main_path.read_text(encoding="utf-8")

s = replace_once(
    s,
    "    private AdView banner;\n",
    "    private AdView banner;\n    private AdView resultBanner;\n    private FrameLayout root, resultBannerSlot;\n",
    "ad fields",
)

s = replace_once(
    s,
    """        game = new ArrowGameView(this, this, wallet);\n        setContentView(game);""",
    """        game = new ArrowGameView(this, this, wallet);\n        root = new FrameLayout(this);\n        FrameLayout.LayoutParams gameParams = new FrameLayout.LayoutParams(\n            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);\n        root.addView(game, gameParams);\n        resultBannerSlot = new FrameLayout(this);\n        resultBannerSlot.setBackgroundColor(INK);\n        resultBannerSlot.setVisibility(View.GONE);\n        FrameLayout.LayoutParams resultSlotParams = new FrameLayout.LayoutParams(\n            FrameLayout.LayoutParams.MATCH_PARENT, dp(60), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);\n        root.addView(resultBannerSlot, resultSlotParams);\n        setContentView(root);""",
    "root layout",
)

# UMP consent remains authoritative before any live ad request.
s = replace_once(
    s,
    """        consent = UserMessagingPlatform.getConsentInformation(this);\n        if (BuildConfig.DEBUG) { startAdsIfAllowed(); return; }\n        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();""",
    """        consent = UserMessagingPlatform.getConsentInformation(this);\n        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();""",
    "debug consent bypass",
)
s = replace_once(
    s,
    "        if (adsStarted || (!BuildConfig.DEBUG && !consent.canRequestAds()) || isDestroyed()) return;",
    "        if (adsStarted || consent == null || !consent.canRequestAds() || isDestroyed()) return;",
    "start ads consent gate",
)
s = replace_once(
    s,
    "    private boolean canRequestAds() { return adsStarted && consent != null && (BuildConfig.DEBUG || consent.canRequestAds()) && !isDestroyed(); }",
    "    private boolean canRequestAds() { return adsStarted && consent != null && consent.canRequestAds() && !isDestroyed(); }",
    "canRequestAds consent gate",
)

# Result screen gets a dedicated banner. It is removed before navigation/interstitial.
s = replace_once(
    s,
    "    @Override public void onLevelCompleted() { completedSinceAd++; }",
    "    @Override public void onLevelCompleted() { completedSinceAd++; showResultBanner(); }",
    "result banner trigger",
)
s = replace_once(
    s,
    """    @Override public void onContinueAfterWin(Runnable proceed) {\n        long now = SystemClock.elapsedRealtime();""",
    """    @Override public void onContinueAfterWin(Runnable proceed) {\n        hideResultBanner();\n        long now = SystemClock.elapsedRealtime();""",
    "hide result banner before continuation",
)
s = replace_once(
    s,
    """    @Override public void openWallet() { showPremiumStore(); }\n    @Override public void openSettings() { showMenu(false); }\n    @Override public void openHome() { showHome(); }""",
    """    @Override public void openWallet() { hideResultBanner(); showPremiumStore(); }\n    @Override public void openSettings() { hideResultBanner(); showMenu(false); }\n    @Override public void openHome() { hideResultBanner(); showHome(); }""",
    "navigation banner cleanup",
)

# Home, Store, Achievements and Daily Challenge all pass through this presenter.
s = replace_once(
    s,
    """        game.setPaused(true);\n        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(INK);scroll.addView(body);""",
    """        game.setPaused(true);\n        addMenuBanner(body);\n        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(INK);scroll.addView(body);""",
    "premium screen banner",
)

anchor = "    private void destroyBanner() { if(banner!=null){banner.destroy();banner=null;} }"
helpers = '''    private void addMenuBanner(LinearLayout body) {\n        FrameLayout slot = new FrameLayout(this);\n        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(66));\n        lp.topMargin = dp(16);\n        body.addView(slot, lp);\n        if (!canRequestAds()) { slot.setVisibility(View.GONE); return; }\n        banner = new AdView(this);\n        banner.setAdSize(AdSize.BANNER);\n        banner.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);\n        slot.addView(banner, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));\n        banner.loadAd(new AdRequest.Builder().build());\n    }\n\n    private void showResultBanner() {\n        runOnUiThread(() -> {\n            if (resultBannerSlot == null || !canRequestAds() || isFinishing() || isDestroyed()) return;\n            hideResultBanner();\n            FrameLayout.LayoutParams gameParams = (FrameLayout.LayoutParams) game.getLayoutParams();\n            gameParams.bottomMargin = dp(60);\n            game.setLayoutParams(gameParams);\n            resultBanner = new AdView(this);\n            resultBanner.setAdSize(AdSize.BANNER);\n            resultBanner.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);\n            FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);\n            resultBannerSlot.removeAllViews();\n            resultBannerSlot.addView(resultBanner, adParams);\n            resultBannerSlot.setVisibility(View.VISIBLE);\n            resultBanner.loadAd(new AdRequest.Builder().build());\n        });\n    }\n\n    private void hideResultBanner() {\n        if (resultBanner != null) { resultBanner.destroy(); resultBanner = null; }\n        if (resultBannerSlot != null) {\n            resultBannerSlot.removeAllViews();\n            resultBannerSlot.setVisibility(View.GONE);\n        }\n        if (game != null && game.getLayoutParams() instanceof FrameLayout.LayoutParams) {\n            FrameLayout.LayoutParams gameParams = (FrameLayout.LayoutParams) game.getLayoutParams();\n            if (gameParams.bottomMargin != 0) {\n                gameParams.bottomMargin = 0;\n                game.setLayoutParams(gameParams);\n            }\n        }\n    }\n\n''' + anchor
s = replace_once(s, anchor, helpers, "banner helpers")

s = replace_once(
    s,
    '            body.addView(text("Version "+BuildConfig.VERSION_NAME+(BuildConfig.DEBUG?" · Test ads":""),12,MUTED));',
    '            body.addView(text("Version "+BuildConfig.VERSION_NAME,12,MUTED));',
    "remove test ads label",
)
s = replace_once(
    s,
    "    @Override protected void onPause(){super.onPause();game.saveProgress();game.setPaused(true);if(banner!=null)banner.pause();}",
    "    @Override protected void onPause(){super.onPause();game.saveProgress();game.setPaused(true);if(banner!=null)banner.pause();if(resultBanner!=null)resultBanner.pause();}",
    "pause result banner",
)
s = replace_once(
    s,
    "    @Override protected void onResume(){super.onResume();if(game!=null)game.setPaused(showingAd||(menu!=null&&menu.isShowing())||(reminder!=null&&reminder.isShowing()));if(banner!=null)banner.resume();}",
    "    @Override protected void onResume(){super.onResume();if(game!=null)game.setPaused(showingAd||(menu!=null&&menu.isShowing())||(reminder!=null&&reminder.isShowing()));if(banner!=null)banner.resume();if(resultBanner!=null)resultBanner.resume();}",
    "resume result banner",
)
s = replace_once(
    s,
    "    @Override protected void onDestroy(){destroyBanner();if(game!=null)game.removeCallbacks(dailyReminderTask);if(reminder!=null)reminder.dismiss();if(menu!=null)menu.dismiss();if(game!=null)game.release();super.onDestroy();}",
    "    @Override protected void onDestroy(){destroyBanner();hideResultBanner();if(game!=null)game.removeCallbacks(dailyReminderTask);if(reminder!=null)reminder.dismiss();if(menu!=null)menu.dismiss();if(game!=null)game.release();super.onDestroy();}",
    "destroy result banner",
)

main_path.write_text(s, encoding="utf-8")

checks = {
    "version code": "versionCode = 5" in g,
    "version name": 'versionName = "1.0.3"' in g,
    "R8": "isMinifyEnabled = true" in g and "isShrinkResources = true" in g,
    "production banner": "ca-app-pub-2475015099415787/6590130477" in g,
    "production interstitial": "ca-app-pub-2475015099415787/3782285105" in g,
    "production rewarded": "ca-app-pub-2475015099415787/2469203439" in g,
    "no test IDs": "ca-app-pub-3940256099942544" not in g,
    "result banner": "showResultBanner()" in s,
    "premium banner": "addMenuBanner(body)" in s,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Patch validation failed: " + ", ".join(failed))
print("Production ads + R8 patch validated")
