#!/usr/bin/env python3
"""Provision through adb forward tcp:18080 tcp:8080; restart app afterwards."""
from urllib.request import Request,urlopen
from pathlib import Path
import json,base64,ssl,sys
root=Path(__file__).resolve().parent.parent
scheme='https' if '--renew' in sys.argv else 'http'
context=ssl.create_default_context(cafile=str(root/'certificates/infcraft-kiosk-ca.crt'))
p=root/'.private/tls'
payload={'pkcs12':base64.b64encode((p/'server.p12').read_bytes()).decode(),'password':(p/'password').read_text().strip()}
req=Request(scheme+'://127.0.0.1:18080/api/tls',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+(root/'.private/admin-token').read_text().strip()})
with urlopen(req,timeout=15,context=context) as response:
 assert json.load(response)['restartRequired'] is False
print('Certificado provisionado. HTTPS será ativado automaticamente na porta 8080.')
