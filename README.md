# CallCompanion v0.7 — Samsung Text Call bridge experiment

**Build status:** GitHub CI validates Java logic and APK compilation. **On-device incoming Jio call automation remains unverified on Samsung A52s 5G.** Do not claim that installation alone gives a working autonomous AI secretary.

Version 0.7 also checks the underlying Samsung window when an accessibility overlay is visible, and provides a manual Try AI Attend shortcut when the incoming label is not exposed. All call-control features remain OFF by default and unverified on the A52s.\n\n## What is implemented
- One Android app, no PC or Termux needed after installation, no subscription and no API charges.
- Offline **Talk** mode: opt-in microphone -> strictly on-device Android SpeechRecognizer -> context-aware scripted response -> installed offline Android TTS speaker.
- A52s Samsung in-call Accessibility only: experimental **Try AI Attend (Bixby)** overlay and opt-in **Automatically try Samsung Text Call**. Uses Samsung exact UI ID `ai_call_floating_button_container` and an accessible Text Call answer confirmation. Does not use generic tap coordinates, dialer replacement or root.
- Experimental **Auto-reply to confirmed caller text**: local scripted responses, gated on explicit incoming-speaker marking, one visible editable field, one whitelisted Samsung send button, cooldown, max 12 sends. If the A52s One UI provides no speaker labeling or safe send ID, **NO automatic replies are sent**. This is expected fail-closed behavior, not a hidden success.
- **Encrypted call brief** option: uses Android Keystore AES-GCM; only inferred category, number of recognized turns, and suggested action. **No raw conversation, caller ID, phone number or voice audio saved by our app**. Samsung Phone may independently retain Bixby transcripts.
- User's public owner instructions affect scripted responses, but there is **no Qwen or other generative language model bundled**. A human-level AI secretary is NOT delivered here.

## Strict privacy controls
- Every automatic action switch OFF by default. Explicit confirmation in app before enabling.
- Android accessibility permission is granted/revoked by Android system only. It grants sensitive UI-reading capability; enable only for trusted test calls.
- No `INTERNET`, `READ_CALL_LOG`, `READ_CONTACTS`, `READ_SMS`, `READ_PHONE_STATE`, `SYSTEM_ALERT_WINDOW`, or `CAPTURE_AUDIO_OUTPUT` permission. `RECORD_AUDIO` is optional for foreground **Talk** tab only, not used on carrier calls.
- App has no analytics, cloud calls or external AI SDKs. Backups disabled; encrypted local brief can be deleted.
- Not independently security audited, and Samsung's own Bixby service can process or save call information separately.

## How to test without jeopardizing personal calls
1. Install `app-debug.apk` from the latest green **Build Android APK** GitHub Actions run. If debug key differs, uninstall old app before install (deletes old settings).
2. Samsung Phone > Settings > Bixby Text Call > enable/download English voice pack if available. Confirm with trusted caller.
3. Open **CallCompanion** -> Home -> use **Observe Samsung call UI** plus optional **Floating call shortcut**. Android will require one-time accessibility permission. Leave both auto-attend and auto-reply OFF for the first test.
4. Get a dummy incoming call from a consenting person. See if floating shortcut is visible and tap **Try AI Attend (Bixby)**. It may fail if Samsung screen controls differ.
5. In Bixby Text Call ask one dummy enquiry. After call, open **View Samsung bridge diagnostics** to see only structure/role availability. If role marker or whitelisted send button missing, automatic reply is blocked and requires A52s-specific integration, not blindly enabling more permissions.
6. Only if Android/Samsung bridge is accurately recognized during controlled testing, choose explicit automatic answer/reply toggles. Do NOT enable automation blindly for real private calls.
7. For encrypted summary, enable **Save encrypted category-only call brief** and read the result on Home approximately 45 seconds after the last Samsung UI event. No promise of appointment, callback or transfer is performed.

**Important blocker:** This build has no direct SIM audio injection or capture; Bixby Text Call depends on Samsung firmware and its own voice-processing/privacy systems. The Github A36 automation reference is not A52s compatibility proof. No reliable zero-cost generic Android method has been proven for unlimited fully conversational cellular-call assistants with no root/server.

## Previous v0.5 notes

# CallCompanion — v0.5 private voice lab (Samsung A52s 5G)

**Current release is a private on-device voice conversation prototype — NOT an autonomous SIM-call agent.**

## What's NEW in v0.5
- Native **Talk** tab: tap to speak, optional user-started turn-taking, and typed fallback.
- Mic capture is explicitly permission-gated and active **only while the app stays in the foreground**.
- Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` (Android 12+) and refuses to silently fall back to a cloud recognizer if no on-device service/language is available.
- Stateless network architecture: the APK has **NO `INTERNET` permission**; typed and speech turns are not uploaded by our app.
- Offline TTS reply through Android TextToSpeech with a chosen installed offline voice. Hands-free demo listens, responds, speaks, then starts another turn after TTS completes.
- `ConversationEngine`: a **simple rules-based** multi-turn responder with topic carryover, owner-provided public facts and up to 6 recent exchanges kept **in RAM only**. It varies replies by topic/turn but is **not an LLM** and cannot follow arbitrary natural-language instructions reliably.
- `Privacy` screen: owner/public-facts fields, editable experimental guidance, clear/reset controls. Do **not** put private information or secrets there.
- App still has no ability to capture protected cellular caller audio, inject synthesized voice into a carrier call, answer the call, or autonomously converse with that caller.
- The optional Samsung accessibility overlay remains an **inert preference shortcut**, not a live call AI feature.

### What about an offline LLM or Gemini?
An optional on-device small LLM (`llama.cpp` + Qwen tiny models) may improve response quality on phones with enough RAM, but it is NOT bundled or evaluated for latency in v0.5. Gemini free-tier calls require Internet, usage limits and disclosure of call content to a third party, so cloud upload is NOT integrated under the current privacy requirement. **The real cellular bridge must be independently proven before connecting a full AI to live calls.**

### Privacy change since v0.4
Version 0.4 declared no microphone access. v0.5 **adds `RECORD_AUDIO`** for opt-in local voice recognition. Android prompts the user on first voice use. No microphone foreground service, no contact/SMS/call-log permissions, no backups, no networking. Microphone is stopped when the app is paused or you leave Talk. Keep Samsung accessibility OFF unless deliberately running a test. Android's installed TTS service and Samsung's proprietary Text Call service have separate privacy practices, which this APK cannot override.

### Phone-only APK install
Get the latest successful build from **Actions → Build Android APK** in this repository. With Termux already authorized:
```bash
gh run list -R hemendera2/TextCallLab --limit 3
mkdir -p ~/storage/downloads/CallCompanionV05
gh run download RUN_ID -R hemendera2/TextCallLab -n TextCallLab-debug-apk -D ~/storage/downloads/CallCompanionV05
```
Use the actual successful `RUN_ID`. Install `Downloads/CallCompanionV05/app-debug.apk`. If the previous debug signing key differs, uninstall the old APK first (erases local settings). Once installed, Termux does not need to run. On devices missing on-device recognition, use the typed test and check Android language model availability—no cloud substitution.

## Existing v0.4 notes and technical background


**Current build: v0.4.** A modern native Android UI, a local offline voice catalog/preview and an experimental Samsung call-screen shortcut. **This is not yet an autonomous AI call attendant.** Normal cellular call audio is not available for generic third-party Android apps, and Samsung Bixby Text Call automation has **not** been validated end-to-end on this A52s.

## Functional capabilities

| Component | Current reality |
|---|---|
| Native home, voice studio, privacy screen and custom launcher icon | Implemented, APK compiled |
| Installed offline language/voice variants | Enumerated from Android TextToSpeech, network-dependent voices filtered |
| Voice character | Deep, Natural and Bright pitch presets; TTS voice gender is not reliably provided by Android |
| Offline response demo | Deterministic, scripted, one-turn keyword responder only; not a generative model |
| Preview audio | Plays through phone speaker on user action; **not injected into SIM calls** |
| Floating shortcut over Samsung in-call screen | Experimental opt-in Accessibility overlay; shows preset switch and app shortcut only |
| Automatic answering, caller dialogue and sending replies | **NOT implemented**; blocked in source |
| Call transcription availability on this device | **Not verified**; scanner records only structural metadata |
| Termux needed while using app | No. Termux only used to download/install builds; Android app runs independently once installed |

## Privacy/security boundaries

- No app `INTERNET`, `RECORD_AUDIO`, `READ_CONTACTS`, `READ_SMS`, `READ_CALL_LOG`, `READ_PHONE_STATE` or overlay permissions.
- Optional Accessibility Service restricted to `com.samsung.android.incallui`. It can see call-screen structure, which is sensitive access. Enable it **only for trusted testing** and turn it off when finished.
- Call transcript bodies/voice content never saved to local diagnostics by the probe. Diagnostics store only node counts and structural status. **No automatic message sending or control of call accept/end.**
- Android app backups disabled. Local profile, voice choices, and test diagnostics stay in app-private preferences; app has no network client.
- Android TTS engines and Samsung's built-in Text Call service are separate components with their own data handling/privacy behaviors. This app's lack of Internet permission is **not** a guarantee about those other apps.
- This has **not** received an external security audit or penetration test; do not use sensitive real customer calls for experiments.
- Voice model packs may need downloading through Android's text-to-speech settings. Selecting an installed offline voice avoids relying on an online synthesis voice, but CPU/battery/latency still depend on hardware.

## Install the latest build with only an Android phone

Existing repo: https://github.com/hemendera2/TextCallLab

1. Use GitHub Actions > **Build Android APK** on the repository; wait until the most recent run is green.
2. In Termux, while signed in with `gh`: `gh run list -R hemendera2/TextCallLab --limit 3`
3. Download a **specific successful run**, replacing `RUN_ID`: `mkdir -p ~/storage/downloads/CallCompanion && gh run download RUN_ID -R hemendera2/TextCallLab -n TextCallLab-debug-apk -D ~/storage/downloads/CallCompanion`
4. In Samsung **My Files**, open `Downloads/CallCompanion/app-debug.apk`. Debug APKs built on separate GitHub-hosted runners may have different signing keys. **Uninstall the older debug APK first** if Android reports a signature/update conflict; this resets local app data.
5. Start CallCompanion. The home screen, voice studio, and privacy screen work as a normal installed APK **without Termux running**.
6. For local speaking tests, pick a voice variant available on your phone and tap **Generate & speak locally**. If there are no offline voices, use the Android/Samsung TTS settings link once to install/download a compatible voice.
7. Only if you knowingly want to test the Samsung in-call panel: manually grant restricted Accessibility access in Android Settings, return to the app, enable **Observe Samsung call UI**, then enable **Floating call shortcut**. With a trusted consenting test caller, use Samsung's own **Bixby Text Call** option. The shortcut may or may not appear on your One UI version; **it does not answer, talk, or type in the call**.
8. After testing, toggle monitoring off and disable Accessibility Service in Android settings.

## Limitations, verification and next release gates

**Build green is not proof that the Samsung call-screen shortcut works in a live call.** The user must first confirm whether the Samsung in-call package and UI anchors are exposed. Further work on true autonomous answering requires a permitted telephony interface (which may have service costs), a suitable on-device generative model (hardware-dependent), verified speech recognition/synthesis latency and independent privacy review. None are delivered by this repo today.

Build CI runs static privacy guards, the deterministic responder tests, Gradle compilation, and uploads a debug APK. Do not claim zero latency, fully human voice, unlimited call handling, or universal Android compatibility.

## Implementation

- Java 17; minimum Android API 29; target/compile API 35.
- Native Android widgets and custom Canvas/vector artwork; no external runtime UI SDKs.
- No app telemetry, analytics, crash reporter, backend, API tokens, ads or billing.
- `BixbyAccessibilityService.java`: Samsung UI structural probe; opt-in experimental accessibility overlay.
- `LocalVoiceEngine.java`: Android TTS, installed offline variants only.
- `OfflineResponder.java`: deterministic safety-aware scripted response demo.
- `tools/test-privacy.sh`, `tools/test-local.sh`: static and scripted regression checks.

© CallCompanion personal prototype. Verify any third-party software/voice licenses before commercial redistribution.
