# Arrow Escape: Puzzle Maze — Production Launch Checklist

## Release decision

Production access is granted.

`1.0.2` / `versionCode 3` is already on Google Play Early Access/testing. The current repository contains later improvements, so the new Production candidate is `1.0.3` / `versionCode 4`.

Do not upload another code-3 bundle.

## Repository checks completed

- [x] Package is `com.arrowescape.pro`.
- [x] `compileSdk = 36`.
- [x] `targetSdk = 36`.
- [x] Production candidate is `1.0.3` / code `4`.
- [x] Code 3 is already present on Google Play Early Access/testing.
- [x] Debug build uses Google test AdMob IDs.
- [x] Release build uses production AdMob IDs.
- [x] Release signing is environment/secret driven; private keys are not stored in source.
- [x] UMP consent information is refreshed at app launch.
- [x] Ads are gated by `canRequestAds()` in release builds.
- [x] Settings exposes Privacy choices when UMP requires an entry point.
- [x] Privacy policy describes local game data and Google advertising data processing.
- [x] Gameplay validation scripts cover generated levels/solvability.
- [x] Wallet verification and UI/UX verification are part of the release workflow.
- [x] Built-in tutorial / How to Play is present.
- [x] Rate App action is present in Settings.
- [x] High contrast arrows accessibility option is present.

## Signed AAB checks

- [ ] GitHub Actions secrets exist: `ARROW_RELEASE_KEYSTORE_B64`, `ARROW_RELEASE_STORE_PASSWORD`, `ARROW_RELEASE_KEY_PASSWORD`.
- [ ] Manual `Arrow Escape 1.0.3 Signed Play Release` workflow succeeds.
- [ ] Artifact name is `Arrow-Escape-1.0.3-SIGNED-PLAY-AAB`.
- [ ] Final file is `PLAY-STORE-Arrow-Escape-1.0.3-SDK36-SIGNED.aab`.
- [ ] Workflow verifies the pinned upload certificate SHA-256.
- [ ] SHA256SUMS.txt is retained with the release artifact.

## Play Console checks before pressing Start rollout

- [ ] Production access shows as enabled.
- [ ] Store listing has no blocking errors.
- [ ] App content has no incomplete declarations.
- [ ] Ads declaration = Yes.
- [ ] Data safety reflects Google Mobile Ads SDK processing.
- [ ] Content rating is active and valid.
- [ ] Target audience is intentional and consistent with ad settings.
- [ ] Privacy-policy URL is live and points to the current policy.
- [ ] Support email is current and monitored.
- [ ] Countries/regions and pricing are correct.
- [ ] New Production release uses `1.0.3` / code `4` AAB.
- [ ] Production release notes are correct.
- [ ] Pre-launch report has no release-blocking crash/ANR/security issue.
- [ ] Device catalog does not show an unexpected compatibility restriction.

## Recommended first rollout

Use a staged rollout for the first public production launch. Start small, watch Android vitals and Play Console feedback, then increase exposure when the release is stable.

Monitor at minimum:

- crash rate;
- ANR rate;
- install/update failures;
- user reviews and support messages;
- AdMob policy centre and serving status;
- progression/economy complaints, especially rewards and ads;
- any level that users report as impossible.

## Versioning after this release

After code 4 is uploaded, every future new binary must use `versionCode >= 5`.
