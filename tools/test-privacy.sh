#!/usr/bin/env bash
set -euo pipefail
manifest="app/src/main/AndroidManifest.xml"
service="app/src/main/java/in/textcall/lab/BixbyAccessibilityService.java"
prefs="app/src/main/java/in/textcall/lab/Prefs.java"
voices="app/src/main/java/in/textcall/lab/LocalVoiceEngine.java"
main="app/src/main/java/in/textcall/lab/MainActivity.java"
access="app/src/main/res/xml/accessibility_service_config.xml"

# Static guardrails only; live Samsung call integration requires real device validation.
for permission in INTERNET READ_SMS READ_CONTACTS READ_CALL_LOG READ_PHONE_STATE RECORD_AUDIO     SYSTEM_ALERT_WINDOW CAPTURE_AUDIO_OUTPUT; do
  if grep -Fq "<uses-permission android:name=\"android.permission.${permission}\"" "$manifest"; then
    echo "FAIL: Unauthorized sensitive permission $permission"
    exit 1
  fi
done
grep -Fq 'android:allowBackup="false"' "$manifest"
grep -Fq 'android:packageNames="com.samsung.android.incallui"' "$access"
grep -Fq 'LIVE_SEND_CERTIFIED = false;' "$service"
grep -Fq 'TYPE_ACCESSIBILITY_OVERLAY' "$service"
grep -Fq 'No call transcripts are saved' "$main"
grep -Fq 'v.isNetworkConnectionRequired()' "$voices"
grep -Fq 'voice.speak(previewReply)' "$main"
grep -Fq 'android.intent.action.TTS_SERVICE' "$manifest"
grep -Fq '.remove(DIAGNOSTICS).remove(STATUS)' "$prefs"
grep -Fq 'android:icon="@drawable/app_mark"' "$manifest"

if grep -Eq 'performAction\(|\.ACTION_SET_TEXT|\.ACTION_CLICK|SharedPreferences\.Editor.*caller' "$service"; then
  echo 'FAIL: Uncertified Samsung UI automation detected'
  exit 1
fi
if grep -Eq 'HttpURLConnection|java\.net\.|okhttp|Retrofit|https?://' "$main" "$service" "$voices"; then
  echo 'FAIL: Network code path detected'
  exit 1
fi

echo 'PASS: local-only permissions; offline voice catalog; no caller capture; no call actions; optional overlay; custom icon'
