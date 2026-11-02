"""Check the built manifest, not just source-code strings. Run after bundletool dump."""
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
assert app.attrib.get(a+'debuggable', 'false') != 'true', 'Debug bundle must not be uploaded'
assert app.attrib.get(a+'icon'), 'Missing launcher icon'
services = app.findall('service')
wallpapers = [s for s in services if s.attrib.get(a+'name', '').endswith('.SnowWallpaperService')]
assert len(wallpapers) == 1, 'Missing live wallpaper service'
assert wallpapers[0].attrib.get(a+'permission') == 'android.permission.BIND_WALLPAPER', 'Wallpaper permission missing'
assert any(m.attrib.get(a+'name') == 'android.service.wallpaper' for m in wallpapers[0].findall('meta-data')), 'Wallpaper metadata missing'
assert any(s.attrib.get(a+'name','').endswith('.EffectsService') for s in services), 'Missing overlay service'
print('VERIFIED:', meta)
