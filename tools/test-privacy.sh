#!/usr/bin/env bash
set -euo pipefail

manifest="app/src/main/AndroidManifest.xml"
service="app/src/main/java/in/textcall/lab/BixbyAccessibilityService.java"
prefs="app/src/main/java/in/textcall/lab/Prefs.java"
access="app/src/main/res/xml/accessibility_service_config.xml"

# These are static regression checks; they are not a substitute for device testing.
for permission in INTERNET READ_SMS READ_CONTACTS READ_CALL_LOG READ_PHONE_STATE RECORD_AUDIO; do
  if grep -Fq "android.permission.${permission}" "$manifest"; then
    echo "FAIL: Disallowed permission $permission"
    exit 1
  fi
done

grep -Fq 'android:allowBackup="false"' "$manifest"
grep -Fq 'android:packageNames="com.samsung.android.incallui"' "$access"
grep -Fq 'LIVE_SEND_CERTIFIED = false;' "$service"
grep -Fq '.remove(DIAGNOSTICS).remove(STATUS)' "$prefs"
grep -Fq '" | hasText="' "$service"

# Never persist raw transcript/answer in diagnostics.
if grep -Eq '"Caller: "|"Reply: "|"Draft: "|redacted\(content\)|redacted\(desc\)' "$service"; then
  echo 'FAIL: Raw text diagnostic path found'
  exit 1
fi

echo 'PASS: No network/contact/SMS/audio permissions; backups off; Samsung-only probe; sending locked; metadata-only diagnostic'
