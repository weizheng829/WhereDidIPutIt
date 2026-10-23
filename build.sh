#!/usr/bin/env bash
# 无 Gradle 构建：aapt2 + javac + d8 + zipalign + apksigner
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BT="$ROOT/toolchain/bt/android-14"
AJAR="$ROOT/toolchain/pf/android-34/android.jar"
OUT="$ROOT/build"
APKDIR="$ROOT/apk"
APKNAME="收哪了-v1.3.5.apk"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$APKDIR"

echo "[1/6] 编译资源 (aapt2 compile)"
"$BT/aapt2.exe" compile --dir "$ROOT/app/res" -o "$OUT/res.zip"

echo "[2/6] 链接资源 (aapt2 link)"
"$BT/aapt2.exe" link -o "$OUT/app.base.apk" -I "$AJAR" \
  --manifest "$ROOT/app/AndroidManifest.xml" \
  -A "$ROOT/app/assets" \
  --java "$OUT/gen" --auto-add-overlay \
  "$OUT/res.zip"

echo "[3/6] 编译 Java (javac)"
find "$ROOT/app/src" "$OUT/gen" -name "*.java" | cygpath -m -f - > "$OUT/sources.txt"
javac -encoding UTF-8 --release 8 -nowarn \
  -cp "$AJAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/6] 生成 dex (d8)"
CLASSES=$(find "$OUT/classes" -name "*.class")
"$BT/d8.bat" --release --lib "$AJAR" --min-api 21 --output "$OUT/dex" $CLASSES

echo "[5/6] 打包 dex 进 APK 并对齐"
(cd "$OUT/dex" && jar -uf "$OUT/app.base.apk" classes.dex)
"$BT/zipalign.exe" -f 4 "$OUT/app.base.apk" "$OUT/app.aligned.apk"

echo "[6/6] 签名 (apksigner)"
KS="$ROOT/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias app \
    -dname "CN=Nawle" -keyalg RSA -keysize 2048 -validity 10000
fi
"$BT/apksigner.bat" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$APKDIR/$APKNAME" "$OUT/app.aligned.apk"
"$BT/apksigner.bat" verify "$APKDIR/$APKNAME"
echo "构建完成: $APKDIR/$APKNAME"
