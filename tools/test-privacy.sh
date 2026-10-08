#!/usr/bin/env bash
set -euo pipefail
manifest="app/src/main/AndroidManifest.xml"
service="app/src/main/java/in/textcall/lab/BixbyAccessibilityService.java"
prefs="app/src/main/java/in/textcall/lab/Prefs.java"
voices="app/src/main/java/in/textcall/lab/LocalVoiceEngine.java"
main="app/src/main/java/in/textcall/lab/MainActivity.java"
access="app/src/main/res/xml/accessibility_service_config.xml"

# Static guardrails only; live Samsung call integration requires real device validation.
for permission in INTERNET READ_SMS READ_CONTACTS READ_CALL_LOG READ_PHONE_STATE SYSTEM_ALERT_WINDOW CAPTURE_AUDIO_OUTPUT; do
  if grep -Fq "<uses-permission android:name=\"android.permission.${permission}\"" "$manifest"; then
    echo "FAIL: Unauthorized sensitive permission $permission"
    exit 1
  fi
done
grep -Fq 'android:allowBackup="false"' "$manifest"
grep -Fq 'android:packageNames="com.samsung.android.incallui"' "$access"
grep -Fq 'CallTurnGuard.isIncoming' "$service"
grep -Fq 'CallTurnGuard.safeSend' "$service"
grep -Fq 'Prefs.LIVE_REPLY, false' "$service"
grep -Fq 'Prefs.AUTO_ATTEND, false' "$service"
grep -Fq 's.editables != 1 || s.sendButtons != 1' "$service"
grep -Fq 'MAX_REPLIES = 12' "$service"
grep -Fq 'AndroidKeyStore' app/src/main/java/in/textcall/lab/PrivateBriefStore.java
grep -Fq 'AES/GCM/NoPadding' app/src/main/java/in/textcall/lab/PrivateBriefStore.java
grep -Fq 'System.loadLibrary("callcompanion_llm")' app/src/main/java/in/textcall/lab/LocalModel.java
grep -Fq 'getContentResolver().openInputStream' app/src/main/java/in/textcall/lab/LocalModel.java
grep -Fq 'private static native String nativeGenerate' app/src/main/java/in/textcall/lab/LocalModel.java
grep -Fq 'llama_model_load_from_file' app/src/main/cpp/callcompanion-llm.cpp
grep -Fq 'llama_memory_clear(mem,true)' app/src/main/cpp/callcompanion-llm.cpp
grep -Fq 'MAX_CPU_TIME=std::chrono::seconds(35)' app/src/main/cpp/callcompanion-llm.cpp
grep -Fq 'nativeProgress' app/src/main/java/in/textcall/lab/LocalModel.java
grep -Fq 'nativeCancel' app/src/main/java/in/textcall/lab/LocalModel.java
grep -Fq 'Stop slow AI inference' "$main"
grep -Fq 'watchInference(epoch)' "$main"
grep -Fq 'LLAMA_PROCESS_TYPE_DECODE' app/src/main/cpp/callcompanion-llm.cpp
grep -Fq 'Prefs.USE_LLM, false' "$service"
grep -Fq 'epoch != generationEpoch' "$service"
grep -Fq '!hash.equals(fingerprint(fresh.callerText))' "$service"
grep -Fq 'compileSdk 35' app/build.gradle
grep -Fq "abiFilters 'arm64-v8a'" app/build.gradle
grep -Fq 'TYPE_ACCESSIBILITY_OVERLAY' "$service"
grep -Fq 'No call transcripts are saved' "$main"
grep -Fq 'voice.isNetworkConnectionRequired()' "$voices"
grep -Fq 'voice.speak(previewReply)' "$main"
grep -Fq 'android.permission.RECORD_AUDIO' "$manifest"
grep -Fq 'android.speech.RecognitionService' "$manifest"
grep -Fq 'SpeechRecognizer.createOnDeviceSpeechRecognizer' app/src/main/java/in/textcall/lab/LocalSpeechInput.java
grep -Fq 'SpeechRecognizer.isOnDeviceRecognitionAvailable' app/src/main/java/in/textcall/lab/LocalSpeechInput.java
grep -Fq 'requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}' "$main"
grep -Fq 'inputSpeech.stop()' "$main"
grep -Fq 'No call recording, no cloud fallback' "$main"
grep -Fq 'private final List<String> turns' app/src/main/java/in/textcall/lab/ConversationEngine.java
grep -Fq 'android.intent.action.TTS_SERVICE' "$manifest"
grep -Fq '.remove(DIAGNOSTICS).remove(STATUS)' "$prefs"
grep -Fq 'android:icon="@drawable/app_mark"' "$manifest"
grep -Fq 'android:name=".SecretaryActivity"' "$manifest"
grep -Fq 'android:label="KALLVO"' "$manifest"
grep -Fq 'final String[] names={"Home","Talk","Settings"}' app/src/main/java/in/textcall/lab/SecretaryActivity.java
grep -Fq 'String gender = gender(voice);' "$voices"
grep -Fq 'KEY_FEATURE_NOT_INSTALLED' "$voices"
grep -Fq 'isNetworkConnectionRequired()' "$voices"
grep -Fq 'new TextToSpeech(context, listener, requested)' "$voices"
grep -Fq 'prefs.edit().putString(Prefs.TTS_ENGINE' app/src/main/java/in/textcall/lab/SecretaryActivity.java
grep -Fq 'VOICE CHARACTER' app/src/main/java/in/textcall/lab/SecretaryActivity.java
grep -Fq 'genderFilter' app/src/main/java/in/textcall/lab/SecretaryActivity.java

# Live actions are restricted to exact call UI, role attribution and opt-in.
if grep -Eq 'SharedPreferences\\.Editor.*caller|Log\\.[a-z]+\\(.*caller' "$service"; then
  echo 'FAIL: Possible caller data persistence/logging'
  exit 1
fi
if grep -Eq 'HttpURLConnection|java\.net\.|okhttp|Retrofit|https?://' "$main" "$service" "$voices"; then
  echo 'FAIL: Network code path detected'
  exit 1
fi

echo 'PASS: no internet; foreground mic consent; on-device-only STT; no caller transcript persistence; role-gated opt-in Samsung actions; encrypted briefs; offline TTS'
