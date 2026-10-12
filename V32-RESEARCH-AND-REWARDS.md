# Arrow Escape 1.3.1 / v32

## Product decisions and evidence

The gameplay keeps the approved navy/cyan arrow identity. The four tools are always visible: Hint, Eraser, Bomb and Sweep. Bundled Manrope typography prevents OEM font substitution. Portrait projection uses more of the board space; hit testing and target previews use the same transform.

King's official booster guide describes free earned boosters and in-game tools visible at the bottom of the game. Its Daily Treat Machine demonstrates occasional rewards and increased milestone prizes. These are useful design references, not evidence that a particular reward cadence will improve this game's retention.

- https://candycrush.zendesk.com/hc/en-us/articles/360000750998-What-are-Boosters
- https://candycrush.zendesk.com/hc/en-us/articles/4402467400849--Daily-Treat-Machine

## Implemented booster economy

Coins do not buy Hint, Eraser, Bomb or Sweep. Stock starts at zero; tools can be earned by selected campaign clears or optional rewarded ads. Each completed rewarded ad grants exactly one chosen tool, stored for one future use. Cancelling the ad prompt, ad unavailability, or a display failure grants nothing and spends nothing. A targeting preview never consumes stock. Confirmation consumes one tool only when at least one valid arrow is targeted. Hint consumes one item when a safe arrow can actually be highlighted.

Ad disclosures explicitly name the chosen item and quantity before the ad is shown. Reward credit happens only in the earned-reward callback. Receipts prevent duplicate callbacks from crediting twice. Google's policy requires affirmative opt-in, accurate reward disclosure, delivery of earned rewards, and normal usage after declining.

- https://support.google.com/admob/answer/7313578?hl=en
- https://developers.google.com/admob/android/next-gen/rewarded

Initial campaign gift schedule (a design hypothesis for playtesting):

| Clear | Free gift |
|---|---|
| Every 5th, except multiples of 30 | One rotating Hint / Eraser / Bomb / Sweep |
| Every 30th | Two Bombs |
| Other levels | No booster gift |

Across 400 levels there are 80 gift checkpoints, including 13 two-Bomb checkpoints. Gifts are independent of ads and awarded once per level, including when assists were used. Gift claim and inventory count persist together; replay and restart cannot farm the same gift. This is on-device storage, not cloud backup or server fraud verification. Coin rewards remain useful for cosmetic arrow types and themes.

## Increased puzzle difficulty

All 400 campaign boards are regenerated. Each has eight more arrows than its prior tier target. Normal levels now have two opening arrows, Hard/Super/Extreme have one. Minimum dependency depth is 65% / 80% / 87% / 94% of arrow count. The 6th/12th/30th cadence and higher-tier precedence remain. Independent geometric solving and the actual Java runtime solver verify that every puzzle clears without boosters. Mathematical constraints cannot establish perceived human difficulty: test completion times, mistaken taps and abandonment with real players before treating this balance as final.

Completed stars and unlocked levels persist. An unfinished old-pack board restarts at the same level because its removed-arrow IDs do not describe the new puzzle.

## Sharing and growth

The result screen has an optional Challenge a friend action. Android Sharesheet receives the actual level/challenge and earned star count plus the production Play listing link. The user selects the destination. There is no automatic message, friend tracking, leaderboard backend or deep-link level unlock.

- https://developer.android.com/training/sharing/send
- https://support.google.com/admob/answer/6201350?hl=en

Existing short onboarding, direct Route entry, Daily Challenge and collectible cosmetics are retention surfaces. Prioritize first-session completion, day-1/day-7 return, rewarded-ad acceptance versus cancellations, level retry distribution, and share-to-install conversion when analytics is configured. No viral or retention uplift is guaranteed; compare measured cohorts before adding more ad exposure.

## Release limits

This APK updates the v31 preview package using its preserved preview key. Production upload signing and Play Games authentication configuration remain pending. Preview ad units use Google's test ads. Native JVM rendering and runtime tests are useful regression coverage; hardware testing and Play Console pre-launch review remain necessary.
