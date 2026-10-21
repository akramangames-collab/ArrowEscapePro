# Arrow Escape 1.6.0 / versionCode 38

The easy-to-recognize repeated U-shaped bands are replaced by mixed-length winding paths. Backbite tail rewiring preserves each arrow's occupied nodes and final head edge while changing its internal route. Arrows therefore remain collision-safe and solvable as their geometry becomes less predictable.

All 400 campaign boards are regenerated. Every board has exactly one safe opening and at most two simultaneously available moves in ANY reachable solve state. The independent verifier computes the dependency graph's exact maximum antichain, rather than checking only one removal order. Weak seeds are rejected using dependency-depth gates: 85% for Normal/Hard, 90% for Super Hard and 94% for Extreme Hard. The 6/12/30 cadence remains.

Every board includes short paths (14 cells or fewer), long paths (32 cells or more) and an arrow with at least 20 bends. Across the pack, paths reach 61 cells and 45 bends. There are 39–151 arrows per board. Level 1 contains 51 arrows: 29 short, 8 long and 14 medium; lengths range from 6 to 50 cells, with up to 21 bends. Its dependency depth is 48 and its opening count is one.

The expressive rescue character, shelter, progress feedback, music, original production AdMob IDs, booster inventory and rewarded-ad behavior remain. Approved V15 path-following movement and full-path/self-tail clearance rules remain. Daily/weekly challenge packs are unchanged. Existing wallet, stars and unlocks remain; an incompatible saved in-level board restarts after the pack upgrade.

## Validation

- Independent solver: all 400 levels solvable; orthogonal, non-overlapping paths; full coverage outside the shelter; moving-tail safety; one opening; at most two choices in every solve state; recorded length/bend metrics match the actual assets.
- Deterministic asset regeneration and UI contract checks pass.
- Runtime suite: all 29 tests pass, including the new Level 1 length/bend/opening check and all 400 puzzles under actual runtime rules.
- Wallet suite: 64 assertions pass.
- APK/AAB signature checks pass; original v35 signing certificate matches.
- Bundletool validation and package/version/production AdMob-ID checks pass.
- Release lint: zero errors, 22 warnings.
- AAB assets match the independently verified levels, character atlas and privacy policy.

The source is committed locally. GitHub upload still requires the separate authorization requested earlier. No Play Store publication is performed. Play Games console credentials and real-device sign-in, production ad delivery and music audibility remain pending external checks.
