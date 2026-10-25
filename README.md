# Arrow Escape: Puzzle Maze

Android package: `com.arrowescape.pro` · **v1.8.0 candidate (versionCode 51)** · minimum Android API 24 · target API 36.

## Gameplay and release contents

Arrow heads move forward while their bodies follow the entire winding path. A move is legal only if the head's whole escape route is clear of other arrows and its own moving tail. The original V42 cage, key box, flying key animation, rescue character art and classic four-button HUD are preserved.

V52 adds **1,000 distinct deterministic campaign levels**, water rising during active puzzle time, rescue character expressions at water danger thresholds, and a timed level failure when the cage fills. Levels 1–5 are introductory (120 seconds); later levels have one unassisted removal order by the independent solver. **The gold cage-key arrow must be tapped manually last** and cannot be erased, bombed, swept or removed through Triple Escape. A blocked booster does not spend stock.

The supported booster inventory is Hint, Eraser, Bomb, Sweep, Safe Scan and Triple Escape. Mistake Shield is retired. Separately, the earned Flow Shield protects at most one mistake in a level attempt (including after resuming a save). Replays cannot collect the same level's coins twice.

See [WATER-RESCUE.md](WATER-RESCUE.md) for rules, timer/resume behavior, and balancing limitations.

## Verify a release candidate

```bash
python3 scripts/generate-water-levels.py --check
python3 scripts/verify-water-levels.py
python3 scripts/verify-levels.py
bash scripts/test-wallet.sh
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease
```

The GitHub Actions workflow `.github/workflows/v42-restore-release.yml` checks preserved cage/character hashes, level pack generation, the independent 1,000-level solver, runtime tests, lint, release APK/AAB, and **absence of AD_ID from the compiled release APK**. It creates GitHub Actions artifacts. Do not treat a successful CI build as physical-device gameplay QA or Google Play policy approval.

## Release signing

**Never commit the keystore or passwords.** Release signing requires the original Google Play upload key. Gradle takes the environment variables:

- `ARROW_RELEASE_STORE_FILE`
- `ARROW_RELEASE_STORE_PASSWORD`
- `ARROW_RELEASE_KEY_ALIAS`
- `ARROW_RELEASE_KEY_PASSWORD`

The workflow can use repository secrets `ARROW_RELEASE_KEYSTORE_B64`, `ARROW_RELEASE_STORE_PASSWORD`, and `ARROW_RELEASE_KEY_PASSWORD`, with the known original alias `arrowescape-upload`. When secrets are present on a trusted branch push, it verifies the original upload certificate fingerprint and signs the APK/AAB. Without them, the workflow emits clearly labeled **UNSIGNED** files; those cannot be uploaded to Google Play as final release artifacts. The signing step is disabled for pull-request events.

## Ads, privacy, and publishing gate

Debug builds use Google test ad IDs; release builds use the existing production AdMob IDs. Google Mobile Ads / UMP consent-gating is implemented in app code with child-directed and G-rated ad requests. This does **not** prove AdMob account-side blocking controls or real creatives are safe. Review [PLAY-ADS-POLICY-RELEASE-GATES-v49.md](PLAY-ADS-POLICY-RELEASE-GATES-v49.md) and the updated [privacy policy](PRIVACY_POLICY.md).

Before publishing: test on a real phone (including menus, water timeouts, booster protection, resume, ad delivery and ad-consent withdrawal), verify production signing, update accurate Play Console Data Safety/target audiences/content rating information, and check the public privacy policy URL. Do **not** publish V52 on CI alone.

## Official website and support

Developed by [AK Software Labs](https://aksoftwarelabs.tech/).

- [Arrow Escape product page](https://aksoftwarelabs.tech/apps/arrow-escape/)
- App support: [support@aksoftwarelabs.tech](mailto:support@aksoftwarelabs.tech)
- [Other AK Software Labs projects](https://aksoftwarelabs.tech/)
