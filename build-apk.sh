#!/bin/sh
# Build an Android APK from a desktop Minecraft Alpha client jar.
#   ./build-apk.sh path/to/a1.1.2_01.jar [out.apk]
# Needs (Debian/Ubuntu): openjdk, android-sdk-platform-23, dalvik-exchange, aapt,
# zipalign, apksigner, libasm-java
set -e
GAME_JAR=$(readlink -f "$1")
OUT=${2:-minecraft-alpha.apk}
case "$OUT" in /*) ;; *) OUT="$PWD/$OUT";; esac
cd "$(dirname "$0")"
ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
ASM=/usr/share/java/asm.jar:/usr/share/java/asm-commons.jar:/usr/share/java/asm-tree.jar
B=build
DX=$(command -v dx || echo /usr/lib/android-sdk/build-tools/debian/dx)
mkdir -p $B

echo "[1/7] remapping game jar"
mkdir -p $B/tools
javac -nowarn -cp $ASM -d $B/tools tools/Remap.java tools/Check.java
java -cp $ASM:$B/tools Remap "$GAME_JAR" $B/game-remapped.jar $B/game-res.zip

echo "[2/7] compiling shim"
rm -rf $B/shim-classes && mkdir -p $B/shim-classes
javac -nowarn -source 8 -target 8 -bootclasspath $ANDROID_JAR -d $B/shim-classes $(find shim/src -name '*.java') 2>$B/javac.log || { cat $B/javac.log; exit 1; }

echo "[3/7] checking references"
java -cp $ASM:$B/tools Check $B/game-remapped.jar $B/shim-classes | tail -1

echo "[4/7] compiling Android code"
rm -rf $B/android-classes && mkdir -p $B/android-classes
javac -nowarn -source 8 -target 8 -bootclasspath $ANDROID_JAR -cp $B/shim-classes -d $B/android-classes $(find android/src -name '*.java') 2>$B/javac.log || { cat $B/javac.log; exit 1; }

echo "[5/7] dexing"
rm -rf $B/dex && mkdir -p $B/dex
$DX --dex --min-sdk-version=21 --output=$B/dex/classes.dex $B/shim-classes $B/android-classes $B/game-remapped.jar

echo "[6/7] packaging"
aapt package -f -M android/AndroidManifest.xml -S android/res -I $ANDROID_JAR -F $B/unsigned.apk
python3 - "$B/unsigned.apk" "$B/dex/classes.dex" "$B/game-res.zip" <<'PY'
import sys, zipfile
apk, dex, res = sys.argv[1:4]
with zipfile.ZipFile(apk, 'a') as z:
    z.write(dex, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(res) as r:
        for n in r.namelist():
            # game resources go at the APK root, where getResourceAsStream finds them
            z.writestr(n, r.read(n), compress_type=zipfile.ZIP_DEFLATED)
PY

echo "[7/7] aligning and signing"
mkdir -p "$(dirname "$OUT")"
[ -f $B/debug.keystore ] || keytool -genkeypair -keystore $B/debug.keystore -storepass android -keypass android \
    -alias mcport -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=mcport" >/dev/null 2>&1
zipalign -f 4 $B/unsigned.apk $B/aligned.apk
apksigner sign --ks $B/debug.keystore --ks-pass pass:android --key-pass pass:android --out "$OUT" $B/aligned.apk
echo "done: $OUT"
