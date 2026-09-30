# Arrow Escape: Puzzle Maze — Google Play production launch

Current production source of truth: `main`

- Package: `com.arrowescape.pro`
- Current Play line: `1.0.2` (`versionCode 3`)
- Target / compile SDK: Android 16 / API 36
- Minimum SDK: 24
- App type: Game
- Category: Puzzle
- Contains ads: Yes
- In-app purchases: No
- Login / special app access: None

## Production-access status

Production access has been granted for the app. Do not repeat the old closed-testing setup instructions in this repository when preparing the public launch.

## Safest first-production path

Prefer promoting the exact tested `1.0.2` / code `3` artifact from the closed-testing track to Production if that is the artifact currently accepted by Play Console. This keeps the first public rollout on the same binary that testers already exercised.

Do not rebuild a different binary and try to upload it with an already-used `versionCode`. If the current source has changes that were not part of the Play Console code-3 artifact, bump to at least `versionCode 4` (normally `1.0.3`) before creating a new upload.

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

Use the current approved Play Console icon, feature graphic and screenshots unless you are intentionally replacing them with newly reviewed assets. Older V16 asset filenames mentioned by previous documentation are no longer the source of truth.

## Ads and privacy

Release builds use the production AdMob app/ad-unit IDs; debug builds use Google's test IDs. The app requests updated consent information at launch, only starts production ad requests when Google's consent state allows it, and exposes `Settings > Privacy choices` when required by UMP.

Play Console Data safety must reflect the Google Mobile Ads SDK. Do not declare that the app collects no data. Review the current Google Mobile Ads disclosure for categories such as approximate location, app interactions, diagnostics and device/other identifiers, along with the purposes and sharing fields shown by Play Console.

The game itself stores progress, virtual coins and settings locally and has no player account/backend sync.

## Production rollout checklist

1. Confirm Play Console shows Production access enabled.
2. Confirm App content, Data safety, Ads, Content rating, Target audience, Store listing and Privacy policy sections have no blocking warnings.
3. Prefer promoting the tested closed-track `1.0.2` / code `3` release for the first launch.
4. Review the release summary and device availability before rollout.
5. Start with a staged rollout rather than immediately exposing 100% of eligible users.
6. Watch Android vitals, crash/ANR rate, Play pre-launch findings, user reviews and AdMob policy/status after launch.
7. Increase rollout only when the production build remains healthy.

## Versioning rule after first production

Every new Play upload must use a strictly higher `versionCode` than every artifact previously uploaded to Play Console. For the next new binary after code 3, use code 4 or higher. Never reset the Play version code again.

## Current policy note

As of 31 August 2026, new apps and app updates submitted to Google Play for standard Android mobile devices must target Android 16 / API 36 or higher. This project already targets API 36.
