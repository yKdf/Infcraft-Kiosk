"""Grafana controls refresh; manual reload must preserve resting screen."""
import json,time
from device_smoke import request

def state(): return json.loads(request('/api/state')[1])
original=state()
try:
    assert 'reloadSeconds' not in original
    assert 'retryOnError' not in original
    assert 'dashboardRefreshDisabled' not in original
    url='https://dash.infcraft.net/d/ag3m3od6wl0xse/monitor?kiosk&refresh=1m'
    assert request('/api/config',dict(original,url=url))[0]==200
    assert state()['url']==url
    assert request('/api/screen',{'action':'black'})[0]==200
    time.sleep(.5)
    before=state()
    assert request('/api/reload',{})[0]==200
    time.sleep(2)
    assert state()['screenState']=='black'
    assert state()['idleSeconds']>=before['idleSeconds']
    print('PASS: Grafana refresh URL preserved; kiosk refresh settings removed; manual reload keeps screen black')
finally:
    assert request('/api/config',original)[0]==200
    assert request('/api/screen',{'action':'wake'})[0]==200
