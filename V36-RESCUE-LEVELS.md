# Arrow Escape 1.5.0 (versionCode 36)

All 400 campaign boards use dense rectangular serpentine layouts with long multi-bend arrows and a reserved central 7x7 shelter. The centre shows a toy rescue character and a gold escape-route illustration. The goal illustration is decorative: the existing rule remains to clear every puzzle arrow; it is not an extra tappable arrow or a new win condition.

The generator is deterministic, covers every grid node outside the shelter and cannot produce one-node tails. Normal levels have two initial safe moves; Hard (6), Super Hard (12) and Extreme Hard (30) levels have one. The independent solver also checks path geometry, moving-tail safety, dependency depth, possible frontier width, complete solvability and shelter occupancy. Forced self-crossing templates from the previous layout have been replaced by full rectangular coverage; own-tail collision prevention remains unchanged.

The approved V15 snake movement is preserved. Existing coins, booster inventory, stars and unlocked levels remain. A saved in-progress board from an older level pack restarts because its piece IDs no longer match. Daily/weekly challenge assets remain unchanged and do not show the campaign rescue illustration.

## Validation

- Independent solver: all 400 campaign levels solvable; 35–177 puzzle arrows per level.
- Runtime suite: 27 tests passed, including all campaign levels under the real special/tail rules, shelter renders, old-pack migration, music, startup, onboarding, booster inventory and banner layout.
- Wallet suite: 64 assertions passed.
- Deterministic regeneration and UI contract checks passed.
- Release APK signature verified; SHA-256 certificate matches v35 exactly. AAB JAR signature verified.
- Bundletool validation passed. APK package/version/production AdMob IDs and bundled asset checks passed.
- Release lint: zero errors, 21 warnings.
- Signed outputs: original package com.arrowescape.pro, versionName 1.5.0, versionCode 36.

## Production checks still outside this build

Google Play Games Android/OAuth credential setup and a real-device sign-in check are still pending. Music audibility and production ads need phone checks. Play Console declarations, app-ads.txt availability, final policy review and installation of the release APK on a real phone are not established by JVM tests. No Play Store publication is performed.
