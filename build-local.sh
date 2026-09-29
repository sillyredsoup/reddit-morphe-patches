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
mkdir -p "$build_dir/patch-classes" "$build_dir/patch-dex" "$build_dir/bundle/META-INF" "$build_dir/bundle/extensions"
javac -cp "$MORPHE_DESKTOP_JAR" -d "$build_dir/patch-classes" "$repo_dir"/patches/src/main/java/local/reddit/*.java
java -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 28 --lib "$ANDROID_JAR" --classpath "$MORPHE_DESKTOP_JAR" --output "$build_dir/patch-dex" "$build_dir"/patch-classes/local/reddit/*.class
for extension in RedditMode AppPostFilter; do
    extension_dir="$build_dir/$extension"
    mkdir -p "$extension_dir/classes" "$extension_dir/dex"
    javac -cp "$MORPHE_DESKTOP_JAR:$ANDROID_JAR" -d "$extension_dir/classes" "$repo_dir/extensions/extension/src/main/java/local/reddit/extension/$extension.java"
    java -cp "$R8_JAR" com.android.tools.r8.D8 --min-api 26 --lib "$ANDROID_JAR" --output "$extension_dir/dex" "$extension_dir"/classes/local/reddit/extension/*.class
done
cp -R "$build_dir/patch-classes/." "$build_dir/bundle/"
cp "$build_dir/patch-dex/classes.dex" "$build_dir/bundle/classes.dex"
cp "$build_dir/RedditMode/dex/classes.dex" "$build_dir/bundle/extensions/reddit-mode.mpe"
cp "$build_dir/AppPostFilter/dex/classes.dex" "$build_dir/bundle/extensions/hide-app-posts.mpe"
cat > "$build_dir/bundle/META-INF/MANIFEST.MF" <<'EOF'
Manifest-Version: 1.0
Name: Local Reddit Patches
Description: Independent app post filter and NSFW bottom bar mode
Version: 0.2.1
Timestamp: 1790690117000
Source: https://github.com/sillyredsoup/reddit-morphe-patches
Author: sillyredsoup
Contact: na
Website: https://github.com/sillyredsoup/reddit-morphe-patches
License: GPLv3
Patcher-Version: 1.14.0

EOF
jar cfm "$repo_dir/reddit-mode.mpp" "$build_dir/bundle/META-INF/MANIFEST.MF" -C "$build_dir/bundle" .
mkdir -p "$repo_dir/bundles"
cp "$repo_dir/reddit-mode.mpp" "$repo_dir/bundles/reddit-mode.mpp"
printf 'Built %s\n' "$repo_dir/reddit-mode.mpp"
