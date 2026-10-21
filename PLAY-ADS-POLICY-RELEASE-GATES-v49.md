# Arrow Escape v49 — Families Ad Content release gate

**NOT YET CLEARED BY GOOGLE PLAY. Successful code tests are not policy approval.**

## Enforcement / facts
- Google rejected *version code 42* on 20 October 2026: advertising content inconsistent with the app's content rating. Google explicitly requested excluding sensitive categories.
- Public store listing has content rating **Everyone**, says **Contains ads**, and currently declares **No data collected / No data shared**; these declarations require review for the current Google Mobile Ads SDK 25.5.0.
- Google documents SDK collection/sharing of IP addresses, app interactions, diagnostics and device/account identifiers. Removing the Android AD_ID permission does not stop all other data types.

## Code changes in v49
- Google Mobile Ads SDK 25.5.0 (classic), Google UMP 4.0.0.
- Every ad request configured G maximum and child-directed before GMA initialization.
- AD_ID merged permission removed.
- Consent gated before ad requests, including async returns; v49 clears cached banners/interstitials/rewarded ads when permission withdrawn.
- In-app privacy policy and repository policies updated to reflect SDK Legacy 25.5.0 data disclosure, and not outdated Next-Gen documentation.
- Original v42 cage art, arrow levels and four primary boosters kept. Safe Scan and Triple Escape add-on preserved.

## AdMob account — REQUIRED MANUAL SETTINGS
Go to https://admob.google.com > Apps > Arrow Escape > Blocking controls.

- Set app maximum ad content rating **G (General audiences)**. Global SDK also sets G; align app UI.
- Manage Sensitive categories: block inappropriate material, particularly **Social Casino Games**, dating and adult romance, sex-related ads, alcohol, gambling, weight loss/body image and other unsafe content as available in your region. Restricted categories generally start blocked; do not enable them.
- Manage General categories: prevent ads for mature games, mature apps, films and other adult media. Review real ads through the Ad Review Center. Filters are best-effort; block advertisers/URLs of offending creatives.
- Verify account-side mediation, bidding and ad sources are appropriate for children; client code cannot verify external creative quality or server settings.
- Confirm app-ads.txt is served from the developer website root used in the Play listing. A repo-root file by itself does not prove domain installation.

## Google Play Console — REQUIRED MANUAL DECLARATIONS
- App content / Target audience: verify actual child / older age brackets. An Everyone content rating is NOT the same as declared target ages. Mixed child/adult targeting may require a neutral age screen.
- App content / Content rating: review questionnaire and ensure game content, characters, ads and target ages match declarations.
- App content / Data safety: published "No data collected/shared" requires correction for Google Mobile Ads. Check IP as approximate location, app interactions, diagnostics/performance and device/app-set identifiers, plus any services beyond AdMob. Carefully evaluate collection, sharing (including applicable service-provider exemptions), purposes, optionality and deletion; use Google's SDK disclosure rather than guess.
- App content / Ads declaration: clearly declare all banners, post-level interstitials and voluntary rewarded ads.
- Store listing / Privacy policy URL: confirm the PUBLIC link without login shows the UPDATED text; local APK text does not automatically update the URL.
- Publishing overview: submit revised signed AAB for Google review only once the above is complete.

## Real-device required tests
- Inspect actual banner, rewarded and interstitial creative appearance with an actual device, including after every second completed level, for misleading, objectionable, non-closeable, or adult content.
- Verify ad identification, family-safe placement without accidental clicks and layout on phones/tablets.
- Test Settings > Privacy choices where available; withdrawing permission must hide all ads and prevent late loaded ads.
- Test offline play and intact v42 cage/character/gold key, as well as four old plus two new boosters.

## Official references
Google Play Families: https://support.google.com/googleplay/android-developer/answer/9893335
Families certified SDK program: https://support.google.com/googleplay/android-developer/answer/9900633
AdMob sensitive categories: https://support.google.com/admob/answer/3150953
Blocking controls: https://support.google.com/admob/answer/3150176
G rating: https://support.google.com/admob/answer/7562142
SDK 25.5.0 Data safety: https://developers.google.com/admob/android/privacy/play-data-disclosure

## Verification boundary
Source and CI can verify ad configuration, consent code, compile/lint/tests and signed APK/AAB certificates. They CANNOT verify AdMob blocking controls, the public Play Data safety declaration, live creatives, or Google acceptance. **Do not call this compliant until the account steps and Play review pass.**
