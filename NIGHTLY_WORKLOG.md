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

Next: build validation, then visual polish pass on Home/Store/Achievements/Levels.
