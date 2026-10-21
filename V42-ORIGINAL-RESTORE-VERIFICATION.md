# Original v42 restored — ad compliance-only patch, v47

Exact assets, all 400 level layouts, CageRescue, RescueCharacterArt and ArrowGameView restored bit-for-bit from the user's original archived v42 source. No new cage art, modified level geometry, or six-booster v46 UI.

Only changes to existing source: app/build.gradle.kts (bump code to 47 and use Families self-certified classic Google Mobile Ads), MainActivity.java (classic AdMob, global G-rated and child-directed, retain original ad placements and UI, remove WATCH AD +1 text), AndroidManifest.xml (AD_ID removal).

Google Play rejection cannot be considered resolved until AdMob account-side sensitive-category blocking is confirmed and the uploaded Play release is reviewed. This report is source verification only, not proof of successful Android compile or physical-device behavior.
