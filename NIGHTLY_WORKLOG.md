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

Next: validate Pass 3 build, then continue first-launch/accessibility and Home/Store/Levels/Achievements visual hierarchy audit.
