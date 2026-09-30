# Arrow Escape: Puzzle Maze — Production Launch Checklist

## Release decision

For the first public launch, prefer the exact tested Play Console artifact already used in closed testing: `1.0.2` / `versionCode 3`, provided that is the code-3 artifact shown in Play Console.

If a different binary from the current repository is to be uploaded instead, bump to `versionCode 4` or higher first. Google Play version codes are monotonically increasing and cannot be reused for a different upload.

## Repository checks completed

- [x] Package is `com.arrowescape.pro`.
- [x] `compileSdk = 36`.
- [x] `targetSdk = 36`.
- [x] Current source version is `1.0.2` / code `3`.
- [x] Debug build uses Google test AdMob IDs.
- [x] Release build uses production AdMob IDs.
- [x] Release signing is environment/secret driven; private keys are not stored in source.
- [x] Manual Play AAB workflow verifies the pinned upload certificate.
- [x] UMP consent information is refreshed at app launch.
- [x] Ads are gated by `canRequestAds()` in release builds.
- [x] Settings exposes Privacy choices when UMP requires an entry point.
- [x] Privacy policy describes local game data and Google advertising data processing.
- [x] Gameplay validation scripts cover generated levels/solvability.
- [x] Wallet verification and UI/UX verification are part of the release workflow.

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
- [ ] Release notes are entered for the production release.
- [ ] Pre-launch report has no release-blocking crash/ANR/security issue.
- [ ] Device catalog does not show an unexpected compatibility restriction.

## Recommended first rollout

Use a staged rollout for the first public production launch. Start small, watch Android vitals and Play Console feedback, then increase exposure when the release is stable.

Monitor at minimum:

- crash rate;
- ANR rate;
- excessive wakeups or battery issues if reported;
- install/update failures;
- user reviews and support messages;
- AdMob policy centre and serving status;
- progression/economy complaints, especially rewards and ads;
- any level that users report as impossible.

## Next update

After production code 3 is live, the next new binary must use `versionCode >= 4`. A normal next semantic version is `1.0.3` / code `4`.
