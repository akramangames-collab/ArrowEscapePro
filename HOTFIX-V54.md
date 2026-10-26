# V54 startup crash hotfix

V53 must not be used as a release candidate. A startup null dereference was introduced in its final gameplay callback change.

## Cause

`MainActivity.onCreate()` constructs `ArrowGameView` before assigning it to the `game` field. The view constructor calls `startLevel()`, which synchronously invokes `MainActivity.onGameplayStarted()`. V53 dereferences `game.isResultScreen()` from that callback while `game` is still null.

The final callback change happened after the previous debug unit test compilation. The earlier 53 passing tests therefore did not validate the exact code later compiled into the signed release. The prior release-verification claim was too broad.

## Fix and scope

Ignore the constructor-time gameplay callback until the activity's game reference exists. Continue handling real gameplay callbacks after initialization. This also avoids counting a cold app launch as a played level.

No save keys, wallet balances, inventory, package ID, or signing identity are replaced. V54 is intended as an in-place update over V53.

## Regression coverage

Startup runtime tests on API 24, 30, 35 and 36 cover:

- Callback before activity initialization.
- Cold launch through the delayed home/onboarding screen.
- Cold launch leaves the local played-day marker unset; starting a level sets it.

Run both debug and release startup tests, and build the signed release from frozen runtime source. Record source fingerprints before and after validation. No physical-device or emulator test is implied by Robolectric coverage.

Validation results and artifact hashes are recorded in the V54 verification report after completion. No production publication is authorized by this hotfix.

Release unit tests are explicitly enabled because AGP 9 defaults to creating unit tests only for the tested (debug) build type. Reference: https://developer.android.com/build/releases/agp-9-0-0-release-notes
