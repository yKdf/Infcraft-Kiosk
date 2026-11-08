"""Run on the attached kiosk; temporarily uses 5/10-second idle delays."""
import json
import subprocess
import time
from pathlib import Path
from device_smoke import request

def state():
    status,raw=request('/api/state')
    assert status==200
    return json.loads(raw)
def command(action):
    assert request('/api/screen',{'action':action})[0]==200
    time.sleep(.4)
def expect(mode):
    actual=state()['screenState']
    assert actual==mode,(mode,actual)
def capture(name):
    Path('build/'+name+'.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p']))
original=state()
try:
    for fields in [dict(idleDimSeconds=-1),dict(idleDimSeconds=1),dict(idleDimSeconds=5.5),dict(idleBlackSeconds=86401),dict(idleBrightness=0),dict(idleBrightness=101),dict(idleDimSeconds=10,idleBlackSeconds=5)]:
        assert request('/api/config',dict(original,**fields))[0]==400,fields
    assert request('/api/screen',{'action':'invalid'})[0]==400
    assert request('/api/screen',{'action':'black'},False)[0]==401
    assert request('/api/config',dict(original,idleDimSeconds=5,idleBlackSeconds=10,idleBrightness=7))[0]==200
    command('wake');expect('active')
    time.sleep(5.1);expect('dimmed')
    time.sleep(5.1);expect('black');capture('idle-black')
    subprocess.run(['adb','shell','input','tap','500','300'],check=True)
    time.sleep(.3);expect('active');assert state()['idleSeconds']<3
    capture('idle-awake')
    command('black');expect('black')
    command('wake');expect('active')
    assert request('/api/config',dict(original,idleDimSeconds=0,idleBlackSeconds=0))[0]==200
    command('wake');time.sleep(5.2);expect('active')
    print('PASS: validation, automatic dim/black, touch wake, remote black/wake, disabled timers')
finally:
    assert request('/api/config',original)[0]==200
    command('wake')
