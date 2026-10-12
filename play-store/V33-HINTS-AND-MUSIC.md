# v33 / 1.3.2: audio and direct booster refill

The original offline 32-second music asset is present and decodes successfully. Its measured average amplitude is -24.9 dBFS, peak -11.9 dBFS. Previous player gain 0.22 attenuated it by another 13.2 dB. Player gain is now 0.65, approximately 9.4 dB louder, with matching game/music audio attributes. Hardware volume keys control the media stream. Window-focus gain retries playback after a denied initial audio-focus request. Delayed focus is accepted. Music remains optional and pauses outside the foreground and during full-screen ads. This addresses plausible causes of inaudible music; actual speaker audibility must still be checked on the user's phone.

Android 15+ requires the app to be the top app or a foreground service to acquire audio focus. Retrying when the window gains focus avoids relying solely on an early onResume request:
https://developer.android.com/media/optimize/audio-focus

Every new level attempt has one free hint in addition to saved Hint stock. The free hint is spent first. Its spent state persists when the app is closed/reopened; beginning a new attempt resets it to one. Stored Hint items still cost one per extra hint. All four gameplay tool buttons show a numeric count, including zero; Hint displays the free allowance plus stored Hint stock.

Zero-stock taps request the chosen item's rewarded ad directly, with no additional confirmation dialog. The gameplay footer explains `0 = TAP FOR AD · +1 TOOL` before the player taps; first-launch tool guidance explains the same action/reward. Available ads show immediately. Otherwise a cancellable loading dialog discloses the chosen item, with a 20-second timeout. Cancellation, backgrounding and ad-load failure cancel the pending request, so a late callback cannot unexpectedly open an ad. An earned callback stores exactly one chosen tool; dismissal alone never grants it. Stored stock is used on a subsequent tap/target confirmation, not automatically consumed by the ad.

Rewarded ads require clear pre-ad action/reward disclosure and affirmative user opt-in:
https://support.google.com/admob/answer/7313578?hl=en

The v32 harder 400-level pack, sparse checkpoint gifts, target preview/confirmation and preserved preview signing are retained. No production publish or new sign-in provider is introduced.
