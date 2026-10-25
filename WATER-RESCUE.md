# Arrow Escape water rescue — v51 / 1.7.0

Levels 1–5 use 3–7 simple arrows and 120 seconds. Levels 6–1000 use increasingly
long physical blocker chains, with exactly one unassisted removal order. Every
board surrounds a reserved central cage on four sides; the gold key is last.

Water rises during active play. Emotions change at 35% and 70%; a full cage
fails the level. Menus, ads, app backgrounding, tutorial and intro overlays pause
the timer. Reduced-motion mode does not slow it. Saved water resumes with the
board, restart resets it, and revive drains it to at most 50%. The clock stops
when the player accepts the final key escape, before its celebration animation.

The gold arrow is immune to Eraser, Bomb, Sweep and Triple Escape, even when it
is the last remaining arrow. Only a manual legal tap removes it. Hint and Safe
Scan may highlight it. A blocked booster does not consume stock.

The result screen has an open cage, happy character, stars, coins, remaining
time, mistakes, earned gifts, sharing and Next Rescue. Replay clears do not
award additional level coins. Flow Shield remains once per level.

The generator extracts dependency paths from the prior 400 layouts, then trims
tails and reflects variants while preserving the physical chain. These are
1000 distinct geometries in the existing visual family. Difficulty and timer
budgets still need human phone playtesting.

Checks:
- `python scripts/generate-water-levels.py --check`
- `python scripts/verify-water-levels.py`
- `gradle testDebugUnitTest assembleDebug lintRelease`
- `bash scripts/test-wallet.sh`

This change does not publish to Google Play.
