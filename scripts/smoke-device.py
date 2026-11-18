#!/usr/bin/env python3
"""Black-box UI/install/restart test for the exact candidate APK; no instrumentation or test APK."""
import hashlib, json, os, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=35).decode(errors='replace').strip()
def shell(*args): return adb('shell', *args)
def tree():
    shell('uiautomator','dump','/sdcard/nexus-check.xml')
    return ET.fromstring(shell('cat','/sdcard/nexus-check.xml'))
def find(label, partial=False):
    end=time.monotonic()+18
    while time.monotonic()<end:
        for n in tree().iter('node'):
            values=[n.get('content-desc',''),n.get('text','')]
            if any(label in v if partial else label==v for v in values):return n
    raise AssertionError('Visible control not found: '+label)
def click(label):
    n=find(label);l,t,r,b=map(int,re.findall(r'\d+',n.get('bounds')))
    shell('input','tap',str((l+r)//2),str((t+b)//2));time.sleep(.2)

apk=Path(os.environ.get('NEXUS_APK','build/input/JARVIS-NEXUS.apk'))
api=shell('getprop','ro.build.version.sdk');out=Path('build/compat-'+api);out.mkdir(parents=True,exist_ok=True)
report={'api':api,'fingerprint':shell('getprop','ro.build.fingerprint'),'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'passed':[]}
try:
    assert 'Success' in adb('install','-r',str(apk));report['passed'].append('install');shell('input','keyevent','82')
    shell('wm','size','720x1440');shell('wm','density','320')
    report['startup']=shell('am','start','-W','-n','ai.nexus/.MainActivity');find('command_input');report['passed'].append('launch and 360dp UI')
    click('nav_activity');click('Run system check');find('Device report +1');find('Verified');find('App registry');report['passed'].append('two-step diagnostics via visible controls')
    click('nav_home');click('command_input');find('command_text');shell('input','text','call%s+12025550101');click('Run command');find('Approve action')
    shell('am','force-stop','ai.nexus');shell('am','start','-W','-n','ai.nexus/.MainActivity');click('nav_activity');find('Interrupted',True);click('Prepare a call');find('not replayed');report['passed'].append('killed at approval; safe interrupted receipt on restart, no dialer replay')
    report['status']='pass'
except Exception as e:
    report['status']='fail';report['failure']=str(e);raise
finally:
    (out/'result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
