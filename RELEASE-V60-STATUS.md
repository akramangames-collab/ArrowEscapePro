# Arrow Escape V60 review status — 2 November 2026

Source baseline: final V59 review build `8a1208c8ae4bcff9f69752bcf73fcef9caa9e257`.
Version: `2.0.5-review`, version code `60`, package `com.arrowescape.pro`.

## Added

- Chapter cards offer a 200-coin gift after all 40 saved campaign levels have at least one star.
- Chapter completion is read from the existing `arrow_puzzle_faithful` progress store, so existing completed chapters qualify.
- Coin credit and chapter receipt commit in one wallet transaction. Failed saves do not consume the claim; repeat taps and app recreation do not duplicate it.
- Claimed cards are disabled; confirmation shows the credited amount and current balance.
- Tests cover incomplete chapters, invalid chapters, failed persistence, recreated wallets, the final chapter and claims through the native chapter page.
- The review workflow runs the full debug and release unit suites and names artifacts/version checks for V60.

## Verified locally

- Wallet: 81 assertions passed using the installed JDK compiler module.
- Campaign: 1,000 distinct layouts, 66,450 unique-sequence moves, golden key last, self-tail clearance, four-sided cage shelter.
- Daily: 366 unique dedicated puzzles with zero campaign overlap.
- All 69 debug and 69 release runtime tests passed, including native chapter claim UI, preference persistence and startup tests.
- Release lint passed with 29 warnings and zero errors.
- Optimized release APK and App Bundle builds passed. These are unsigned review inputs using test ads, not Play upload artifacts; original upload-key signing remains required.
- Compiled release APK package/version/SDK checked; no AD_ID permission. APK/AAB ZIP integrity checks passed.
- Signed debug preview APK verified with APK Signature Scheme v2; package `com.arrowescape.pro.preview`, version code 60, target SDK 36, no AD_ID permission.
- Native chapter reward screen rendered and visually inspected at 720 × 1560.
- `git diff --check` passed.

## Build blocker

GitHub Actions run: https://github.com/akramangames-collab/ArrowEscapePro/actions/runs/41397904079

GitHub rejected the job before any step ran: **“The job was not started because your account is locked due to a billing issue.”** No step ran in that CI job. A local build subsequently passed all 138 Android runtime tests, release lint and signed preview APK packaging. The preview is a separate debug package using test ads, not an update signed with the production upload key. Do not use earlier V55/V58 verification as V60 evidence.

## Still required for the full redesign

- Test gameplay on a physical Android device and validate the original-key production build.
- Electric, ice, darkness, laser and separate heartbeat hazard mechanics remain unimplemented; water/fire/sand are present.
- Notification delivery/quiet-hour QA, full navigation/device checks, analytics/privacy work and final company branding remain.
- Website deployment, public app-ads.txt verification, actual store screenshots/trailer, AdMob/Play account review and testing-track validation remain.

See `PREMIUM-V2-STATUS.md` for the earlier detailed scope ledger. This is development work, not a finished redesign or a production release.
