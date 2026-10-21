# Arrow Escape v40 — spaced levels (source revision 1.6.2)

Revised all 400 campaign levels to leave intentional space and use mostly short
arrows. The expressive rescue character, central shelter, original advertising
configuration, music, progression, wallet and approved movement remain intact.

## Layout changes

- Removed the requirement to fill every usable grid node.
- 29.1–38.0% of usable grid nodes are deliberately empty; average empty space
  is about one-third. The character shelter is excluded from this calculation.
- At least half of arrows in every level are short (14 grid steps or fewer).
- Arrows of 24 steps or more comprise at most 12% of each level.
- No arrow exceeds 32 grid steps; actual longest paths are at most 30 steps,
  compared with the previous pack's maximum of 124.
- Level 1 has 51 arrows: 38 short, eight medium and five longer arrows. Its
  longest arrow is 27 steps, with a maximum of 17 bends, one safe opening,
  dependency depth 35 and 33.0% intentional empty space.
- Short fragments sometimes replace a removed tail when that tail was an
  important blocker. Fragments are separated by a deliberate gap.
- Paths still cross every adjacent row boundary; there are no full-width
  horizontal separators. Empty areas are distributed through the field.

## Difficulty and progression

Spacing reduces the previous forced-move restriction. Every revised board has
one to four safe opening arrows and at most six simultaneous choices in any
solve state (previously one opening and at most three). Dependency depth stays
at least 55% of its arrow count. Difficulty comes from interlocking directions
and bends while keeping bodies readable. All extreme milestone boards have
greater dependency depth than their preceding normal board. The 6/12/30 level
cadence is retained.

The level-pack version changes to `v40-spaced-400-1`. An in-progress board restarts
when loading the new geometry; coins, stars and unlocked levels remain. Daily
and weekly packs are unchanged.

## Checks completed

- Deterministic generation and asset comparison for all 400 levels.
- Independent solver: all 400 solvable; no permanent self-blocks or overlaps.
- Independent moving-tail simulation and exact maximum solve-frontier bound.
- Spacing, length mix, no-band and shelter checks on actual packed assets.
- Approved movement method hashes preserved; UI/UX contract check passed.
- Python compilation and playable preview JavaScript syntax passed.
- Preview interaction checks: blocked tap, hint, restart, navigation and full
  solves of levels 1–3. The PNG shows the exact packed Level 1 paths and existing
  character artwork; it is a rendered layout preview, not a device screenshot.

## Not completed

The Android toolchain, dependency cache, original signing material and shared
Git metadata used for v39 are no longer available in this workspace. No v40 APK
or AAB was compiled or signed, and updated Android runtime tests were not run.
The delivered source snapshot contains the revised assets, generator, verifier,
runtime regression expectations and version configuration for a future build.

Nothing was pushed to GitHub or published. Play Games console setup, actual
phone music/ad checks and release verification remain pending. Existing v39
APKs and AABs still contain the previous layouts and are not v40 deliverables.
