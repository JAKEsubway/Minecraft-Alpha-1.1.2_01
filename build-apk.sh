#!/bin/sh
# Build an Android APK from one or more desktop Minecraft client jars.
#   ./build-apk.sh game.jar[,other.jar...] [out.apk] [sound folder]
# e.g. ./build-apk.sh a1.1.2_01.jar,in-20100223.jar minecraft.apk ~/.minecraft
# With more than one jar the app asks which version to play when it starts.
# Supported: Alpha 1.1.2_01 and Indev 20100223 (others may start but without
# the touch helpers). The optional sound folder is your .minecraft (or its
# resources/ or assets/): the sounds get packed into the APK and installed on
# first launch, shared by all versions.
# Needs (Debian/Ubuntu): openjdk, android-sdk-platform-23, dalvik-exchange, aapt,
# zipalign, apksigner, libasm-java
set -e
JARS=""
OLDIFS=$IFS; IFS=,
for j in $1; do JARS="$JARS $(readlink -f "$j")"; done
IFS=$OLDIFS
OUT=${2:-minecraft.apk}
case "$OUT" in /*) ;; *) OUT="$PWD/$OUT";; esac
SOUNDS=${3:-}
[ -n "$SOUNDS" ] && SOUNDS=$(readlink -f "$SOUNDS")
cd "$(dirname "$0")"
ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
ASM=/usr/share/java/asm.jar:/usr/share/java/asm-commons.jar:/usr/share/java/asm-tree.jar
B=build
DX=$(command -v dx || echo /usr/lib/android-sdk/build-tools/debian/dx)
mkdir -p $B

echo "[1/7] remapping game jars"
mkdir -p $B/tools
javac -nowarn -cp $ASM -d $B/tools tools/Remap.java tools/Check.java
rm -rf $B/games && mkdir -p $B/games
n=0
for j in $JARS; do
  mkdir -p $B/games/$n
  java -cp $ASM:$B/tools Remap "$j" $B/games/$n/remapped.jar $B/games/$n/res.zip $B/games/$n/label.txt
  n=$((n+1))
done

echo "[2/7] compiling shim"
rm -rf $B/shim-classes && mkdir -p $B/shim-classes
javac -nowarn -source 8 -target 8 -bootclasspath $ANDROID_JAR -d $B/shim-classes $(find shim/src -name '*.java') 2>$B/javac.log || { cat $B/javac.log; exit 1; }

echo "[3/7] checking references"
for g in $B/games/*/; do java -cp $ASM:$B/tools Check $g/remapped.jar $B/shim-classes | tail -1; done

echo "[4/7] compiling Android code"
rm -rf $B/android-classes && mkdir -p $B/android-classes
javac -nowarn -source 8 -target 8 -bootclasspath $ANDROID_JAR -cp $B/shim-classes -d $B/android-classes $(find android/src -name '*.java') 2>$B/javac.log || { cat $B/javac.log; exit 1; }

echo "[5/7] dexing"
rm -rf $B/dex && mkdir -p $B/dex
$DX --dex --min-sdk-version=21 --output=$B/dex/classes.dex $B/shim-classes $B/android-classes
# each version gets its own dex + resources, loaded by its own class loader
# (versions share class names, so they cannot live in one dex)
for g in $B/games/*/; do $DX --dex --min-sdk-version=21 --output=$g/classes.dex $g/remapped.jar; done

echo "[6/7] packaging"
rm -rf $B/apk-assets && mkdir -p $B/apk-assets/games
python3 - $B/games $B/apk-assets/games <<'PY'
import sys, os, re, zipfile
src, dst = sys.argv[1:3]
index = []
for n in sorted(os.listdir(src), key=int):
    g = os.path.join(src, n)
    label = open(os.path.join(g, 'label.txt'), encoding='utf-8').read().strip()
    gid = re.sub(r'[^a-z0-9._-]+', '-', label.lower()).strip('-')
    with zipfile.ZipFile(os.path.join(dst, gid + '.jar'), 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(g, 'classes.dex'), 'classes.dex')
        with zipfile.ZipFile(os.path.join(g, 'res.zip')) as r:
            for e in r.namelist():
                z.writestr(e, r.read(e))   # game resources, found through the version's class loader
    index.append(gid + '\t' + label)
    print('      version: %s (%s.jar)' % (label, gid))
open(os.path.join(dst, 'index.txt'), 'w', encoding='utf-8').write('\n'.join(index) + '\n')
PY
if [ -n "$SOUNDS" ]; then
  python3 tools/collect_sounds.py "$SOUNDS" $B/apk-assets
else
  echo "      (no sound folder given: the APK will be silent unless you copy sounds to the phone yourself)"
fi
aapt package -f -0 ogg -0 mus -0 jar -M android/AndroidManifest.xml -S android/res -A $B/apk-assets -I $ANDROID_JAR -F $B/unsigned.apk
python3 - "$B/unsigned.apk" "$B/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1:3]
with zipfile.ZipFile(apk, 'a') as z:
    z.write(dex, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
PY

echo "[7/7] aligning and signing"
mkdir -p "$(dirname "$OUT")"
[ -f $B/debug.keystore ] || keytool -genkeypair -keystore $B/debug.keystore -storepass android -keypass android \
    -alias mcport -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=mcport" >/dev/null 2>&1
zipalign -f 4 $B/unsigned.apk $B/aligned.apk
apksigner sign --ks $B/debug.keystore --ks-pass pass:android --key-pass pass:android --out "$OUT" $B/aligned.apk
echo "done: $OUT"
