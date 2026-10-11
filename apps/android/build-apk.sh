#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
REPO_DIR="$(cd -- "$APP_DIR/../.." && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${SDK_DIR:?Set ANDROID_HOME to an Android SDK with platform 35 and build-tools 35.0.0}"
TOOLS="$SDK_DIR/build-tools/35.0.0"
ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"
UI_DIR="${MCXD_UI_DIR:-$REPO_DIR/packages/ui/.output/public}"
BUILD="$APP_DIR/build"
test -s "$UI_DIR/index.html" || { echo 'Build the dashboard first: MCXD_DISABLE_PWA=true pnpm build:ui' >&2; exit 1; }
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/res/drawable" "$BUILD/assets/public"
rm -rf "$BUILD/assets/public"
cp -R "$UI_DIR" "$BUILD/assets/public"
cp "$REPO_DIR/packages/ui/public/pwa-192x192.png" "$BUILD/res/drawable/icon.png"
cp "$REPO_DIR/LICENSE" "$BUILD/assets/public/LICENSE.txt"
javac -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
    -d "$BUILD/classes" "$APP_DIR/src/io/github/xx025/metacubexd/mobile/MainActivity.java"
"$TOOLS/aapt2" compile --dir "$BUILD/res" -o "$BUILD/resources.zip"
"$TOOLS/aapt2" link -o "$BUILD/unsigned.apk" -I "$ANDROID_JAR" \
    --manifest "$APP_DIR/AndroidManifest.xml" -A "$BUILD/assets" "$BUILD/resources.zip"
mapfile -t CLASSES < <(find "$BUILD/classes" -name '*.class' | sort)
"$TOOLS/d8" --lib "$ANDROID_JAR" --min-api 26 --output "$BUILD/dex" "${CLASSES[@]}"
python3 - "$BUILD/unsigned.apk" "$BUILD/dex" <<'PY'
import pathlib, sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'a', zipfile.ZIP_DEFLATED) as apk:
    for dex in pathlib.Path(sys.argv[2]).glob('*.dex'):
        apk.write(dex, dex.name)
PY
"$TOOLS/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
# Keep these private and reuse them for upgrades. Never commit the signing key.
KEYSTORE="${MCXD_KEYSTORE:-$BUILD/signing/mobile.jks}"
PASSWORD_FILE="${MCXD_PASSWORD_FILE:-$BUILD/signing/mobile.password}"
if [[ ! -f "$KEYSTORE" ]]; then
    mkdir -p "$(dirname -- "$KEYSTORE")" "$(dirname -- "$PASSWORD_FILE")"
    umask 077
    python3 - "$PASSWORD_FILE" <<'PY'
import secrets, sys
with open(sys.argv[1], 'x') as stream:
    stream.write(secrets.token_hex(32))
PY
    keytool -genkeypair -keystore "$KEYSTORE" -storetype JKS -alias mobile \
        -keyalg RSA -keysize 3072 -validity 10000 \
        -storepass:file "$PASSWORD_FILE" -keypass:file "$PASSWORD_FILE" \
        -dname 'CN=MetaCubeXD Mobile Personal Build'
fi
"$TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias mobile \
    --ks-pass "file:$PASSWORD_FILE" \
    --out "$BUILD/MetaCubeXD-Mobile.apk" "$BUILD/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$BUILD/MetaCubeXD-Mobile.apk"
echo "APK: $BUILD/MetaCubeXD-Mobile.apk"
