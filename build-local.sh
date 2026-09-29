#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -z "${MORPHE_DESKTOP_JAR:-}" || -z "${R8_JAR:-}" || -z "${ANDROID_JAR:-}" ]]; then
    "$repo_dir/fetch-build-tools.sh"
    MORPHE_DESKTOP_JAR="${MORPHE_DESKTOP_JAR:-$repo_dir/.local-tools/morphe-desktop.jar}"
    R8_JAR="${R8_JAR:-$repo_dir/.local-tools/r8.jar}"
    ANDROID_JAR="${ANDROID_JAR:-$repo_dir/.local-tools/android.jar}"
fi
build_dir="$repo_dir/.local-build"
rm -rf "$build_dir"
mkdir -p "$build_dir/patch-classes" "$build_dir/extension-classes" "$build_dir/dex" "$build_dir/bundle/META-INF" "$build_dir/bundle/extensions"
javac -cp "$MORPHE_DESKTOP_JAR" -d "$build_dir/patch-classes" "$repo_dir/patches/src/main/java/local/reddit/RedditModePatch.java"
javac -cp "$MORPHE_DESKTOP_JAR:$ANDROID_JAR" -d "$build_dir/extension-classes" "$repo_dir/extensions/extension/src/main/java/local/reddit/extension/RedditMode.java"
java -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 26 --lib "$ANDROID_JAR" --output "$build_dir/dex" "$build_dir"/extension-classes/local/reddit/extension/*.class
cp -R "$build_dir/patch-classes/." "$build_dir/bundle/"
cp "$build_dir/dex/classes.dex" "$build_dir/bundle/extensions/reddit-mode.mpe"
cat > "$build_dir/bundle/META-INF/MANIFEST.MF" <<'EOF'
Manifest-Version: 1.0
Name: Local Reddit Patches
Description: Hide interactive posts and add NSFW mode
Version: 0.1.0
Timestamp: 1790690000000
Source: local
Author: Local
Contact: na
Website: na
License: GPLv3
Patcher-Version: 1.14.0

EOF
jar cfm "$repo_dir/reddit-mode.mpp" "$build_dir/bundle/META-INF/MANIFEST.MF" -C "$build_dir/bundle" .
printf 'Built %s\n' "$repo_dir/reddit-mode.mpp"
