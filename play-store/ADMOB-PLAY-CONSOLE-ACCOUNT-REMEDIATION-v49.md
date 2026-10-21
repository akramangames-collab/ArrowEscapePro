# Arrow Escape v49 — AdMob + Google Play Console corrections

**Status: account actions are still pending. This file does not change AdMob or Play Console.**

## Confirmed incident
Google Play rejected code 42 on 20 October 2026 for **Ad Content inconsistent with app content rating**, and told the publisher to exclude sensitive advertising categories. The current public Play Store Data safety section says **No data collected** and **No data shared**, which must be reassessed for an app using AdMob 25.5.0. Code 49 is only a new candidate and has **not** been reviewed by Play.

## AdMob: required account settings
1. Open https://admob.google.com > Apps > Arrow Escape: Puzzle Maze (app ID should match release AdMob app ID in Gradle) > Blocking controls > Manage ad content rating.
2. Switch OFF "Match account-level setting" and set **G — General audiences**; Save. SDK requests already specify G but account-level defense in depth matters.
3. Manage Sensitive categories > **Block Social Casino Games**. This standard sensitive category is ALLOWED BY DEFAULT and is expressly prohibited for children by Google's Families ad policy even where no real money is awarded.
4. Review and block other sensitive or mature categories where actually available in this account, particularly **Weight loss** and **Birth control**, and check all restricted categories stay blocked. Some categories are deprecated/region-specific; do not claim an absent category has been blocked. As appropriate block adult/dating, gambling, alcohol, and sexually suggestive advertisers using general category and advertiser URL controls or Ad Review Center. Remember "Dating" is not always a standard sensitive category in every region.
5. Review **general categories** for mature movies, games and software, and block problematic advertisers.
6. Review **Ad review center** and record sample creatives actually shown for banners, rewarded ads and post-level interstitials. Block offensive specific ads or advertiser URLs.
7. Check **mediation, bidding and ad sources**. Client dependency analysis shows only the Google Mobile Ads SDK, no third-party mediation SDK in the Android Gradle file; this does not reveal account-side bidding. Disable any ad sources that cannot guarantee Families creative review/child-directed privacy signaling.
8. Confirm AdMob account/app readiness, restricted-ad warnings, monetization approvals and **app-ads.txt** availability from the root of the developer website associated with the Play listing; the repo-root app-ads.txt alone is insufficient.

**Evidence:** Capture a screenshot after saving each setting; verify a few real ad impressions on a physical device. App-level settings may override account-level settings. Google states SDK maximum ad content rating overrides AdMob UI if it differs.

## Google Play Console: Data safety correction
Play Console > Policy and programs > App content > Data safety > Start/edit.

The **user data collection** question should be **YES** for active network AdMob SDK 25.5.0 processing, not the currently published "No data collected."

Types to examine for collection and for sharing, per the official SDK disclosure and Play categorization:
- **Approximate location**: IP address may be used to infer coarse location (no GPS or location permission).
- **App activity > App interactions**: app opens, taps, ad interactions, video views.
- **App info and performance > Diagnostics**: SDK/app launch, latency, freezes, energy use.
- **Device or other IDs**: app-set ID and applicable account/device identifiers. The Android **AD_ID** permission is removed in the compiled v49 release, so do NOT blindly declare use of AAID, but do examine other SDK IDs.
- **Additional SDKs/features**: Google Play Games optional sign-in, User Messaging Platform, or other SDKs must also be assessed according to their current privacy docs and real runtime behavior.

All data automatically collected by the Google Mobile Ads SDK is encrypted in transit with TLS. Purposes may include advertising/marketing, analytics, app functionality, security/fraud prevention; confirm classifications individually. Determine mandatory/optional for each data type from the actual consent/age behavior. The **shared** checkbox must be evaluated against Google's precise definition of sharing, applicable *service provider* exemptions, and your Google data-processing configuration — do not categorically assume YES or NO only because Google's SDK documentation uses the word "share". The Data safety answers must represent *all distributed versions* and services, not just the latest version.

For local storage, the game keeps coins, hearts and progress; local-only processing does not itself become collection in Play's form unless transmitted off device. User deletion options must accurately describe both local data deletion and any data transmitted to Google. Provide truthful information on data retention and deletion.

**Preferred import workflow:** in Play Console, first **Export CSV** for the current Data safety form or **Download sample CSV**; edit only its official response fields and **Import CSV**. Do NOT invent a partial CSV or replace columns/IDs, and **review every row before Submit** because imports overwrite saved answers. If using the Android Publisher API, it accepts the same completed CSV text through the dataSafety endpoint, but this requires properly authorized credentials.

## Other Play checks
- App content > **Target audience and content**: use the actual intended ages; an Everyone rating is not an age declaration. A mixed audience may require a neutral age screen unless a fully child-safe treatment makes all audience choices compliant. Do not misstate intended audience to evade Families policy.
- App content > **Content rating**: review the questionnaire, original rescue character and advertising content against the Everyone rating.
- App content > **Ads**: declare banner, voluntary rewarded and post-result interstitial ads.
- Public **privacy policy URL**: ensure the live URL in Play Console loads the updated privacy policy for all visitors without authentication. App's internal asset is corrected in v49, but it does NOT automatically update a publicly hosted website.
- Publishing overview: submit the corrected declarations and signed package only after ad-account blocking and live ad audits, then await Google's approval.

## Google official sources
Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
Google Mobile Ads SDK 25.5.0 Data safety: https://developers.google.com/admob/android/privacy/play-data-disclosure
Google Play Families: https://support.google.com/googleplay/android-developer/answer/9893335
AdMob sensitive categories: https://support.google.com/admob/answer/3150953
AdMob maximum content rating: https://support.google.com/admob/answer/7562142
Android Publisher API: https://developers.google.com/android-publisher/api-ref/rest/v3/applications/dataSafety

**Do not claim account settings were changed or that Google accepted the release without evidence.**
