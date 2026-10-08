#!/bin/sh
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST=$(mktemp -d)
trap 'rm -rf "$DEST"' EXIT
javac -d "$DEST" "$BASE/app/src/main/java/in/textcall/lab/OfflineResponder.java" "$BASE/tools/TestOfflineResponder.java"
java -cp "$DEST" TestOfflineResponder
