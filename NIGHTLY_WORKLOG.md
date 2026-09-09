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
- Build: CircleCI android-build pending at end of this pass.

Next: validate Pass 5 build, then continue first-launch/accessibility and Home/Store/Levels/Achievements visual hierarchy audit.
