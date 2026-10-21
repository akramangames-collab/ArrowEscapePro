# v30 / 1.2.2 UI and banner update

Game and menu banners now occupy measured rows in vertical layouts. Root consumes system-bar insets once. Failed and hidden banners use GONE; stale banner-size hints cannot overlap controls.

Trail: colourful gradient path, dimensional level tokens, soft ridges, drifting fireflies and a current-level pulse. Gameplay: brighter header and gradient booster cards. Native buttons: press/release scale feedback. Wins: drifting confetti. Animation only schedules while the view is visible and active.

quiet_trail.ogg is an original synthesized 32-second loop: soft sine-pad chords and pentatonic bell notes, no third-party samples. Volume 22%; persistent independent music toggle. Foreground lifecycle, audio focus and full-screen ad state govern playback. No background service or music permission.

Production blockers from V28-RESEARCH-AND-READINESS.md remain: Play Games credentials/readiness and production signing. Debug update uses the previous debug certificate and package, preserving installed test-app data.

Validation: 11 Android runtime tests passed: five campaign tests (including all 400 puzzle solutions), three full-activity startup tests on APIs 30/35/36, one portrait/landscape measured-banner regression test, and two music lifecycle/focus/toggle tests on APIs 30/35. Phone previews inspected at 360dp width. UI contract checker passed. Release lint: zero errors. Test banner geometry uses a simulated creative; live-device ad serving is still a release check.
