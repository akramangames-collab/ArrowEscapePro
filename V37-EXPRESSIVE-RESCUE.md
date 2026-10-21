# Arrow Escape 1.5.1 / versionCode 37

The flat geometric centre figure is replaced by an original shaded 3D character with detailed eyes, brows, mouth, hair, hoodie, hands and shoes. Four expressions come from one transparent 2x2 atlas, decoded once and drawn at runtime. The shelter expands from 7x7 to 9x9 grid nodes; the 400 dense campaign boards are regenerated around it with full solvability validation.

- Worried at the start, with “Help me escape!”
- Startled/wincing for 1.5 seconds after a real blocked tap; normal heart-loss rules remain.
- Hopeful after a valid tap and as clearing progress grows.
- Happy on the completed-level result; retry uses the startled pose.
- Glowing elliptical platform, gold progress ring, progress bar, cleared percentage and reaction message fill the centre. A subtle breathing movement and platform pulse respect pause and Reduce motion.
- Existing escape rules, special-arrow logic, coins, hints, rewarded booster behavior and banner fixes remain.
- Old in-progress boards restart when their pack version changes. Wallet, stars and unlocks remain.

The character is a visual rescue goal, not a new tappable puzzle piece. Clear all puzzle arrows to win. Daily/weekly challenge packs remain separate.

## Artwork

Asset: `app/src/main/assets/characters/rescue-boy-expressions.png`.
Generated using the built-in image-generation tool. Original generated alpha and pixel content are retained; Android trims transparent margins only through draw-time source rectangles. No image CLI/API fallback used.

Final asset prompt brief: “Precisely aligned 2x2 transparent mobile game sprite atlas. The same original front-facing boy with warm medium-brown skin, textured swept dark hair, large expressive dark brown eyes, detailed eyebrows, nose, mouth and ears, cyan hoodie, navy trousers and white sneakers. Premium lifelike 3D animated game shading; oversized expressive head; consistent full-body scale and shoes visible. Top-left worried with hands clasped, top-right startled and wincing with palms raised, bottom-left hopeful with gentle smile, bottom-right joyful with both arms raised. No text, grid, stage, scenery or gold arrow. Preserve alpha transparency.”

## Validation

Independent solver passes all 400 boards (37–182 arrows), including moving-tail safety, dense coverage, empty shelter and 6/12/30 difficulty cadence. Deterministic regeneration, wallet checks (64 assertions) and UI contract checks pass.

The full runtime suite passed 28 tests. After the final result-screen centring adjustment, both targeted render/interaction checks passed. Release lint has zero errors and 21 warnings. APK and AAB signatures passed; the release certificate matches the original v35 certificate. Bundletool validation and APK package/version/ad-ID checks passed. The AAB contains the verified levels, original transparent character atlas and matching privacy policy.

Google Play Games console credentials, real-device login, production ad delivery and phone music audibility remain external verification steps. This build is not published to Google Play.
