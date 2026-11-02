#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$REPO_ROOT/app"
VERSIONS_DIR="$REPO_ROOT/versions"
WORK_ROOT="${WORK_ROOT:-$HOME/work/poems}"
ANDROID_SDK="${ANDROID_SDK:-$HOME/android/sdk}"
APKTOOL_JAR="${APKTOOL_JAR:-$HOME/work/game/tools/apktool.jar}"
SIGNER_JAR="${SIGNER_JAR:-$HOME/work/game/tools/uber-apk-signer.jar}"
BASE_APK="${BASE_APK:-$WORK_ROOT/base.apk}"
ANDROID_JAR="${ANDROID_JAR:-$ANDROID_SDK/platforms/android-34/android.jar}"
D8_BIN="${D8_BIN:-$ANDROID_SDK/build-tools/34.0.0/d8}"
KEYSTORE="$WORK_ROOT/poems-release.jks"
KEYSTORE_PASSWORD_FILE="$WORK_ROOT/keystore-pass.txt"
VERSION_CODE="${VERSION_CODE:-$(date +%s)}"
VERSION_NAME="${VERSION_NAME:-v$(date +%Y.%m.%d-%H%M)}"
BUILD_ROOT="$WORK_ROOT/build-$VERSION_CODE"
CLASSES_DIR="$BUILD_ROOT/classes"
DEX_DIR="$BUILD_ROOT/dex"
BUILD_DECODED="$BUILD_ROOT/decoded"
SIGN_OUTPUT="$BUILD_ROOT/signed"
UNSIGNED_APK="$BUILD_ROOT/Poems-$VERSION_NAME-unsigned.apk"

for required in "$BASE_APK" "$ANDROID_JAR" "$D8_BIN" "$APKTOOL_JAR" "$SIGNER_JAR"; do
    if [[ ! -e "$required" ]]; then
        printf 'Missing build input: %s\n' "$required" >&2
        exit 1
    fi
done

mkdir -p "$VERSIONS_DIR" "$WORK_ROOT"
if [[ -e "$BUILD_ROOT" ]]; then
    rm -rf "$BUILD_ROOT"
fi
mkdir -p "$CLASSES_DIR" "$DEX_DIR" "$SIGN_OUTPUT"
java -jar "$APKTOOL_JAR" d -s -f "$BASE_APK" -o "$BUILD_DECODED"

find "$APP_DIR/src" -name '*.java' -print0 |
    xargs -0 javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d "$CLASSES_DIR"
jar cf "$BUILD_ROOT/app-classes.jar" -C "$CLASSES_DIR" .
"$D8_BIN" --lib "$ANDROID_JAR" --min-api 23 --output "$DEX_DIR" "$BUILD_ROOT/app-classes.jar"
cp "$DEX_DIR/classes.dex" "$BUILD_DECODED/classes.dex"

sed -i "s/^  versionCode:.*/  versionCode: $VERSION_CODE/" "$BUILD_DECODED/apktool.yml"
sed -i "s/^  versionName:.*/  versionName: $VERSION_NAME/" "$BUILD_DECODED/apktool.yml"
java -jar "$APKTOOL_JAR" b "$BUILD_DECODED" -o "$UNSIGNED_APK"

if [[ ! -f "$KEYSTORE" ]]; then
    if [[ ! -f "$KEYSTORE_PASSWORD_FILE" ]]; then
        openssl rand -hex 32 > "$KEYSTORE_PASSWORD_FILE"
        chmod 600 "$KEYSTORE_PASSWORD_FILE"
    fi
    KEYSTORE_PASSWORD="$(<"$KEYSTORE_PASSWORD_FILE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASSWORD" \
        -keypass "$KEYSTORE_PASSWORD" -alias poems -keyalg RSA -keysize 4096 \
        -validity 10000 -dname "CN=Poems Release, OU=Android, O=Poems, C=RU"
    chmod 600 "$KEYSTORE"
fi
if [[ ! -f "$KEYSTORE_PASSWORD_FILE" ]]; then
    printf 'Missing keystore password file: %s\n' "$KEYSTORE_PASSWORD_FILE" >&2
    exit 1
fi
KEYSTORE_PASSWORD="$(<"$KEYSTORE_PASSWORD_FILE")"

java -jar "$SIGNER_JAR" --apks "$UNSIGNED_APK" --ks "$KEYSTORE" --ksAlias poems \
    --ksPass "$KEYSTORE_PASSWORD" --ksKeyPass "$KEYSTORE_PASSWORD" \
    --out "$SIGN_OUTPUT"
SIGNED_APK="$(find "$SIGN_OUTPUT" -type f -name '*signed.apk' -print -quit)"
if [[ -z "$SIGNED_APK" ]]; then
    printf 'Signer did not produce a signed APK in %s\n' "$SIGN_OUTPUT" >&2
    exit 1
fi
OUTPUT_APK="$VERSIONS_DIR/Poems-$VERSION_NAME.apk"
cp "$SIGNED_APK" "$OUTPUT_APK"

python3 - "$VERSIONS_DIR/latest.json" "$VERSION_CODE" "$VERSION_NAME" \
    "$(basename "$OUTPUT_APK")" <<'PY'
import json
import sys

path, version_code, version_name, filename = sys.argv[1:]
metadata = {
    "versionCode": int(version_code),
    "versionName": version_name,
    "file": filename,
    "changes": [
        "Офлайн-кэш стихов и обновление по запросу",
        "Поиск по названиям и строкам стихов",
        "Избранное, отметки новых стихов и сохранение позиции чтения",
        "Настройка размера шрифта и отправка стихотворения",
        "Проверка обновлений из versions/",
        "Исправлены разделители в списке стихов",
    ],
}
with open(path, "w", encoding="utf-8") as output:
    json.dump(metadata, output, ensure_ascii=False, indent=2)
    output.write("\n")
PY

printf 'Built %s (versionCode %s)\n' "$OUTPUT_APK" "$VERSION_CODE"
