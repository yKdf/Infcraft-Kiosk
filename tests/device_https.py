"""HTTPS, certificate and cookie-session integration against installed tablet."""
import json,ssl,http.client,urllib.request,urllib.error,http.cookiejar
from pathlib import Path
context=ssl.create_default_context(cafile='certificates/infcraft-kiosk-ca.crt')
token=Path('.private/admin-token').read_text().strip()
base='https://127.0.0.1:18080'
jar=http.cookiejar.CookieJar()
def browser(jar):return urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),urllib.request.HTTPCookieProcessor(jar))
client=browser(jar)
def request(path,body=None,bearer=False,opener=None,origin=None):
    headers={'Content-Type':'application/json'}
    if bearer:headers['Authorization']='Bearer '+token
    if origin:headers['Origin']=origin
    req=urllib.request.Request(base+path,headers=headers,data=None if body is None else json.dumps(body).encode())
    try:
        with (opener or client).open(req,timeout=10) as r:return r.status,r.headers,r.read()
    except urllib.error.HTTPError as e:return e.code,e.headers,e.read()
assert request('/api/state')[0]==401
assert request('/')[0]==200
code,headers,raw=request('/api/login',{'remember':True},True)
assert code==200,(code,raw)
cookie=headers['Set-Cookie']
for flag in ['HttpOnly','Secure','SameSite=Strict','Max-Age=2592000']:assert flag in cookie
assert token not in cookie
assert request('/api/state')[0]==200
# A new browser client with the same cookie jar restores authentication without the key.
assert request('/api/state',opener=browser(jar))[0]==200
assert request('/api/reload',{},origin='https://evil.example')[0]==403
assert request('/api/tls',{},False)[0]==403
before=json.loads(request('/api/state')[2])
assert request('/api/config',before,origin=base)[0]==200
stolen=http.cookiejar.CookieJar()
for c in jar:stolen.set_cookie(c)
assert request('/api/logout',{},origin=base)[0]==200
assert request('/api/state')[0]==401
assert request('/api/state',opener=browser(stolen))[0]==401
code,headers,_=request('/api/login',{'remember':False},True)
assert code==200 and 'Max-Age' not in headers['Set-Cookie']
assert request('/api/logout',{})[0]==200
# Actual LAN certificate hostname verification, not only USB loopback.
with urllib.request.urlopen('https://10.40.2.137:8080/',context=context,timeout=10) as r:assert r.status==200
try:
    urllib.request.urlopen('http://10.40.2.137:8080/',timeout=5)
except Exception:pass
else:raise AssertionError('Plain HTTP must not serve administration')
print('PASS: trusted TLS hostname, LAN HTTPS :8080, HTTP rejected, persistent/session cookie, restore, CSRF rejection, logout revocation, configuration API')
