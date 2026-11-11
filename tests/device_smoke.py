"""Integration checks against the installed tablet, using adb forward tcp:18080 tcp:8080."""
import json, ssl
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
base='https://127.0.0.1:18080'
context=ssl.create_default_context(cafile='certificates/infcraft-kiosk-ca.crt')
token=Path('.private/admin-token').read_text().strip()
def request(path, payload=None, auth=True):
    headers={'Content-Type':'application/json'}
    if auth: headers['Authorization']='Bearer '+token
    r=Request(base+path, data=None if payload is None else json.dumps(payload).encode(), headers=headers)
    try:
        with urlopen(r,timeout=10,context=context) as response: return response.status,response.read()
    except HTTPError as e: return e.code,e.read()
assert request('/')[0]==200
assert request('/app.js')[0]==200
assert request('/style.css')[0]==200
assert request('/infcraft-logo.svg')[0]==200
assert request('/infcraft-wallpaper.webp')[0]==200
assert request('/api/state',auth=False)[0]==401
assert request('/api/config',{},False)[0]==401
status,raw=request('/api/state');assert status==200
original=json.loads(raw)
assert original['webState'] in ('loading','ok','error','restarting')
assert isinstance(original['webError'],str)
assert isinstance(original['webRetrySeconds'],int)
for invalid in ['javascript:alert(1)','file:///sdcard/test','https://user:pass@example.com']:
    assert request('/api/config',dict(original,url=invalid))[0]==400
assert request('/api/config',dict(original,brightness=0))[0]==400
assert request('/api/config',dict(original,orientation='invalid'))[0]==400
try:
    updated=dict(original,brightness=original['brightness']-1 if original['brightness']>5 else 6)
    assert request('/api/config',updated)[0]==200
    assert json.loads(request('/api/state')[1])['brightness']==updated['brightness']
finally:
    assert request('/api/config',original)[0]==200
assert request('/api/reload',{})[0]==200
assert request('/missing')[0]==404
print('PASS: assets, authentication, URL validation, input validation, saved configuration, restore, reload, routing')
print('Device owner:',original['owner'])
