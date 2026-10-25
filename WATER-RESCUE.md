# Arrow Escape dense water rescue — v52 / 1.8.0

Levels 1–5 retain the simple tutorial and 120-second water timer. Levels
6–1000 pack arrows into previously empty grid cells with long winding tails.
The centre 9×9 cage is kept clear, surrounded on four sides by arrows. The
gold-key arrow remains the last legal manual escape and remains immune to
Eraser, Bomb, Sweep and Triple Escape.

The deterministic generator preserves arrowheads, all original dependency rays
and the forced single legal move at each step, checking self-tail clearance.
No generated layout is allowed to deadlock. Physical arrows cover at least half
of the available cells after level 5, and often substantially more (previous
v51 layouts covered approximately a quarter).

There are 25 themed chapters of 40 levels. Chapter entry cards, named routes
and chapter trophies accompany the full 1000-level selection map. The map
next arrow now stays enabled through all 50 pages. Existing key, freeze,
switch and linked-pair features activate at their established milestones.

Water fills faster from level 6: 2.1 s per arrow through level 40; 1.9
through 200; 1.7 through 600; 1.55 thereafter, plus a small fixed buffer.
Every 40th-level chapter finale gets another 10% reduction. The actual water
percentage is driven by the timer, not by wall-clock time in menus or ads.
Revive, pause, offline play, coin rewards and boosters keep their prior rules.

Save compatibility: the in-progress v51 board intentionally restarts on v52,
because tail geometry changed. Wallet, finished-level stars and unlocks
are preserved. No Play Store publication occurs during development.

Verification: `python3 scripts/generate-water-levels.py --check`;
`python3 scripts/verify-water-levels.py`; `bash scripts/test-wallet.sh`;
Android unit tests, lint, APK and AAB builds.
