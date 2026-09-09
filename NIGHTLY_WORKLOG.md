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
- Commit: 806acf8ebd8202510d7eb78dc4d8a0375e347d74.

Next: validate Pass 2 build, then continue premium visual polish and first-launch/accessibility checks without changing approved gameplay.
