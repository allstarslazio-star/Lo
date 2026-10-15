#!/usr/bin/env sh
# Minimal Gradle 8.9 bootstrap wrapper. Downloads the pinned distribution on first use.
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.9
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
DIST_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-$GRADLE_VERSION-bin/nerdfm-bootstrap"
GRADLE_BIN="$DIST_DIR/gradle-$GRADLE_VERSION/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$DIST_DIR"
    ZIP_FILE="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
    if [ ! -f "$ZIP_FILE" ]; then
        PART_FILE="$ZIP_FILE.part"
        rm -f "$PART_FILE"
        if command -v curl >/dev/null 2>&1; then
            curl --fail --location --retry 2 --output "$PART_FILE" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
        elif command -v wget >/dev/null 2>&1; then
            wget -O "$PART_FILE" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
        else
            echo "ERROR: Gradle $GRADLE_VERSION is not cached and neither curl nor wget is available." >&2
            exit 1
        fi
        mv "$PART_FILE" "$ZIP_FILE"
    fi
    unzip -q -o "$ZIP_FILE" -d "$DIST_DIR"
fi
exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
