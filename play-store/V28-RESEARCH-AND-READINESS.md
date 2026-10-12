# Arrow Escape 1.2.0 / versionCode 28

400 redesigned puzzles, preserving V15 snake animation, full-path validation,
saved coins and completed-level stars. Old in-progress geometry is reset at
pack migration; players who finished old level 200 can proceed to 201.

## Difficulty and level design

Precedence: Extreme (every 30th), Super (every 12th), Hard (every 6th), otherwise
Normal. Thus 60 is Extreme and 400 is Normal, following the requested cadence.
400 puzzles: 334 Normal, 26 Hard, 27 Super Hard, 13 Extreme Hard.

The generator measures dependency depth and available opening moves rather
than simply applying a label. Normal has 3 openings, Hard 2, Super/Extreme 1.
Minimum dependency depth as a fraction of arrow count: 45%, 60%, 75%, 90%.
Independent verification checks every piece's moving tail, overlapping geometry,
acyclic dependencies, bounded available choices, uniqueness and full solvability.
Native-runtime tests also solve all 400 with special-arrow rules enabled.
These are structural difficulty criteria; human difficulty/time still needs
playtesting. No claim is made that solver metrics establish player enjoyment.

Winding trail uses the same node rectangles for rendering and input, supports
swiping, 20-node trail sections, and a My Level shortcut. Current/completed/
locked states remain visible. Chapter rewards and stars support all 400 levels.

## Boosters

King's official guidance distinguishes tools with different effects and
player-selected in-game targets. Arrow Escape adapts this pattern to arrow logic:
- Hint: highlights a valid escape using full path/self-tail/special rules.
- Precise Eraser: targets one selected arrow, replacing random erasure.
- Area Bomb: radius 4 board cells, 60 coins, removes each whole arrow touching
  the circle. Segment intersection includes crossings and tangencies.
- Row Sweep: one row (half-cell band), 75 coins; clears intersecting arrows.

Target preview highlights affected arrows; USE confirms, CANCEL is free.
Empty target, insufficient balance and duplicate confirmation do not spend.
Booster use counts as an assist and resets the combo/shield; it does not earn
an unassisted three-star clear. No booster is required to solve the campaign.
The existing free eraser and earned-ad replenishment remain.

Do not add rotating/shuffling arrows casually: those mechanics can invalidate
solvability and special-arrow dependencies. Extra lives/revive already exist.
Next playtest should compare clear time, wrong taps, abandonment and booster
use across Normal/Hard/Super/Extreme at early and late campaign positions.

## Google Play Games only

SDK: play-services-games-v2 22.1.0. No additional login provider.
Play Console project created/linked on 12 October 2026:
- Cloud project: arrow-escape-pro-1d6e9
- PGS numeric project ID: 841960223704
- OAuth consent and Android credentials remain unconfigured.

The account session was authenticated and the project linkage verified in Play
Console. The Google Cloud OAuth page returned Site Unavailable in this browser;
no consent scopes, OAuth credentials or publishing were completed.

Use the Play app-signing SHA1 for the production Android OAuth client; the upload
certificate is a different purpose. Debug applicationId is com.arrowescape.pro.debug
and needs its own debug signing credential if sign-in is to be tested in that APK.

After consent and Android OAuth clients are configured and tested, build with
ARROW_PLAY_GAMES_READY=true (or -PplayGamesReady=true). Project ID is already set.
`verifyPlayGamesConfiguration` intentionally fails until readiness is enabled.
The unconfigured SDK never starts automatically or prevents offline gameplay.
Profile accurately says progress/coins are on-device; cloud sync is not claimed.

## Android warnings / release status

App-owned edge-to-edge uses WindowCompat decor fitting and system-bar/cutout
insets. ALWAYS cutout mode is used only on API 30+ where it exists. Buttons,
board and ads must still be checked on actual devices with gestures and buttons.

Google Play HSDP 2.2.0 is the latest version in Google's Maven metadata checked
on this date. Its HsdpShimActivity.onCreate bytecode assigns iconst_1 to
layoutInDisplayCutoutMode (SHORT_EDGES). This is a vendor reference still in the
release dependency graph. Do not remove/stub SDK classes or alter bytecode just
to silence Console analysis. The deprecated-cutout recommendation can persist.
No claim is made that the Play warning is eliminated.

Release bundle compiles with R8 and mapping generation; this alone is not a
signed/verified Play release. Production distribution requires the existing
upload key, matching certificate, live ads/sign-in testing, and final Play checks.
A debug test APK is a separate package and does not replace the installed Play app.

## Research sources

- Android edge-to-edge and insets:
  https://developer.android.com/develop/ui/views/layout/edge-to-edge
- Android cutout behavior:
  https://developer.android.com/develop/ui/views/layout/display-cutout
- King booster categories:
  https://candycrush.zendesk.com/hc/en-us/articles/360000750998-What-are-Boosters
- King booster targeting/effects:
  https://community.king.com/en/candy-crush-saga/discussion/246616/candy-crush-saga-booster-tips
- Play Games platform authentication:
  https://developer.android.com/games/pgs/android/android-signin
- Play Games console setup:
  https://developer.android.com/games/pgs/console/setup

Public references informed mechanics and integration. Cadence, booster prices,
radius and difficulty thresholds are project-specific implementation decisions.

## Verification completed in this session

- Deterministic regeneration and 400-level independent solver: passed.
- 366 dedicated Daily Challenges (no campaign overlap): passed.
- Wallet: 64 assertions; campaign/booster geometry: 19 checks passed.
- Android runtime unit tests: 5 passed, including all 400 campaign solves.
- Release lint: completed with no errors (recommendations remain).
- Debug APK, Android test APK and R8 release AAB: built successfully.
- Debug APK v2 signature: verified. SHA1: AD:D2:16:ED:A1:80:9C:DA:F9:2F:3E:4F:0A:9B:46:FB:FD:47:BE:B0.
- Console upload certificate confirmed: BF:3C:C7:8E:44:7E:32:09:B9:8E:76:59:24:97:34:61:0A:D6:FE:97.

The release build is unsigned: the existing private upload key is not present in
the build environment. Existing key metadata was located, but no signing secret
was read or printed. No key reset was requested. Real-device full-screen ad,
Play Games login and Play pre-launch verification remain outstanding.
