# v35 / 1.4.1: result banner leaking into gameplay

The existing Next Level handler hid the result banner, but a delayed retry after an ad-load failure could unconditionally request a new result banner on the next gameplay screen. This left a stale advertisement rail and could hide the front tool controls.

The new guard requires result-screen state and ownership by the original attached view for loaded, failed and delayed-retry callbacks. Detached old requests are rejected even during a later result screen. Every gameplay-start path now clears result banners. Banner rows reserve the requested creative height plus the noninteractive buffer, with the AdView constrained to its requested height. A failed ad hides the empty row rather than leaving an advertisement placeholder.

A runtime regression exercises a result banner followed by level 4, rejects a stale attached view during gameplay, checks all four front-tool hit targets inside the available phone viewport, then checks that an old view cannot affect a later result. Existing simulated-large-banner phone/landscape coverage remains.

Original package, production AdMob IDs and the v27/v34 matching upload key are retained. Level pack and progress format remain unchanged. Google Play Games Console OAuth, Data Safety and live-device/Console pre-launch checks remain pending as documented in V34-PRODUCTION-CANDIDATE.md. This fixes the local banner lifecycle; it is not a claim of Play approval or live-ad device verification.
