# Arrow Escape 1.4.0 / version code 34

## Implemented

The Route screen now uses connections between tile edges instead of tracks through numbers. Completed paths are cyan; future paths are subdued solid tracks. Stars sit inside completed tiles and the current-level/difficulty labels are separate side badges. A compact header and six-node phone layout leave more room for progression. The 400 harder puzzles, all four visible numeric booster counts, one free hint per attempt, optional direct rewarded refills, checkpoint gifts and original offline music remain.

The production candidate uses the original `com.arrowescape.pro` package and the four original AdMob identifiers, independently matched against v27's signed AAB. Debug tests continue to use test units; live ads are not clicked during QA.

The signing key was recovered from the private 1.0.3 signing backup. Its certificate matches the last v27 signed AAB, not merely a similarly named key:

- SHA-1: BF:3C:C7:8E:44:7E:32:09:B9:8E:76:59:24:97:34:61:0A:D6:FE:97
- SHA-256: C0:F5:B8:4B:37:5B:47:75:BC:C6:7C:37:E0:57:A2:6C:B3:D6:54:FE:79:38:D4:8B:65:8E:B4:23:E4:41:42:9F

Older backup keys with different fingerprints were rejected. No key reset or new production key was created. The APK is upload-key signed; Play-distributed APKs may use Google's separate app-signing certificate, so an APK sideload is not guaranteed to replace a Play-installed copy. Use the AAB in an internal test track for the Play-signed update path.

## Google Play Games client

The release client initializes PGS v2 for project 841960223704, exposes only Google Play Games sign-in and retrieves the signed-in player display name. Automatic creation prompts are suppressed so onboarding remains accessible. Offline gameplay stays available on configuration failure. This is a client integration, not a verified Console OAuth configuration and not cloud progress sync.

Required Console verification:

1. Confirm project 841960223704 belongs to this original package.
2. Link Android credentials for `com.arrowescape.pro` using the **Play app-signing SHA-1**, copied from App integrity. This may differ from the upload-key SHA-1 above.
3. For sideload APK authentication, also link an Android credential with the upload-key SHA-1 above.
4. Configure consent, tester accounts/release-track access, then test authentication on an actual device. Publish PGS settings before the production app release.

https://developer.android.com/games/pgs/console/setup
https://developer.android.com/games/pgs/android/android-signin

Console access was blocked by automatic browser approval review: it reported an origin mismatch (`google.play` versus the requested `play.google.com/console`). No alternate browser route, API access or credential circumvention was used.

## Play checks

- Target SDK 36, compile SDK 36.1; resizable activity, no orientation restriction.
- Core-library desugaring 2.1.5 enabled for the AdMob Next-Gen SDK's Java API compatibility below API 26; app min SDK remains 24.
- G-rated, child-directed ad request configuration retained.
- Production app-ads.txt returned HTTP 200 with the matching publisher entry. The public listing points to taashgame.com.
- R8 enabled, mapping included in the AAB; separate mapping delivered for support.
- No packaged native `.so` libraries found in the bundle: native 16 KB alignment requirements do not introduce an app-native-library blocker here.
- HSDP vendor SHORT_EDGES reference remains; local fixes do not guarantee this Console recommendation disappears.

Sources:
https://developers.google.com/admob/android/next-gen/rel-notes
https://developer.android.com/guide/practices/page-sizes
https://play.google.com/store/apps/details?id=com.arrowescape.pro

## Remaining release gates

The public listing currently reports no data collected/shared. Google's AdMob disclosure documents automatic SDK data handling (IP-derived location, interactions, diagnostics and identifiers; actual configuration can limit some categories). Review and correct the Data Safety form for the configured SDK behavior before rollout. The listing also still describes 200 puzzles; update it to 400 with the new booster rules. No Console form was modified or production release published.

https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure

The signed files are **production candidates**. Actual-device ads, music and Play Games authentication; latest Console version-code availability; policy forms and pre-launch review remain unverified. Do not describe this as guaranteed error-free or already published.

## Completed local validation

24 Android runtime tests pass, including startup on APIs 24/30/35/36 and all 400 puzzle solutions. UI contract passes. Release lint: 0 errors, 21 warnings. APK signature verifies; AAB JAR signature verifies and bundletool validation passes. Signed package/version/target: com.arrowescape.pro / 1.4.0 / code 34 / SDK 36. Native Route render inspected. This is not a Play Console pre-launch result.
