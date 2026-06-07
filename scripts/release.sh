#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Build a versioned debug APK and publish it as a GitHub release vX.Y.Z.
#   scripts/release.sh
# Reads ./VERSION, builds the debug APK, then `gh release create`.
# Refuses if the tag already exists.
set -euo pipefail
cd "$(dirname "$0")/.."

ver="$(tr -d '[:space:]' < VERSION)"
tag="v$ver"

# Refuse if the tag already exists (locally or on origin).
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null 2>&1; then
  echo "error: tag $tag already exists locally" >&2; exit 1
fi
if git ls-remote --exit-code --tags origin "$tag" >/dev/null 2>&1; then
  echo "error: tag $tag already exists on origin" >&2; exit 1
fi
if gh release view "$tag" >/dev/null 2>&1; then
  echo "error: release $tag already exists" >&2; exit 1
fi

# Locate a JDK for Gradle if JAVA_HOME is not already usable.
if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version >/dev/null 2>&1; then
  for cand in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "$(/usr/libexec/java_home 2>/dev/null || true)"; do
    if [ -n "$cand" ] && [ -x "$cand/bin/java" ]; then
      export JAVA_HOME="$cand"; break
    fi
  done
fi
echo "Using JAVA_HOME=${JAVA_HOME:-<system default>}"

echo "Building debug APK for $tag ..."
./gradlew assembleDebug

apk="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$apk" ]; then
  echo "error: expected APK not found at $apk" >&2; exit 1
fi
size="$(du -h "$apk" | cut -f1)"
echo "APK ready: $apk ($size)"

notes="$(cat <<EOF
PharosVPN Android $tag — first public build (pre-alpha).

This is a **DEBUG** build for testing only — it is not Play-Store-signed and
should not be treated as a hardened release.

Dual-protocol VpnService client (AmneziaWG + XRay-REALITY) with cloud profile
sync. Bundles the caravel core engine v$ver (~85M native .aar).
EOF
)"

gh release create "$tag" "$apk" \
  --title "PharosVPN Android $tag" \
  --notes "$notes"

echo "Released $tag."
