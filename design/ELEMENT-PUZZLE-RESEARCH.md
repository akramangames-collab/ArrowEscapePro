# Element puzzle research and implementation candidates

Status: research and design candidates, not released mechanics. Rising water, fire/heat and sand timer environments already exist; board-wide water rendering is in review. The water-tank/fire interaction below is not yet implemented or verified.

## Design principles

- Preserve snake movement, full-path and self-tail collision rules, and a separate golden cage key manually escaping last.
- Introduce each new element in a short tutorial, then a simple untimed variant before mixing it with other elements.
- Give elements distinct shapes and text, not color alone. Do not obscure arrowheads, cage or controls. Respect reduced motion.
- Avoid confusing new rules in existing player saves: keep unlocks, stars and wallet; version any changed board checkpoint.
- Prefer meaningful dependency choices over simply adding more arrows; measure actual player outcomes.

## Candidate mechanics

1. **Fire + water tank**: fire rises towards cage; a separately marked tank key travels on an arrow. Clear that arrow to open the tank, water extinguishes fire, then continue clearing the board and manually escape the golden cage key last. The tank key must be an actual legal move in a verified solution, distinct from cage key; removal boosters cannot erase either. Save tank state and hazard time. One early tutorial, then 2-3 progressively more involved layouts.
2. **Flood + drain**: rising water is danger; clear a drain-switch arrow to lower or pause the water temporarily. Drain duration and remaining time must be obvious. Avoid endless timer farming.
3. **Ice + heater**: clear heater-switch arrow to thaw clearly marked frozen arrows; while frozen they block routes. Static until activation; optional later variant combines water and a heater.
4. **Sand + fan**: sand reduces the remaining rescue time; unlocking a fan slows the rise for a fixed interval. Keep arrowheads visible; show effect and timer.
5. **Electric gate + switch**: a marked, non-tapping obstacle blocks only specified escape routes. Clear switch-arrow to deactivate. First use a persistent off-state, with timing cycles later only after a tutorial.
6. **Bridge + river**: unlock a bridge to change the route of a rescue character after the gold key, not the rules of arrow motion. The bridge is visible before activation; never cover controls.
7. **Two-stage water valves**: separately tagged keys open two valves, both required before the hazard ends. Show progress 0/2. Cage key remains manual last.
8. **Darkness + light**: headlights or lamp-switch reveal decoration, but arrows, blockers and danger cues must remain readable. Avoid hiding needed information.
9. **Steam / pressure release**: a switch vents pressure and delays a rising danger meter once. A vent should not remove arrows or grant repeatable rewards.
10. **Laser gate**: treat as later prototype. Visible countdown and paused/open states; do not require frame-perfect reactions. Verify timed solutions under slow-device and accessibility conditions.

## Generation and verification requirements

Represent state as remaining arrows, moving-tail positions, gate/switch/tank state, hazard and time budget, cage-key state, and relevant limited boosters. Build dependency graphs that include both arrow clearance and element prerequisites. Prefer constructing from a known solution and verify with an independent solver that models exact movement and dynamic state. Preserve a reproducible seed, solution witness and difficulty metrics. Test required objectives before timer expiry and check all booster, restart, pause, offline, save/reload and failure paths. Validate UI and timing on different phone sizes. Do not claim timing solvability purely from a geometric solver.

Current campaign verifies a single forced geometric sequence for each generated level. A state-aware solver is new work. Difficulty needs playtesting; arrow count and depth alone are proxies, not proof of fun or fairness.

## Research

- ZeptoLab describes Cut the Rope's use of bubbles, air cushions and spikes to affect movement and timing: https://www.zeptolab.com/franchise/cut-the-rope
- AAAI's Sokoban work generates puzzles via simulated gameplay with solvability guarantees within its modeled rules: https://ojs.aaai.org/index.php/AIIDE/article/view/12859
- A research survey highlights puzzle solvability as a central constraint: https://www.scss.tcd.ie/mads.haahr/papers/de-kegel-2020-transactions.pdf
- Timing and animation accessibility considerations: https://usability.yale.edu/digital-accessibility/accessibility-resources/accessibility-articles/animated-content-and-timing

These sources inspire the principles, not claims that the candidate Arrow Escape mechanics already exist or are proven fun.
