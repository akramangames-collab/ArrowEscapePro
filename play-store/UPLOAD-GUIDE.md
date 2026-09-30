# Arrow Escape: Puzzle Maze — Google Play production launch

Current production source of truth: `main`

- Package: `com.arrowescape.pro`
- Next production upload: `1.0.2` (`versionCode 3`)
- Target / compile SDK: Android 16 / API 36
- Minimum SDK: 24
- App type: Game
- Category: Puzzle
- Contains ads: Yes
- In-app purchases: No
- Login / special app access: None

## Production-access status

Production access has been granted for the app. The old closed-testing requirement is complete.

`versionCode 3` has NOT been uploaded to Play Console. Therefore the current `1.0.2` / code `3` release is available to use as the next production upload, assuming no other bundle with code 3 has been uploaded outside this project history.

Do not bump to code 4 just for the first production release. Reserve code 4+ for the next binary after code 3 is uploaded.

## Production release path

1. Build the current `main` source as signed `1.0.2` / code `3` AAB.
2. Verify the AAB signature and pinned Play upload certificate.
3. Upload that AAB to the Production track.
4. Complete the production release review in Play Console.
5. Use a staged rollout for the first public release.

## Release build

The repository has one manual GitHub Actions workflow for the signed Play bundle. It intentionally does not run on every push.

The workflow:

- requires the existing private upload-keystore secrets;
- validates generated levels and solvability;
- runs wallet and UI/UX contract checks;
- runs Android lint;
- builds `bundleRelease`;
- verifies the AAB signature;
- verifies the pinned Play upload-certificate SHA-256;
- uploads the signed AAB as a workflow artifact.

Never commit the keystore or signing passwords to this repository.

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

## Production rollout checklist

1. Confirm Play Console shows Production access enabled.
2. Confirm App content, Data safety, Ads, Content rating, Target audience, Store listing and Privacy policy sections have no blocking warnings.
3. Upload the signed `1.0.2` / code `3` AAB as the new Production release.
4. Review the release summary and device availability.
5. Start with a staged rollout rather than immediately exposing 100% of eligible users.
6. Watch Android vitals, crash/ANR rate, Play pre-launch findings, user reviews and AdMob policy/status.
7. Increase rollout only when the production build remains healthy.

## Versioning rule after first production

Once code 3 is uploaded, every later Play upload must use a strictly higher `versionCode`. The normal next binary should therefore be at least `1.0.3` / code `4`.

Never reset or reuse a Play version code.

## Current policy note

As of 31 August 2026, new apps and app updates submitted to Google Play for standard Android mobile devices must target Android 16 / API 36 or higher. This project already targets API 36.
