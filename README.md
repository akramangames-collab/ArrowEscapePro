# Arrow Escape: Puzzle Maze — V16

Android 6.0+ arrow puzzle with 200 hard mazes, based on successful V15 commit `ad619400a21b71ff4fea35c18a4a292810eea318` (Actions run `34047666833`).

The arrowhead exits straight ahead and its body follows the original bends. V15 route construction, travel, hit testing and collision methods are verified against saved fingerprints. The V15 level-sorter header bug is repaired, and each original layout is extended with blocking arrows to create harder, solvable levels. New layouts progress from 48 to 188 arrows. No fallback layout is needed.

## V16

- Supplied glossy maze/arrow/coin icon, launcher branding and splash screen.
- Persistent coins, puzzle progress, hearts, free hints and erasers.
- 100 starter coins for a fresh wallet. Existing V15 wallet balances and daily streaks are retained on compatible upgrades.
- Each completed attempt: +15 coins, +5 with no blocked taps, +30 on every fifth level. Duplicate completion callbacks cannot grant twice.
- Two free hints per level, then 25 coins each. A repeated tap while a hint is visible does not spend again.
- Daily claim: +100 coins once per UTC day; clock rollback cannot reclaim an older day.
- Earned rewarded ad: +75 coins, or the selected erase/revive benefit. Closing or failing to load an ad grants nothing.
- Optional heart: 40 coins; continue: 60 coins.
- Interstitials only at the next-level transition, after at least five clears and two minutes. Banners appear only in the menu.
- Working sound, vibration, contrast and tutorial controls. Ad requests wait for UMP consent eligibility.

## Build

`.github/workflows/build.yml` builds on pushes to `main` or `v16-puzzle-maze`, and supports Run workflow. Source lives in `app/`; no source is generated inside YAML.

The workflow validates all 200 mazes, checks wallet persistence and duplicate rewards, builds both variants, checks compiled ad IDs/version metadata, runs native Android tests and captures screenshots.

- Debug: `ArrowEscape-PuzzleMaze-V16-test.apk`, official Google test IDs, package `com.arrowescape.pro.debug` so it can coexist with installed production builds.
- Release: `ArrowEscape-PuzzleMaze-V16-release-unsigned.apk`, supplied production IDs, package `com.arrowescape.pro`, version `16.0.1` / code `18`. CI keeps the release APK unsigned so signing credentials stay private. The final V16 download is signed separately with the V16 release key; retain its private backup for future updates. Private signing keys must never be committed. V15 used a different test certificate, so the signed release requires a fresh installation when that V15 APK is already installed.

Production AdMob mapping supplied in the project conversation:

| Format | ID |
|---|---|
| App | `ca-app-pub-2475015099415787~6197424406` |
| Banner | `ca-app-pub-2475015099415787/6590130477` |
| Interstitial | `ca-app-pub-2475015099415787/3782285105` |
| Rewarded | `ca-app-pub-2475015099415787/2469203439` |

Documentation: [Google demo ad IDs](https://developers.google.com/admob/android/test-ads), [reward callbacks](https://developers.google.com/admob/android/rewarded), [UMP consent integration](https://developers.google.com/admob/android/privacy).

A production ID does not guarantee ad fill. AdMob account/app readiness and configured privacy messages remain controlled in AdMob. No live ads are clicked during verification.

## Verification / reproduction

```sh
python3 scripts/verify-levels.py
bash scripts/test-wallet.sh
gradle :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
```

Regenerate the deterministic harder layouts using `python3 scripts/generate-hard-levels.py`. The original recovered V15 layouts are retained only as the generator fixture in `tests/v15-levels.txt`.

## Google Play preparation

The approved V16 gameplay and wallet are preserved. Store build 16.0.1 / code 18 adds an offline privacy-policy screen, production App Bundle output and 1080x1920 listing captures. GitHub Actions uploads `ArrowEscape-V16-Play-Bundle`; sign this bundle privately with the existing V16 key before the first Play upload. The original signed V16 APK remains available separately.

English listing text is in `play-store/`. The privacy text in `PRIVACY.md` matches `app/src/main/assets/privacy-policy.txt`. Select the intended audience and complete the publisher/account declarations in Play Console before submitting the app.
