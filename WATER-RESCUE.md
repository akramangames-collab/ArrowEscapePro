# Arrow Escape — V52 Dense Water Chapters

Campaign: **1000 levels, 50 chapters × 20 levels**, ten story worlds (five
chapters per world). The existing gameplay, original V42 character/cage/key
animation, six active boosters, and Gold Key Last rule are preserved.

Levels 1–5 stay easy (3–7 arrows; 120 seconds). Every level from 6 onward
uses a *dense physical board*, with growing tails filling 53.8–82% of the
usable board cells (70.8% average in the offline verified generation; the
central 9×9 cage reserve is intentionally empty). These are **real playable
arrow bodies**, not decorative obstacles.

Difficulty is structural: the generator selects a proven chain of physical
escape-ray blockers, then grows each arrow tail only onto unoccupied cells
that cannot obstruct earlier arrows in the sole correct solve order.
Growth also checks self-tail collision. Level 6 begins with a harder source
chain. A full independent verifier simulates every step on all 1000 boards:
exactly one legal next arrow, all body moves physically clear, golden key last,
central cage surrounded on four sides, no duplicate boards. Pinch zoom
remains available for the packed puzzles.

Water speed: the active-play seconds-per-arrow budget drops at levels
41, 161, 401 and 701. Integer arithmetic keeps the Python level metrics
identical to Java runtime water clocks. Water only advances in active gameplay,
never under tutorials, chapter-intros, ads, menus or backgrounding. Emotion
changes, fail/revive, save/restore and final-key victory timing are preserved.

Chapter UX: route has 50 navigable chapter pages, each containing 20 levels;
new-chapter intros, themed chapter labels, 50 trophy progress cards, and
chapter completion results. Existing level stars, coins, ranks and purchased
cosmetics are preserved across updates. Saved *in-progress boards* from the
previous level pack restart safely because arrow IDs/geometries changed.

Verification:
- `python3 scripts/generate-water-levels.py --check`
- `python3 scripts/verify-water-levels.py`
- `python3 scripts/verify-levels.py`
- `gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease`

Build is not a Play Store upload; production approval, on-device playtesting
and matching the current Play upload-key fingerprint remain separate gates.
