# Premium V2 — V55 redesign checkpoint

**V53 startup crash:** superseded by the V54 hotfix. See `HOTFIX-V54.md`. Earlier V53 test results did not cover the final callback edit. The redesign remains an internal preview.

Selected name: **Arrow Escape: Rescue Puzzle** (27 characters). Package remains `com.arrowescape.pro`. Version code 55; version name `2.0.0-redesign`.

This branch is an internal development preview, not the finished redesign and not approved for production. No Play production publication or advertising spend was performed.

| Deliverable | Status | Evidence / remaining work |
|---|---|---|
| GitHub implementation | COMPLETED | Source on this branch; see commits. |
| Complete redesigned game | PENDING | Existing game extended; full visual/feature specification is not finished. |
| Updated UI/UX | TESTED | Dedicated home, hero cage, chapter collection, booster collection/guides, statistics, history, help, developer and notification pages. Native rendering tests; further device QA needed. |
| Professional game logo | COMPLETED | Original illustrated arrow/cage/key logo, matching mascot and launcher icon integrated into the native app. See design/REDESIGN-V55.md. Play listing assets remain pending. |
| Company branding | COMPLETED | Live website confirms AK Software Labs and support@aksoftwarelabs.tech. Text branding added; verified company logo asset still needed. |
| Banner system | TESTED | Existing centralized adaptive menu/result rail reused. Gameplay intentionally remains banner-free. More eligible screens, refresh/navigation reuse and physical placement audit remain. |
| Heartbeat | TESTED | Game-danger interval, optional quiet audio/haptics, low/medium/high haptic amplitude, silent-mode guard, reduced-motion setting. Actual-device perception/accessibility QA pending. |
| Rescue hazards | TESTED | Water, fire/heat and sand timer environments with stable chapter assignment. These share timer rules; electric, freezing, darkness, laser and separate heartbeat-mode mechanics are not implemented. |
| Level/chapter selection | COMPLETED | 25 chapter cards, 40 levels each, locks/progress and continue. Existing map uses 20-level pages inside each chapter. V55 adds a curved route and round level medallions; device QA pending. |
| Booster pages/demonstrations | TESTED | Six native detail pages and isolated interactive booster-specific demos. Demo inventory is separate and the golden key remains protected. |
| Daily challenges | TESTED | All 366 deterministic daily puzzles verified against generated assets, with no campaign overlap; local-calendar challenge selection. No cloud claim sync. |
| Daily booster rewards | TESTED | 200-coin wallet reward retained; completed-day entitlement gates an atomic stock+receipt choice. Choice shown after win and on Daily; repeat claims rejected across recreation. |
| Notifications | PENDING | Native opt-in settings, permission request, inexact daytime scheduling, boot/timezone rescheduling, play-day suppression and two-hour minimum gap implemented. Delivery on real devices, configurable quiet hours and effectiveness analytics pending. Standard at 18:00; frequent 10/12/14/16/18/20; fixed quiet hours 21:00–10:00. |
| Level completion rewards | TESTED | Existing payout retained (15 base, +5/+10 stars, existing milestone bonus). Persistent per-level wallet receipts prevent alternate-run duplicate payouts. Claimed amount and wallet balance displayed. V55 shows a breakdown derived from actual credit; a legal level-clear integration test verifies the wallet balance. |
| Chapter rewards | PENDING | Existing trophies and level milestone gifts retained. Dedicated chapter reward transaction/celebration not implemented. |
| All native screens | PENDING | Many new native destinations implemented; all 30 requested screen flows/back-stack behavior are not complete. |
| Official website redesign | PENDING | Responsive, accessible landing-page source in `website/arrow-escape/index.html`, clearly labeled redesign preview. Not deployed. Actual screenshots/trailer pending. |
| app-ads.txt | BLOCKED | Public `https://aksoftwarelabs.tech/app-ads.txt` returned HTTP 404. Repository contains existing publisher entry; root deployment and Play developer-site verification required. |
| ads.txt | TESTED | Public root returned HTTP 200, existing google.com pub-2475015099415787 DIRECT entry and DeskKit subdomain line. Left unchanged. Account-side seller verification pending. |
| Store assets | PENDING | Selected name, draft short description, ASO notes and release notes prepared. Matching launcher icon implemented; store feature graphic, published screenshots and experiments pending. Live listing unchanged. |
| AdMob compliance | PENDING | Existing G-rated child-directed request configuration, consent gating and debug test IDs retained. Account blocking controls, Families declarations, Data Safety and SDK/console audit not completed. |
| Analytics | PENDING | No new collection SDK or fabricated retention/revenue data. Instrumentation and privacy review remain. |
| Marketing creatives | PENDING | No gameplay promotional videos generated or campaigns launched. |
| APK | TESTED | Signed APK v2 verified; package/title/version checked. Internal development preview only; not device-tested. |
| AAB | TESTED | JAR signature verified and original certificate matched. No testing-track upload performed. |
| Signing | TESTED | Recovered original upload key verified against SHA-256 C0:F5:B8:4B:37:5B:47:75:BC:C6:7C:37:E0:57:A2:6C:B3:D6:54:FE:79:38:D4:8B:65:8E:B4:23:E4:41:42:9F. Key/credentials kept outside git. Artifact signatures checked separately. |
| Automated tests | TESTED | Full Android suite: 53 passed after navigation fixes. Plain Java wallet: 64 assertions passed. Independent verifier: 1000 layouts / 47,142 legal unique-sequence moves, golden key last, self-tail clearance. Final full suite passed 53/53; release lint passed with 25 warnings and 0 errors. Build/signature/hash verification saved separately. |
| Production release | REQUIRES APPROVAL | Finish remaining work, account audits, physical/emulator QA, testing-track validation, then obtain final approval. |

## V55 verification and artifact delivery

Source commit: `05b55845373d9cc8d6ba1b35abd86b121797b434`.

Original logo, mascot/icon, teal-and-gold lobby, rounded puzzle board, curved level map and shared native page styling are implemented. The V54 startup guard remains.

Prior-session validation completed 64 debug and 12 release startup tests; release lint reported 30 warnings and zero errors. A subsequent recovery rebuild again passed 64 debug tests, campaign verification (1,000 levels; 47,142 legal unique-sequence moves) and 64 wallet assertions. Workspace maintenance removed local artifacts before persistent delivery. These are historical test observations, not evidence that a download is currently available.

The branch now has a V55 review workflow that retains build artifacts, reports and actual native renders. Original-key signed release files are delivered only when the existing signing secrets are available and the certificate matches. Otherwise, only the separately packaged development APK is eligible for delivery. No substitute production identity is created. Current workflow outcome must be checked before claiming downloadable APK/AAB completion.

## Technical limits

- No physical Android device or emulator QA was performed. Robolectric native graphics previews are not emulator or physical-device evidence.
- Existing saves and inventory preference namespaces retained. New receipts/history are additive. Old history is not invented.
- Daily challenge uses local dates; pre-existing daily-login streak still uses its original wallet day boundary. A migration needs dedicated tests before changing old streak behavior.
- Offline clock rollback is rejected by monotonically retained daily claims; a fully trusted anti-clock-cheat system requires server time/account sync.
- Rewards/history remain local. Cloud synchronization is not implemented.
- Exact alarms are not requested; Android battery management can delay reminders.
- Booster demonstrations use isolated teaching scenarios with separate inventory.

## Ranking goal

Top-three rank is a target for specified relevant queries, country and language, not a promised outcome. No keyword volume, current rank, retention or revenue figures were invented. Draft listing changes must describe only features actually present in the published build.

Sources checked 25 October 2026:
- https://support.google.com/googleplay/android-developer/answer/9898842
- https://support.google.com/googleplay/android-developer/answer/9958766
- https://developers.google.com/admob/android/banner
- https://blog.google/products/admob/admob-banner-ad-implementation-guidance/
- https://developer.android.com/develop/background-work/services/alarms
