#!/usr/bin/env python3
"""Static guardrails for Arrow Escape's approved premium UI/UX contract.

This complements Android lint and gameplay tests. It intentionally checks only
high-signal product invariants that have regressed during redesign passes.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

main = read("app/src/main/java/com/arrowescape/pro/MainActivity.java")
game = read("app/src/main/java/com/arrowescape/pro/ArrowGameView.java")
wallet = read("app/src/main/java/com/arrowescape/pro/Wallet.java")
styles = read("app/src/main/res/values/styles.xml")
styles31 = read("app/src/main/res/values-v31/styles.xml")

failures = []

def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)

# Theme must stay dark-neon on both legacy and Android 12+ resource paths.
for name, source in (("values/styles.xml", styles), ("values-v31/styles.xml", styles31)):
    require("Theme.Material.Light" not in source, f"{name}: light Material theme regression")
    require("#050B1E" in source, f"{name}: approved navy background missing")
    require("#17C7FF" in source, f"{name}: approved cyan accent missing")
    require('android:windowLightStatusBar">false' in source, f"{name}: status icons must remain light")

# Product architecture: these are separate first-class destinations from Home.
for label in ("LEVELS", "DAILY CHALLENGE", "ACHIEVEMENTS", "STORE"):
    require(label in main, f"Home/navigation contract missing destination: {label}")
require("showPremiumStore()" in main, "Dedicated Store surface missing")
require("showAchievements()" in main, "Dedicated Achievements surface missing")
require("showDailyChallengePanel()" in main, "Dedicated Daily Challenge surface missing")

# Daily challenge reward and streak ladder are product requirements.
require("public static final int DAILY_CHALLENGE = 200" in wallet,
        "Daily Challenge wallet reward must remain exactly 200 coins")
require("{10, 15, 20, 30, 40, 50, 75}" in wallet,
        "Daily streak reward ladder changed")
require("Daily challenge · +200 coins · once per day" in game,
        "Daily Challenge win UI must state the actual 200-coin reward")
require("SUPER HARD" in main and "200 COINS" in main,
        "Daily Challenge premium presentation lost SUPER HARD / 200-coin copy")

# Prevent the known stale Daily Challenge reward copy from reappearing.
require("Daily challenge · +150" not in game, "Stale +150 Daily Challenge copy reintroduced")
require("Daily challenge · +125" not in game, "Stale +125 Daily Challenge copy reintroduced")
require("Daily challenge · +100" not in game, "Stale +100 Daily Challenge copy reintroduced")

# Premium system surfaces must use the same visual language and motion.
require("@style/PremiumAlertDialog" in styles, "Premium AlertDialog theme missing")
require("@style/PremiumWindowAnimation" in styles, "Premium dialog motion missing")
require("@drawable/dialog_background" in styles, "Premium dialog background missing")

# Basic touch-target guard for reusable native controls. 48dp is Android's floor;
# the product currently targets 50dp for primary/secondary actions.
require("new LinearLayout.LayoutParams(-1,dp(50))" in main,
        "Reusable action button no longer guarantees a >=48dp touch target")

if failures:
    print("UI/UX contract FAILED:")
    for failure in failures:
        print(f" - {failure}")
    sys.exit(1)

print("UI/UX contract OK")
print(" - dark navy/cyan theme consistent through Android 12+")
print(" - Home destinations preserved")
print(" - Daily Challenge reward copy matches wallet logic")
print(" - streak ladder preserved")
print(" - premium native dialog styling/motion preserved")
print(" - reusable action touch target remains >=48dp")
