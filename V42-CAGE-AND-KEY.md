# V42: cage and key rescue

Version 1.6.4 (42) adds a locked cage around the expressive rescue character and a wooden key box inside the reserved center space.

A gold arrow with a key badge unlocks the box only after its escape animation has completed. The lid opens, the key flies to the padlock, the cage doors slide aside, and the character celebrates. The marked arrow is selected from the deepest dependency chain, with proximity to the center as a tie breaker. It uses the existing collision and escape rules.

Clearing all arrows remains the level completion requirement. Completion waits for the rescue animation; reduced motion completes it immediately. Erasing or otherwise removing the marked arrow also releases the key. Restart resets the cage. Saved removal of the key arrow restores an open cage without repeating the animation.

The 400 approved level layouts and piece IDs remain unchanged. The existing level pack version is retained so in-progress games, unlocks, stars, and wallets remain compatible. Daily and weekly challenge completion is unchanged.

Verification includes the pure rescue state model, native runtime coverage of all 400 levels, unlock ordering, completion timing, restart, saved progress, boosters, reduced motion, and native renders of the locked cage, opening box, flying key, and open cage.

Signed release files must be built with the existing private upload key. The build script checks its public certificate before signing. Credentials and private key files are excluded from source archives. This change does not publish a release to Google Play.
