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
    assert f"name='{package}'" in info and "versionCode='2'" in info and "versionName='1.0.1'" in info
    assert "application-label:'Arrow Escape: Puzzle Maze'" in info
    manifest=subprocess.check_output([str(aapt),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
    assert f'ca-app-pub-{publisher}~' in manifest
    assert apk.stat().st_size>1000000
    print(variant, 'APK verified:',apk.stat().st_size,'bytes; correct package, label, version and ad IDs')

import zipfile
assert Path('PRIVACY.md').read_bytes() == Path('app/src/main/assets/privacy-policy.txt').read_bytes()
bundle=Path('app/build/outputs/bundle/release/app-release.aab')
with zipfile.ZipFile(bundle) as archive:
    names=set(archive.namelist())
    assert {'BundleConfig.pb','base/manifest/AndroidManifest.xml','base/assets/levels.txt','base/assets/privacy-policy.txt'} <= names
    assert archive.read('base/assets/levels.txt') == Path('app/src/main/assets/levels.txt').read_bytes()
    assert archive.read('base/assets/privacy-policy.txt') == Path('PRIVACY.md').read_bytes()
print('Play App Bundle contains the verified levels and the matching privacy policy.')
