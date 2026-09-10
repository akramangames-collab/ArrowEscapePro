# Arrow Escape Overnight Worklog

## Pass 1 — UI consistency + navigation + Daily Challenge correctness
- Base: verified V22 neon build.
- Found: app theme was still Android Light theme, causing native dialogs/switches to visually clash with the approved dark neon UI.
- Fixed: switched the app theme to dark Material, dark status/navigation bars, cyan accent, dark launch background.
- Found: Daily Challenge win screen still advertised the old +100/+125/+150 rewards even though wallet logic awards 200.
- Fixed: win screen now states +200 coins once per day.
- Found: Daily Challenge completion returned to Levels despite Daily Challenge now being a separate Home destination.
- Fixed: challenge exit returns to Home; replay action replays the challenge without issuing a second daily coin reward.
- Found: Levels on-screen back arrow returned directly to gameplay while hardware back returned Home.
- Fixed: both now return to Home.
- Found: Settings still showed the wallet balance despite Store being the dedicated purchase area.
- Fixed: removed Store-style coin balance clutter from Settings.
- Found: Settings Done returned behind the menu to gameplay.
- Fixed: Settings now has an explicit Back to Home action.
- Found: gameplay could briefly unpause behind the Daily Challenge reminder after Activity resume.
- Fixed: reminder is included in pause-state logic.
- Build: CircleCI android-build SUCCESS on commit 6d106d3c98dade3da96ce6fbdfaffb417baf3f66.

## Pass 2 — Daily streak correctness + retention UX audit
- Audited Home, Store, Achievements, Daily Streak and Daily Challenge state presentation after Pass 1 build passed.
- Found: after a player missed a full UTC day, the stored streak value was still shown as the current streak until the next claim. This made the Daily Streak hero, reminder card and streak achievements visually report a stale active streak even though the next claim correctly restarted at Day 1.
- Fixed: Wallet.streak() now reports an effective current streak of 0 once a full day has been missed, while preserving stored claim history until the next legitimate claim resets it to Day 1.
- Preserved: streak reward ladder remains exactly 10 / 15 / 20 / 30 / 40 / 50 / 75 coins, with 75 coins for continuing days after Day 7.
- Preserved: Daily Challenge remains one 200-coin reward per UTC day and uses the full-clearance/self-tail rule.
- Initial build result: FAILED on commits 806acf8ebd8202510d7eb78dc4d8a0375e347d74 and 971d2b276cce40ee9fb00ed88e29dfade5bdf96e.
- Failure isolated: wallet tests used synthetic epoch-day timestamps for claims but asserted streak() using the real device clock. The new expiry-aware streak() correctly returned 0 for those ancient synthetic days, so the test contract—not gameplay—was stale.
- Fixed validation: added currentStreak(now) test assertions using the same synthetic clock, plus explicit tests that a one-day grace keeps the streak active and a missed full day resets current streak to 0 / next reward to Day 1.
- Gameplay logic unchanged.
- Corrected build: CircleCI android-build SUCCESS on commit 773568d48aaca6d3f8c319a1c801d444b0aec952.

## Pass 3 — Android 12+ theme regression + native component polish
- Audited resource qualifiers after the corrected Pass 2 build.
- Critical UI finding: values-v31/styles.xml still overrode AppTheme with Theme.Material.Light.NoActionBar, white background, light status bar and blue accent. On Android 12+ this could undo the approved dark-neon theme despite the base theme fix from Pass 1.
- Fixed: Android 12+ now uses the same dark Material base, navy system bars/background, cyan accent, light system icons and matching dark splash screen.
- Improved: added a premium AlertDialog theme so confirmation dialogs, privacy dialogs and other native surfaces inherit dark navy backgrounds, cyan controls, light typography and consistent width.
- Improved: native switches/control activation now use the same cyan accent as the game UI.
- Improved: dialog background upgraded from a flat fill to a subtle navy gradient with a cleaner blue edge while preserving 24dp rounded corners.
- Gameplay movement, level generation and full-clearance/self-tail rules unchanged.
- Commit: ab197409beefc644713ae5b9cd177210f72c0c0a.
- Build: CircleCI android-build SUCCESS.

## Pass 4 — premium motion + overlay depth consistency
- Audited transition feel between Home, Store, Achievements, Daily Challenge, Settings and native confirmation/privacy surfaces.
- Found: premium surfaces appeared/disappeared with default platform dialog motion, which visually clashed with the custom neon cards and made screen changes feel abrupt.
- Improved: added a restrained 180 ms fade/2% rise enter transition and 120 ms fade/1% lift exit transition for premium dialog/full-screen surfaces.
- Improved: standardized modal backdrop dimming to 72% so confirmation and privacy surfaces read clearly above the game without washing out the navy palette.
- Improved: explicitly disabled system force-dark transformation on the premium theme path to protect cyan/purple/gold contrast on devices that apply OEM dark-mode overrides.
- Preserved: no gameplay movement, level generation, self-tail/full-clearance, reward amount, or progression rule changes.
- Commit: fb27f3c809c850319256909e1e5b68ca9dabd17a.
- Build: CircleCI android-build SUCCESS.

## Pass 5 — build delivery clarity + version consistency
- Re-verified Pass 4 CI before making another change: CircleCI android-build is SUCCESS.
- Upload-readiness finding: the active CircleCI pipeline still packaged current V23/1.0.0 builds under old V22 filenames. This creates a real risk of installing or sharing the wrong APK during the morning design/working review.
- Fixed: debug APK is now packaged as INSTALL-THIS-ArrowEscape-1.0.0-test.apk.
- Fixed: release bundle is now packaged as PLAY-STORE-ArrowEscape-1.0.0-unsigned.aab and explicitly labeled unsigned so signing is not confused with the pre-approval APK review.
- Fixed: BUILD-SIZES.txt wording now clearly distinguishes the installable test APK from the unsigned Play Store bundle.
- Preserved: no gameplay, rewards, level generation, movement, self-tail/full-clearance, or UI behavior changed in this pass.
- Commit: 8b17ec1ad5756164098377416d6074c856ed5344.
- Build: CircleCI android-build SUCCESS.

## Pass 6 — permanent UI/UX regression gates + Android lint
- Re-verified Pass 5 CI before changing validation: CircleCI android-build SUCCESS.
- Quality-system finding: CI validated gameplay, wallet logic and packaged builds, but it did not run Android lint and had no explicit guard for premium UI contract regressions.
- Added scripts/verify-ui-ux.py as a focused regression gate for the approved dark navy/cyan theme on both base and Android 12+ resources, separate Home destinations, Daily Challenge 200-coin copy, exact 10/15/20/30/40/50/75 streak ladder, premium native dialogs/motion, and reusable >=48dp action touch targets.
- Added Android :app:lintDebug to CI before packaging so resource, manifest and Android-quality regressions fail before an APK is presented for morning review.
- Preserved: no gameplay movement, level generation, rewards or full-clearance/self-tail behavior changed.
- Code commits: 4aebfdd857ef4f018b501a8aa1a905f59ed411a0 and 17696f6e4c0c17c7132c037f9d57db7353bdbb30.
- Build: CircleCI android-build FAILED after the new lint gate correctly exposed Android compatibility defects; product/gameplay verification itself remained green.

## Pass 7 — independent CI diagnosis + exact lint isolation
- Added the existing GitHub Actions build path to the V23 branch so failures have step-level diagnostics instead of treating the CircleCI red status as opaque.
- Verified before lint: generated level pack PASS; self-clearance/solvability/V15 movement PASS; wallet PASS with 64 assertions; premium UI/UX contract PASS.
- Android lint then reported 6 errors and 17 warnings, isolating the actual reason the strengthened quality gate failed.
- Errors found: legacy Activity.onBackPressed is not invoked by Android 16 predictive-back gestures; two ArrayList.sort calls require API 24 while minSdk was 23; three base-theme attributes were declared below their platform API availability.
- Added/updated trusted GitHub build workflow so future V23 runs validate level pack, gameplay rules, wallet, premium UI contract, Android lint, APK/AAB assembly and device smoke tests in one reproducible path.
- Diagnostic workflow experiment was removed after the failure source was isolated so it cannot leave a permanent unrelated red check on later commits.
- Commits: 38715666b1913a3740cd8047fa1f7049b560d5d8 (diagnostic experiment), 0b628651ed017dc591340c863210ae342ea73f9b (trusted V23 quality build), e7f1f9ad98b2360b23b96847f012fe8c829d4b4c (diagnostic cleanup).
- Gameplay logic unchanged.

## Pass 8 — Android compatibility repairs + lint gate recovery
- Fixed API-23 runtime risk from ArrayList.sort by setting the supported runtime floor to API 24 (Android 7.0+), matching APIs already used by the level validator/game code instead of shipping a potential Android 6 crash path.
- Fixed base dark-theme compatibility by removing windowLightNavigationBar and forceDarkAllowed declarations from the unqualified values resource; the Android 12+ qualified theme continues to carry modern dark-system-bar behavior.
- Android 16 back-navigation finding was handled using Google's documented temporary migration path: android:enableOnBackInvokedCallback=false keeps the existing custom back stack functional while predictive-back migration is deferred to a later dedicated refactor. A narrowly scoped lint rule documents this intentional opt-out instead of suppressing unrelated lint categories.
- Re-ran trusted GitHub quality build: level pack PASS; self-clearance/solvability/V15 movement PASS; wallet PASS; premium UI/UX contract PASS; Android lint PASS.
- APK/AAB assembly completed successfully; the separate device-smoke job then failed before installation because scripts/device-smoke.sh still referenced obsolete V20 artifact filenames.
- Fix commits: 8153b92a74e1ce6d83cdd4e76c58831c0c682a74, d5f64cf2e5761b589075bce3d32b9ddf761225f8, f43641fac4f698afa06eefb1d8948063c7249bb7, 2ace51d949f37ad3f4e206102fb5041663718ad7, e7f1f9ad98b2360b23b96847f012fe8c829d4b4c.
- Preserved: approved snake/path-following motion, full-clearance/self-tail blocking, 200-level pack, Daily Challenge 200-coin rule and 10/15/20/30/40/50/75 streak ladder were not altered.

## Pass 9 — device-smoke delivery path repair
- Exact failure isolated from GitHub Actions device-test logs: emulator booted correctly, but installation stopped at `dist/ArrowEscape-PuzzleMaze-V20-test.apk: No such file or directory`.
- Fixed scripts/device-smoke.sh to install the current V23/1.0.0 artifact names: INSTALL-THIS-ArrowEscape-1.0.0-test.apk and ArrowEscape-1.0.0-tests.apk.
- This is a validation-path fix only; gameplay and UI behavior are unchanged.
- Commit: d1ff4a1ed4edb39e445d0ccf4871805668724ac8.
- Trusted GitHub quality workflow rerun: SUCCESS. Build job passed generated level pack, self-clearance/solvability/V15 movement, wallet, premium UI/UX contract, Android lint, APK/AAB assembly, ad/package metadata; device-test also passed all instrumentation and process checks.

## Pass 10 — clean visual-QA capture isolation
- Inspected the successful device-smoke screenshot artifact from Pass 9.
- Found: the app itself remained responsive and the device-test passed, but emulator screenshots for Home/Store/Settings were visually contaminated by an unrelated `Pixel Launcher isn't responding` system ANR dialog. This makes human UI/UX review unreliable even though it is not an Arrow Escape crash.
- Fixed the emulator smoke harness to suppress infrastructure error dialogs during screenshot capture, disable emulator transition/animation noise, clear logcat before test execution, and still fail specifically on Arrow Escape fatal-process evidence.
- This improves the trustworthiness of visual review without hiding Arrow Escape crashes or changing production behavior.
- Commit: 6a8d25c157a9bf7a2f73cc3754690eea6b14f972.
- Preserved: no gameplay movement, level generation, rewards, full-clearance/self-tail rules or production UI behavior changed.

Next: validate the clean screenshot rerun, inspect Home/Store/Settings hierarchy without system overlay, then make any justified UI polish changes only if the clean captures expose them.
