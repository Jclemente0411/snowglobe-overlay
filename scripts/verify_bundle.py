"""Validate the compiled bundle manifest, not merely source strings."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

meta = json.loads(Path(sys.argv[1]).read_text())
manifest = ET.parse(sys.argv[2]).getroot()
a = '{http://schemas.android.com/apk/res/android}'
assert manifest.attrib['package'] == meta['applicationId'], 'Wrong package'
assert int(manifest.attrib[a+'versionCode']) == meta['versionCode'], 'Wrong version code'
assert manifest.attrib[a+'versionName'] == meta['versionName'], 'Wrong version name'
assert int(manifest.find('uses-sdk').attrib[a+'targetSdkVersion']) == meta['targetSdk'], 'Wrong target SDK'
app = manifest.find('application')
assert app.attrib.get(a+'debuggable', 'false') != 'true', 'Debug bundle'
assert app.attrib.get(a+'icon'), 'Missing launcher icon'
services = app.findall('service')
wallpapers = [s for s in services if s.attrib.get(a+'name', '').endswith('.SnowWallpaperService')]
assert len(wallpapers) == 1, 'Missing live wallpaper'
assert wallpapers[0].attrib.get(a+'permission') == 'android.permission.BIND_WALLPAPER'
assert any(m.attrib.get(a+'name') == 'android.service.wallpaper' for m in wallpapers[0].findall('meta-data'))
assert any(s.attrib.get(a+'name','').endswith('.EffectsService') for s in services)
if meta['versionCode'] == 15:
    assert any(s.attrib.get(a+'name','').endswith('.SnowGlobeTileService') for s in services), 'Missing Quick Settings tile'
    permissions={p.attrib.get(a+'name') for p in manifest.findall('uses-permission')}
    assert 'com.android.vending.BILLING' not in permissions, 'Billing must remain absent in this beta'
    assert 'android.permission.INTERNET' not in permissions, 'No network required for this beta'
    assert 'android.permission.PACKAGE_USAGE_STATS' not in permissions, 'No app monitoring'
    assert not any(s.attrib.get(a+'permission')=='android.permission.BIND_ACCESSIBILITY_SERVICE' for s in services), 'No accessibility bypass'
print('VERIFIED:', meta)
