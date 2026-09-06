#!/usr/bin/env python3
import os, subprocess
from pathlib import Path
for variant,publisher,package in [('debug','3940256099942544','com.arrowescape.pro.debug'),('release','2475015099415787','com.arrowescape.pro')]:
    config=Path(f'app/build/generated/source/buildConfig/{variant}/com/arrowescape/pro/BuildConfig.java').read_text()
    assert all(f'ca-app-pub-{publisher}/' in line for line in config.splitlines() if 'ADMOB_' in line)
    for key in ('ADMOB_BANNER_ID','ADMOB_INTERSTITIAL_ID','ADMOB_REWARDED_ID'):assert key in config
    assert ('2475015099415787' not in config) if variant=='debug' else ('3940256099942544' not in config)
    apk=next(Path(f'app/build/outputs/apk/{variant}').glob('*.apk'))
    aapt=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0/aapt'
    info=subprocess.check_output([str(aapt),'dump','badging',str(apk)],text=True)
    assert f"name='{package}'" in info and "versionCode='17'" in info and "versionName='16.0.0'" in info
    assert "application-label:'Arrow Escape: Puzzle Maze'" in info
    manifest=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
    assert f'ca-app-pub-{publisher}~' in manifest
    assert apk.stat().st_size>1000000
    print(variant, 'APK verified:',apk.stat().st_size,'bytes; correct package, label, version and ad IDs')
