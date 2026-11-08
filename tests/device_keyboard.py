"""Check keyboard visibility on an actual editable HTML input; restore config."""
import http.server,threading,subprocess,time,json,re
from device_smoke import request
class Page(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  body=b'<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0"><input style="position:absolute;left:20px;top:20px;width:300px;height:80px;font-size:24px" placeholder="Teste do teclado"></body></html>'
  self.send_response(200);self.send_header('Content-Type','text/html');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
 def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',18081),Page)
threading.Thread(target=server.serve_forever,daemon=True).start()
def adb(*args):return subprocess.check_output(['adb',*args],text=True)
def visible():
 s=adb('shell','dumpsys','input_method');m=re.search(r'mInputShown=(true|false)',s);assert m,s;return m[1]=='true'
def expect_keyboard(expected):
 deadline=time.monotonic()+8
 while time.monotonic()<deadline:
  if visible()==expected:return
  time.sleep(.5)
 raise AssertionError('Expected keyboard visible='+str(expected))
def tap():adb('shell','input','tap','150','60');time.sleep(2)
original=json.loads(request('/api/state')[1])
try:
 adb('reverse','tcp:18081','tcp:18081')
 cfg=dict(original,url='http://127.0.0.1:18081/',kiosk=False,idleDimSeconds=0,idleBlackSeconds=0)
 assert request('/api/config',cfg)[0]==200
 assert request('/api/screen',{'action':'wake'})[0]==200
 time.sleep(4);tap();
 if not visible():
  from pathlib import Path
  Path('/tmp/kiosk-keyboard.png').write_bytes(subprocess.check_output(['adb','exec-out','screencap','-p']))
 assert visible(),'Keyboard should open with kiosk disabled'
 assert request('/api/config',dict(cfg,kiosk=True))[0]==200
 expect_keyboard(False)
 tap();assert not visible(),'Keyboard should stay closed in kiosk'
 assert request('/api/config',cfg)[0]==200
 time.sleep(1);tap();expect_keyboard(True)
 print('PASS: keyboard opens outside kiosk, closes when enabled, stays hidden on tap, returns when disabled')
finally:
 request('/api/config',original)
 if original['screenState']=='black':request('/api/screen',{'action':'black'})
 adb('reverse','--remove','tcp:18081');server.shutdown();server.server_close()
