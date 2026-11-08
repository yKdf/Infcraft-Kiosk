#!/usr/bin/env python3
"""Create a private local CA and tablet certificate. No trust store changes."""
import os,secrets,subprocess,sys,ipaddress
from pathlib import Path
os.umask(0o077)
root=Path(__file__).resolve().parent.parent
os.chdir(root)
ip=str(ipaddress.ip_address(sys.argv[1] if len(sys.argv)>1 else '10.40.2.137'))
private=Path('.private/tls');private.mkdir(parents=True,exist_ok=True)
Path('certificates').mkdir(exist_ok=True)
def run(*args):subprocess.run(['openssl',*args],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
ca=private/'ca.key';cert=Path('certificates/infcraft-kiosk-ca.crt')
if not ca.exists():
 run('req','-x509','-newkey','rsa:3072','-nodes','-keyout',str(ca),'-out',str(cert),'-days','3650','-subj','/CN=Infcraft Kiosk Local CA','-addext','basicConstraints=critical,CA:TRUE,pathlen:0','-addext','keyUsage=critical,keyCertSign,cRLSign')
 cert.chmod(0o644)
(private/'password').write_text(secrets.token_urlsafe(32))
run('req','-new','-newkey','rsa:2048','-nodes','-keyout',str(private/'server.key'),'-out',str(private/'server.csr'),'-subj','/CN=Infcraft Kiosk')
(private/'extensions.cnf').write_text('basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectAltName=IP:'+ip+',IP:127.0.0.1,DNS:localhost\n')
run('x509','-req','-in',str(private/'server.csr'),'-CA',str(cert),'-CAkey',str(ca),'-CAserial',str(private/'serial'),'-CAcreateserial','-out',str(private/'server.crt'),'-days','365','-sha256','-extfile',str(private/'extensions.cnf'))
run('pkcs12','-export','-inkey',str(private/'server.key'),'-in',str(private/'server.crt'),'-certfile',str(cert),'-out',str(private/'server.p12'),'-passout','file:'+str(private/'password'),'-keypbe','PBE-SHA1-3DES','-certpbe','PBE-SHA1-3DES','-macalg','sha1')
print('Certificado criado para',ip,'e localhost. Certificado público:',cert)
