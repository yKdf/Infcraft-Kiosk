#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="$PWD/tools/sdk"
BT="$SDK/android-15"
PLATFORM="$SDK/android-9/android.jar"
rm -rf build/classes build/dex
mkdir -p build/classes build/dex .private
chmod 700 .private
"$BT/aapt" package -f -m -J build -M android/AndroidManifest.xml -S android/res -A android/assets -I "$PLATFORM" -F build/resources.apk
javac --release 8 -cp "$PLATFORM" -d build/classes android/src/com/biel/kiosk/*.java
"$BT/d8" --lib "$PLATFORM" --min-api 28 --output build/dex build/classes/com/biel/kiosk/*.class
cp build/resources.apk build/unsigned.apk
python - <<'PYZIP'
from zipfile import ZipFile
with ZipFile('build/unsigned.apk','a') as z: z.write('build/dex/classes.dex','classes.dex')
PYZIP
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f .private/signing.p12 ]; then
 python -c 'import secrets,pathlib; p=pathlib.Path(".private/signing-password"); p.write_text(secrets.token_urlsafe(32)); p.chmod(0o600)'
 keytool -genkeypair -keystore .private/signing.p12 -storepass:file .private/signing-password -alias kiosk -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Infcraft Kiosk' >/dev/null 2>&1
 chmod 600 .private/signing.p12
fi
"$BT/apksigner" sign --ks .private/signing.p12 --ks-pass file:.private/signing-password --out build/infcraft-kiosk.apk build/aligned.apk
"$BT/apksigner" verify build/infcraft-kiosk.apk
printf 'APK: %s/build/infcraft-kiosk.apk\n' "$PWD"
