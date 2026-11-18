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
        nodes=list(tree().iter('node'))
        # Never acknowledge Nexus errors or permissions. The fresh Pixel image may show this
        # specific, unrelated launcher ANR while it boots; Wait can resurface it indefinitely.
        if report.get('launcher_dialogs',0)<2 and any(n.get('package')=='android' and n.get('text') in ("Pixel Launcher isn't responding",'Pixel Launcher isn’t responding') for n in nodes):
            close=next((n for n in nodes if n.get('package')=='android' and n.get('text')=='Close app'),None)
            if close is not None:
                l,t,r,b=map(int,re.findall(r'\d+',close.get('bounds')));shell('input','tap',str((l+r)//2),str((t+b)//2));report['launcher_dialogs']=report.get('launcher_dialogs',0)+1;continue
        for n in nodes:
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
    assert shell('getprop','ro.hardware') in ('ranchu','goldfish'), 'Disposable AVD required'
    assert 'Success' in adb('install','-r',str(apk));report['passed'].append('install');shell('am','start','-W','-a','android.settings.SETTINGS')
    shell('wm','size','720x1440');shell('wm','density','320')
    report['startup']=shell('am','start','-W','-n','ai.nexus/.MainActivity');find('command_input');report['passed'].append('launch and 360dp UI')
    click('nav_activity');click('Run system check');find('Device report +1');find('Verified');find('App registry');report['passed'].append('two-step diagnostics via visible controls')
    click('nav_home');click('command_input');click('command_text')
    shell('input','text','call%s2025550101');find('call 2025550101')
    # A tap coordinate from a moving IME-inset layout is unreliable on Oreo. Hide an open
    # keyboard, then reacquire the button. Never send BACK if the IME isn't actually showing.
    if 'mInputShown=true' in shell('dumpsys','input_method'): shell('input','keyevent','4')
    click('Run command');find('Approve action');find('Number\n2025550101')
    shell('am','force-stop','ai.nexus');shell('am','start','-W','-n','ai.nexus/.MainActivity');click('nav_activity');find('Interrupted',True);click('Prepare a call');find('not replayed');report['passed'].append('killed at approval; safe interrupted receipt on restart, no dialer replay')
    report['status']='pass'
except Exception as e:
    report['status']='fail';report['failure']=str(e)
    # Disposable emulator populated only with synthetic test input; keep actual screen evidence.
    try:
        ui=tree();report['visible_app_labels']=[(n.get('text') or n.get('content-desc'))[:110] for n in ui.iter('node') if n.get('package')=='ai.nexus' and (n.get('text') or n.get('content-desc'))][:55]
        report['foreground_packages']=sorted({n.get('package','') for n in ui.iter('node')})
        (out/'failure.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p'],timeout=10))
    except Exception: pass
    raise
finally:
    (out/'result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
