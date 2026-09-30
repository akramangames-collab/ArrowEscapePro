# Arrow Escape: Puzzle Maze — Google Play production launch

Current production source of truth: `main`

- Package: `com.arrowescape.pro`
- Existing Play release: `1.0.2` (`versionCode 3`) on Early Access/testing
- New production candidate: `1.0.3` (`versionCode 4`)
- Target / compile SDK: Android 16 / API 36
- Minimum SDK: 24
- App type: Game
- Category: Puzzle
- Contains ads: Yes
- In-app purchases: No
- Login / special app access: None

## Production-access status

Production access has been granted for the app. The old closed-testing requirement is complete.

`1.0.2` / `versionCode 3` is already uploaded to Google Play. Do not upload another AAB with code 3.

The current repository contains improvements beyond the Early Access build, so the production release candidate is now `1.0.3` / `versionCode 4`.

## Production release path

1. Build the signed `1.0.3` / code `4` AAB from the manual GitHub Actions release workflow.
2. Confirm the workflow passes level validation, wallet checks, UI/UX checks, Android lint and signature verification.
3. Download `PLAY-STORE-Arrow-Escape-1.0.3-SDK36-SIGNED.aab` from the workflow artifact.
4. In Play Console, create a new Production release and upload that signed AAB.
5. Use the current `play-store/release-notes.txt` text.
6. Review App content, Data safety, Ads, Content rating, Target audience, Store listing and Privacy policy for blocking warnings.
7. Review device availability and the pre-launch report.
8. Start with a staged rollout, then expand when Android vitals remain healthy.

## Release build

The repository retains one manual GitHub Actions workflow for signed Play bundles. It intentionally does not run on every push.

The workflow:

- requires the existing private upload-keystore secrets;
- validates generated levels and solvability;
- runs wallet and UI/UX contract checks;
- runs Android lint;
- builds `bundleRelease`;
- verifies `versionCode 4`, `versionName 1.0.3`, `compileSdk 36` and `targetSdk 36`;
- verifies the AAB signature;
- verifies the pinned Play upload-certificate SHA-256;
- uploads the signed AAB as a workflow artifact.

Never commit the keystore or signing passwords to this repository.

Required GitHub Actions secrets:

- `ARROW_RELEASE_KEYSTORE_B64`
- `ARROW_RELEASE_STORE_PASSWORD`
- `ARROW_RELEASE_KEY_PASSWORD`

The workflow uses upload-key alias `arrowescape-upload`.

## Store listing

Repository copy:

- App name: `play-store/app-name.txt`
- Short description: `play-store/short-description.txt`
- Full description: `play-store/full-description.txt`
- Release notes: `play-store/release-notes.txt`
- Screenshot plan: `play-store/screenshot-production-brief.md`
- Privacy policy: `PRIVACY.md`

Use the current approved Play Console icon, feature graphic and screenshots unless intentionally replacing them with newly reviewed assets.

## Ads and privacy

Release builds use the production AdMob app/ad-unit IDs; debug builds use Google's test IDs. The app requests updated consent information at launch, only starts production ad requests when Google's consent state allows it, and exposes `Settings > Privacy choices` when required by UMP.

Play Console Data safety must reflect the Google Mobile Ads SDK. Do not declare that the app collects no data. Review the form for approximate location, app interactions, diagnostics, device/other identifiers and the associated purposes/sharing fields.

The game itself stores progress, virtual coins and settings locally and has no player account/backend sync.

## Versioning rule

`versionCode 3` is already used on Google Play. This production candidate uses `versionCode 4`; every later uploaded binary must use a strictly higher code.

Never reset or reuse a Play version code.
