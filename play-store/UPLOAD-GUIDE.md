# Arrow Escape: Puzzle Maze — Google Play upload

Use the signed `ArrowEscape-PuzzleMaze-V16-Play.aab`, version 16.0.1 / code 18, package `com.arrowescape.pro`. An AAB is an upload file; Android phones do not install it directly. Keep the original V16 private key backup for future uploads. The publisher must complete Play Console account and app declarations before public release.

## Store listing

- App name: `app-name.txt`
- English short/full descriptions: `short-description.txt` and `full-description.txt`
- App type: Game; category: Puzzle
- Contains ads: Yes
- In-app purchases: No (coins are earned virtual game currency, not purchased with money)
- App access: No login or special access is required
- Icon: `play-icon-512.png`
- Feature graphic: `play-feature-1024x500.png`
- Phone screenshots: the three `level-*.png` files, at 1080x1920
- Release notes: `release-notes.txt`
- Privacy: `PRIVACY.md`, also available inside Settings > Privacy policy

Enter a support email you control. Select the intended age groups, distribution countries and pricing yourself; these are publisher decisions, not inferred from the puzzle's appearance. If children are part of the intended audience, assess Families requirements and ad settings before publishing. Complete the content-rating questionnaire using the actual game content.

## Data safety evidence

The game stores progress and virtual coins in private Android preferences and has no player accounts or developer backend. Its Google Mobile Ads SDK 25.4.0 collects/shares data, so do not answer “No data collected.” Google's SDK disclosure covers:

| Play data category to review | Why it applies |
|---|---|
| Approximate location | IP-derived location |
| App interactions | Ad/app interactions |
| Diagnostics / app performance | SDK diagnostic data |
| Device or other IDs | Advertising ID and app set ID |

Google lists advertising, analytics and fraud prevention as SDK purposes and states SDK traffic is encrypted in transit. Review the exact form's collection, sharing, purpose, optionality and deletion answers against your AdMob configuration. Do not claim a developer data-deletion service; this app does not have one. Users can clear local app data and use Google's advertising/privacy controls. Android backup may preserve app data according to device settings.

## First release

Create the app in Play Console, configure Play App Signing, complete the store listing and App content tasks, then create a testing release and upload the signed AAB. Google can generate the distribution signing key and treat the existing V16 key as the upload key. If you need Play installs to update the existing sideloaded production APK, configure Play App Signing with that existing signing key instead; keep private keys out of public uploads and repositories.

Review the generated release and pre-launch report before production. New personal developer accounts created after 13 November 2023 generally need at least 12 opted-in closed testers for 14 continuous days before applying for production access. The owner's direct APK test is valuable but is not this Play closed-testing process.

Production ad IDs are included. AdMob account/app readiness, privacy-message configuration and app-ads.txt verification are handled separately in AdMob.

## Official references

- App Bundles: https://developer.android.com/guide/app-bundle
- Signing: https://developer.android.com/studio/publish/app-signing
- Store assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Privacy and data: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Mobile Ads disclosure: https://developers.google.com/admob/android/privacy/play-data-disclosure
- Personal-account testing: https://support.google.com/googleplay/android-developer/answer/14151465
