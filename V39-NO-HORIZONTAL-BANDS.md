# Arrow Escape 1.6.1 / versionCode 39

All 400 campaign levels now use one continuous arrow field around the central rescue character. Long and short paths cross the old region boundaries, and their heads can change direction. There are no full-width horizontal separators.

The generator starts with a coverage seed, then splices neighbouring paths across grid squares and tests both endpoint orientations. It accepts only non-overlapping paths with full self-tail clearance, an acyclic dependency graph, one opening move and a bounded solve frontier. Failed layouts are regenerated; banded seed layouts are never released as a fallback.

The no-band requirement is enforced twice: generation requires at least four vertical path connections across EVERY adjacent grid-row boundary; an independent verifier reconstructs those edges from the final packed assets and checks every one of the 400 boards. The runtime test also checks the first rendered board. The central 9x9 shelter stays empty of puzzle arrows.

The fully rewired layouts retain exactly one opening and at most three available moves in ANY solve state. Global rewiring replaces the previous tier-specific 85–94% dependency-depth thresholds with a 75% floor; the previous maximum of two available moves becomes three. This permits paths to weave across the entire field while keeping openings and later choices constrained. The 6/12/30 checkpoints, larger milestone boards and special mechanics remain.

Level 1 has 51 arrows: 28 short, 6 long and 17 medium; lengths range from 3 to 51 cells, with up to 23 bends. Across the pack, paths reach 124 cells and 72 bends. The expressive character, original ads, inventory, free level hint, music and banner fixes remain. Approved V15 path-following movement remains unchanged. Incompatible in-level saves restart; wallet, stars and unlocked levels remain. Daily/weekly challenge packs are unchanged.

## Validation

- Independent solver: all 400 boards solvable, no permanent self-blocks, no overlapping starting paths, full coverage outside the shelter, no horizontal separators, one opening and at most three choices in every solve state.
- Runtime suite: all 30 tests passed, including all campaign levels under actual game rules and the new no-band test.
- UI contract, generated-asset checks and 64 wallet assertions passed.
- APK/AAB signatures passed; original signing certificate matched.
- Bundletool validation and package/version/production AdMob-ID checks passed.
- Release lint: zero errors, 22 warnings.
- AAB assets match the verified levels, character atlas and privacy policy.

No Play Store publication is performed. Play Games console setup and real-device login, production ads and phone music audibility remain pending checks. Source changes are local; the earlier GitHub push authorization remains outstanding.
