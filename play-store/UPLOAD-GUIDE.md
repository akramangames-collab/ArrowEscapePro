# Arrow Escape: Puzzle Maze — Google Play production launch

Current production source of truth: `main`

- Package: `com.arrowescape.pro`
- Current Play release: `1.0.2` (`versionCode 3`)
- Current track: Early Access / testing
- Target / compile SDK: Android 16 / API 36
- Minimum SDK: 24
- App type: Game
- Category: Puzzle
- Contains ads: Yes
- In-app purchases: No
- Login / special app access: None

## Production-access status

Production access has been granted for the app. The old closed-testing requirement is complete.

`1.0.2` / `versionCode 3` is already uploaded to Google Play for Early Access. Do not upload another AAB with code 3 and do not rebuild a different binary under the same version code.

For the first public production release, the preferred path is to promote the existing tested `1.0.2` / code `3` release from Early Access/testing to Production.

## Production release path

1. Open the existing `1.0.2` / code `3` release in Play Console.
2. Use the available promote/copy-to-Production flow for that existing release.
3. Keep the exact tested bundle and signing lineage already accepted by Play.
4. Review the Production release summary, declarations and device availability.
5. Start with a staged rollout for the first public release.

No new AAB is required unless you intentionally want to ship code changes beyond the Early Access build. Any such new binary must use `versionCode 4` or higher.

## Release build

The repository retains one manual GitHub Actions workflow for future signed Play bundles. It intentionally does not run on every push.

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
3. Promote/copy the existing Early Access `1.0.2` / code `3` release to Production.
4. Review the release summary and device availability.
5. Start with a staged rollout rather than immediately exposing 100% of eligible users.
6. Watch Android vitals, crash/ANR rate, Play pre-launch findings, user reviews and AdMob policy/status.
7. Increase rollout only when the production build remains healthy.

## Versioning rule after first production

Because code 3 is already uploaded to Play, every future new binary must use a strictly higher `versionCode`. The next normal update should therefore be at least `1.0.3` / code `4`.

Never reset or reuse a Play version code.
