#!/bin/sh
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST=$(mktemp -d)
trap 'rm -rf "$DEST"' EXIT
javac -d "$DEST" "$BASE/app/src/main/java/in/textcall/lab/OfflineResponder.java" "$BASE/app/src/main/java/in/textcall/lab/ConversationEngine.java" "$BASE/app/src/main/java/in/textcall/lab/CallTurnGuard.java" "$BASE/app/src/main/java/in/textcall/lab/PromptFormatter.java" "$BASE/app/src/main/java/in/textcall/lab/FastReply.java" "$BASE/tools/TestOfflineResponder.java" "$BASE/tools/TestConversationEngine.java" "$BASE/tools/TestCallTurnGuard.java" "$BASE/tools/TestPromptFormatter.java" "$BASE/tools/TestFastReply.java"
java -cp "$DEST" TestOfflineResponder
java -cp "$DEST" TestConversationEngine

java -cp "$DEST" TestCallTurnGuard

java -cp "$DEST" TestPromptFormatter

java -cp "$DEST" TestFastReply
