#!/bin/sh
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST=$(mktemp -d)
trap 'rm -rf "$DEST"' EXIT
javac -d "$DEST" "$BASE/app/src/main/java/in/textcall/lab/OfflineResponder.java" "$BASE/app/src/main/java/in/textcall/lab/ConversationEngine.java" "$BASE/app/src/main/java/in/textcall/lab/CallTurnGuard.java" "$BASE/tools/TestOfflineResponder.java" "$BASE/tools/TestConversationEngine.java" "$BASE/tools/TestCallTurnGuard.java"
java -cp "$DEST" TestOfflineResponder
java -cp "$DEST" TestConversationEngine

java -cp "$DEST" TestCallTurnGuard
