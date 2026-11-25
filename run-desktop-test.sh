#!/bin/sh
# Run the game on your PC through the same shim, with scripted input and screenshots.
#   ./run-desktop-test.sh path/to/a1.1.2_01.jar ["script"]
# Needs: liblwjgl-java, xvfb, libgl1-mesa-dri (and the build-apk.sh dependencies)
set -e
GAME_JAR=$(readlink -f "$1")
SCRIPT=${2:-"wait 60; shot shots/title.png; click 427 236; wait 10; click 427 100; wait 200; shot shots/world.png"}
cd "$(dirname "$0")"
ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
ASM=/usr/share/java/asm.jar:/usr/share/java/asm-commons.jar:/usr/share/java/asm-tree.jar
B=build
mkdir -p $B/tools shots
javac -nowarn -cp $ASM -d $B/tools tools/Remap.java tools/Check.java
java -cp $ASM:$B/tools Remap "$GAME_JAR" $B/game-remapped.jar $B/game-res.zip
rm -rf $B/shim-classes $B/desk-classes && mkdir -p $B/shim-classes $B/desk-classes
javac -nowarn -source 8 -target 8 -bootclasspath $ANDROID_JAR -d $B/shim-classes $(find shim/src -name '*.java') 2>$B/javac.log || { cat $B/javac.log; exit 1; }
javac -nowarn -d $B/desk-classes -cp $B/shim-classes:/usr/share/java/lwjgl.jar $(find desktop/src -name '*.java')

if [ -z "$DISPLAY" ]; then
  Xvfb :99 -screen 0 1280x720x24 +extension RANDR +extension GLX >/dev/null 2>&1 &
  XVFB=$!
  export DISPLAY=:99
  sleep 2
fi
export LIBGL_ALWAYS_SOFTWARE=1
mkdir -p $B/mchome
java -Xmx1g -Djava.library.path=/usr/lib/jni $MCDESK_JAVA_OPTS \
  -cp $B/desk-classes:$B/shim-classes:$B/game-remapped.jar:$B/game-res.zip:/usr/share/java/lwjgl.jar \
  mcdesk.TestMain "$PWD/$B/mchome" "$SCRIPT" || true
[ -n "$XVFB" ] && kill $XVFB 2>/dev/null || true
