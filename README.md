# Arrow Escape: Puzzle Maze

Current release-hardening line for the Android arrow puzzle game.

## Current build

- Version: `1.0.2`
- Version code: `3`
- Package: `com.arrowescape.pro`
- Minimum SDK: `24`
- Compile SDK: `36`
- Target SDK: `36`
- Java: `17`
- Active hardening branch: `v26-release-hardening`

## Gameplay baseline

The approved V15 movement model remains the gameplay foundation: the arrow head travels forward while the body follows the original path like a snake. Later releases preserve that movement while adding full escape validation, including own-tail/body clearance and blocking by other live arrows.

The current product line retains the 200-level campaign, persistent progression/economy, hearts, hints, Daily Challenge, streak rewards, achievements, store/settings surfaces, production AdMob IDs in release and Google test IDs in debug.

## Release signing

Private signing material must never be committed to this repository.

`app/build.gradle.kts` supports release signing only when all four environment values are present:

- `ARROW_RELEASE_STORE_FILE`
- `ARROW_RELEASE_STORE_PASSWORD`
- `ARROW_RELEASE_KEY_ALIAS`
- `ARROW_RELEASE_KEY_PASSWORD`

The GitHub release workflow maps those values from these repository secrets:

- `ARROW_RELEASE_KEYSTORE_B64` — base64 encoded existing release/upload keystore
- `ARROW_RELEASE_STORE_PASSWORD`
- `ARROW_RELEASE_KEY_ALIAS`
- `ARROW_RELEASE_KEY_PASSWORD`

Use the same existing release/upload key that was used for the Play Store app. Do not create a replacement key unless the Play Console key-management process explicitly requires it.

## GitHub Actions policy

To avoid wasting Actions minutes, the release-hardening branch has one workflow only:

`.github/workflows/aab-1.0.1.yml`

It is `workflow_dispatch` only. Normal pushes do not start a build.

When manually started, it:

1. Fails immediately if any signing secret is missing.
2. Installs Android SDK 36.
3. Restores the private keystore only inside the runner temporary directory.
4. Verifies the deterministic level pack, clearance/solvability rules, wallet tests and UI/UX contract.
5. Runs Android lint.
6. Builds the release AAB with the configured signing key.
7. Uses `jarsigner -verify -strict` so an unsigned bundle cannot be published as a release artifact.
8. Uploads `PLAY-STORE-Arrow-Escape-1.0.2-SDK36-SIGNED.aab` plus its SHA-256 checksum.

Legacy V16/V22/V23/V24/V30 auto-build and self-modifying workflows are intentionally removed from this branch.

## Local/static verification

The repository contains the existing validation scripts used throughout development:

```sh
python3 scripts/generate-hard-levels.py --check
python3 scripts/verify-levels.py
bash scripts/test-wallet.sh
python3 scripts/verify-ui-ux.py
```

For an Android build with a configured SDK/Gradle environment:

```sh
gradle :app:lintDebug :app:assembleDebug :app:bundleRelease
```

Without the four release-signing environment variables, Gradle may still produce an unsigned release artifact for development checks. Such an artifact is **not** the final Play Store update bundle.

## Ads

Debug builds use Google's official test ad IDs. Release builds keep the production AdMob IDs already configured for Arrow Escape. Production ads must not be clicked during verification.

## Play Store release rule

Only upload the manually generated artifact whose signing verification passes with the existing Play release/upload identity. Version `1.0.2` / code `3` is already configured for SDK 36.
