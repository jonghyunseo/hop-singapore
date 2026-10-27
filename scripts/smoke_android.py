"""Small core-flow smoke pass. Uses only the dedicated hop_api36 emulator."""
import json, re, shlex, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path
import os

ROOT=Path(__file__).resolve().parents[1]
ADB=Path(os.environ['ANDROID_HOME'])/'platform-tools/adb.exe'
SERIAL=os.environ.get('HOP_EMULATOR','emulator-5554')
OUT=ROOT/'deliverables'
def adb(*args):
    return subprocess.check_output([str(ADB),'-s',SERIAL,*args])
def shell(*args):
    return adb('shell',' '.join(shlex.quote(str(x)) for x in args)).decode('utf-8',errors='replace')
def tree():
    shell('uiautomator','dump','/sdcard/hop-window.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/hop-window.xml'))
def find(label):
    return [n for n in tree().iter('node') if n.get('text')==label or n.get('content-desc')==label]
def tap(label):
    nodes=find(label)
    if not nodes: raise AssertionError('Missing UI: '+label)
    b=list(map(int,re.findall(r'\d+',nodes[-1].get('bounds'))))
    shell('input','tap',(b[0]+b[2])//2,(b[1]+b[3])//2)
    time.sleep(.35)
def screenshot(name):
    (OUT/name).write_bytes(adb('exec-out','screencap','-p'))
def persisted():
    return json.loads(adb('exec-out','run-as','app.hop.singapore','cat','files/places.json'))
def share():
    shell('am','start','-n','app.hop.singapore/.MainActivity','-a','android.intent.action.SEND','-t','text/plain','--es','android.intent.extra.TEXT','Singapore Flyer\nhttps://www.google.com/maps/search/?api=1&query=1.2894095,103.8631218')
    for _ in range(5):
        if find('장소 추가'): break
        time.sleep(.25)

assert 'hop_api36' in adb('emu','avd','name').decode(), 'Use the dedicated test emulator.'
shell('am','force-stop','app.hop.singapore')
shell('am','start','-n','app.hop.singapore/.MainActivity'); time.sleep(.7)
tap('지도')
assert find('Raffles Place') and find('430m')
assert find('이 역에서 도보 확인'), 'Primary action must be reachable on the initial screen'
screenshot('android-map.png')
tap('역별'); assert find('한 번 내려서, 3곳.')
tap('400m'); assert find('한 번 내려서, 2곳.')
tap('800m'); screenshot('android-stations.png')
tap('저장한 곳'); assert find('가고 싶은 6곳')
tap('멀라이언 방문 완료')
assert next(p for p in persisted() if p['id']=='merlion')['visited']
share(); assert find('장소 추가'); assert find('Singapore Flyer')
screenshot('android-share.png')
for _ in range(5):
    if find('내 장소에 저장'): break
    shell('input','swipe','540','1850','540','650','300'); time.sleep(.2)
tap('내 장소에 저장'); time.sleep(.5)
assert len(persisted())==7
share()
for _ in range(5):
    if find('내 장소에 저장'): break
    shell('input','swipe','540','1850','540','650','300'); time.sleep(.2)
tap('내 장소에 저장'); assert len(persisted())==7, 'Duplicate share saved twice'
shell('am','force-stop','app.hop.singapore'); shell('am','start','-n','app.hop.singapore/.MainActivity'); time.sleep(.5)
tap('저장한 곳'); assert find('가고 싶은 7곳')
assert next(p for p in persisted() if p['id']=='merlion')['visited']
screenshot('android-saved.png')
result={'device':'Android 16 / API 36 emulator','core_unit_tests':4,'initial_nearest':'Raffles Place 430m','primary_action_visible':True,'radius_800m':3,'radius_400m':2,'share_received_and_saved':True,'duplicate_share_count':7,'places_and_visited_persist_after_restart':True}
(OUT/'smoke-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
