"""Check the compiled bundle rather than only Gradle's source configuration."""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
meta=json.loads(Path(sys.argv[1]).read_text())
manifest=ET.parse(sys.argv[2]).getroot()
a='{http://schemas.android.com/apk/res/android}'
assert manifest.attrib['package']==meta['applicationId'],'Wrong package'
assert int(manifest.attrib[a+'versionCode'])==meta['versionCode'],'Wrong version code'
assert manifest.attrib[a+'versionName']==meta['versionName'],'Wrong version name'
assert int(manifest.find('uses-sdk').attrib[a+'targetSdkVersion'])==meta['targetSdk'],'Wrong target SDK'
app=manifest.find('application')
assert app.attrib.get(a+'debuggable','false')!='true','Debug bundle'
assert app.attrib.get(a+'icon'),'Missing launcher icon'
services=app.findall('service')
wallpapers=[s for s in services if s.attrib.get(a+'name','').endswith('.SnowWallpaperService')]
assert len(wallpapers)==1,'Missing live wallpaper'
assert wallpapers[0].attrib.get(a+'permission')=='android.permission.BIND_WALLPAPER'
assert any(m.attrib.get(a+'name')=='android.service.wallpaper' for m in wallpapers[0].findall('meta-data'))
assert any(s.attrib.get(a+'name','').endswith('.EffectsService') for s in services)
if meta['versionCode']>=15:
    assert any(s.attrib.get(a+'name','').endswith('.SnowGlobeTileService') for s in services),'Missing tile'
    permissions={p.attrib.get(a+'name') for p in manifest.findall('uses-permission')}
    for forbidden in ['com.android.vending.BILLING','android.permission.INTERNET','android.permission.PACKAGE_USAGE_STATS']:
        assert forbidden not in permissions,'Unexpected permission: '+forbidden
    assert not any(s.attrib.get(a+'permission')=='android.permission.BIND_ACCESSIBILITY_SERVICE' for s in services)
print('VERIFIED:',meta)

if meta['versionCode']>=19:
    assert 'android.permission.RECEIVE_BOOT_COMPLETED' in permissions,'Boot restore permission missing'
    receivers=[r for r in app.findall('receiver') if r.attrib.get(a+'name','').endswith('.EffectsStartupReceiver')]
    assert len(receivers)==1,'Missing boot/update receiver'
    receiver=receivers[0]
    assert receiver.attrib.get(a+'exported')=='false','Resume receiver must be private'
    actions={x.attrib.get(a+'name') for flt in receiver.findall('intent-filter') for x in flt.findall('action')}
    assert 'android.intent.action.BOOT_COMPLETED' in actions,'Boot receiver action missing'
    assert 'android.intent.action.MY_PACKAGE_REPLACED' in actions,'Update receiver action missing'
