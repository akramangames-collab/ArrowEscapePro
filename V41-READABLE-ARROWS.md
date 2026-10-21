# Arrow Escape v41 — readable arrows (source revision 1.6.3)

All 400 campaign layouts now use mostly simple paths. At least 94% of arrows
in every board have 0–3 bends, and no arrow has more than six. A few arrows
retain 4–6 bends for variety. The average is about two bends per arrow.

## Resulting layouts

- Level 1: 51 arrows, with 49 at 0–3 bends and two at 4–5 bends.
- Level 1 lengths: 48 short arrows (at most 14 grid steps), three medium arrows,
  and no arrows of 24 steps or more; longest path is 17 steps.
- Level 1 has one safe opening, dependency depth 32 and 52.2% empty usable area.
- Across the campaign, 46.0–61.6% of usable grid nodes are deliberately empty.
  The existing 9×9 character shelter is excluded from these percentages.
- Across the campaign, longest paths are at most 28 grid steps. Longer arrows
  remain at most 12% of a board, and at least half its arrows remain short.
- Paths still cross every adjacent row boundary; there are no full-width
  horizontal separators. The layout is not divided into horizontal bands.

## Generation and difficulty

The generator replaces zigzag interiors with straight or elbow shortcuts,
checking collision safety and the dependency graph after every accepted edit.
It shortens an occasional stubborn tail or replaces it with independently
directed shorter pieces separated by a gap. Deterministic alternative edit
orders resolve difficult layouts; boards that fail a gate are never written.

Every level remains solvable with one to four safe opening arrows and at most
six simultaneous safe choices in any solve state. Dependency depth is at least
55% of its arrow count. Extreme milestone boards retain greater depth than
their preceding normal boards. The 6/12/30 difficulty cadence is unchanged.

The expressive character, existing movement, wallet, coins, stars, unlocks,
advertising configuration and music are preserved. In-progress geometry is
reset by the new `v41-readable-400-1` pack version. Daily/weekly packs are unchanged.

## Completed verification

- Independent solver and moving-body tail simulation pass all 400 levels.
- No overlaps, permanent self-blocks, shelter collisions or horizontal gaps.
- Exact maximum solve-frontier bound, bend limits and length mix verified from
  packed assets independently of generator metrics.
- Deterministic generation/asset comparison and Python compilation pass.
- Approved movement method hashes and UI/UX contract pass.
- Playable preview JavaScript syntax and interaction checks pass: blocked tap,
  hint, restart, navigation and complete solves of levels 1–3.
- The PNG was visually checked. It renders the exact Level 1 paths with the
  existing character artwork; it is a layout preview, not a device screenshot.

## Build limitations

No new APK or AAB was compiled or signed. The previous Android build tools,
dependency cache, original signing files and shared Git metadata are missing.
Updated Android runtime test expectations are included but were not executed.
The source snapshot contains the complete revised source/assets for rebuilding.

Nothing was pushed or published. Real-phone music/ad checks and Play Games
console completion remain pending. Existing v39 APK/AAB files still contain
their previous levels and do not include these v41 changes.
