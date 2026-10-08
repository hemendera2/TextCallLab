# KALLVO — v0.9 product UX and voice studio

KALLVO is the working product brand for this iteration, **not trademark-cleared**. KALLVO has an original minimal three-tab native Android interface: Home, Talk, Settings. The old CallCompanion advanced diagnostic interface remains available only as "Open technician panel" in Settings.

## What's new
- Native redesigned shell with concise hierarchy, responsive cards, custom KAL(L)VO mark, meaningful no-fake-green statuses, and no repeated diagnostic walls.
- The separate Talk screen hosts real offline Qwen model import/load, microphone turn taking, bounded context, typed input, live CPU progress and a stop control.
- Settings groups Voice Studio, owner profile, experimental Bixby control, privacy/data, and technician tools instead of placing everything on Home.
- Voice Studio discovers installed Android TTS engines and offline voice variants, supports **explicit engine selection** and voice/sample preview, and lets the user filter by known named male/female speaker IDs only. It never relabels arbitrary low-pitched voices as male or high-pitched voices as female.
- The pitch is neutral so genuine neural voice timbre stays intact. A neural TTS engine such as Piper/Kokoro (e.g., the separately installable VoxSherpa TTS app) must be installed with voice models for neural quality. **No neural TTS model or engine is bundled inside this APK yet.** Quality and response time have not been benchmarked on the Samsung A52s. Some third-party voice engine apps may have their own network permissions/terms even though KALLVO does not.
- Crucially, the **selected Android neural voice applies to in-app Talk**. In a SIM call, Samsung Bixby Text Call speaks its **own** voice. No JNI or custom TTS voice is injected into the protected cellular call audio.
- Multi-turn Qwen responses run locally, no Internet or paid API. Live Samsung Text Call bridge remains EXPERIMENTAL and **has not passed a real A52s device acceptance test**.
- Owner profile instructions are always limited by AI error risk; no unverified booking or OTP promises.

## How to try a neural voice (no new phone root)
1. Install KALLVO's latest successfully built APK from GitHub Actions, and keep the existing downloaded verified Qwen3.5 GGUF in Downloads.
2. Open KALLVO > Settings > Voice studio. Select an installed system TTS engine. If needed install an optional compatible neural TTS engine (Piper/Kokoro), download voice pack from its **own** source and check its privacy/license first. It will show as an installed engine when Android exposes it.
3. Filter available named speakers by "Male" / "Female". If none are available, this is stated honestly rather than synthesizing the impression via pitch. Tap one to hear a sample. Match preview language to voice pack.
4. KALLVO > Talk > load offline Qwen and test actual response time. The model download is not repeated; re-import only if an old debug signing-key mismatch forces a complete uninstall.
5. Never enable auto-attend or auto-reply on private primary Jio calls until Bixby text input, speaker role identification and caller audio have been proven in a controlled test.

## Product release certification (still OPEN)
- Standalone neural TTS integration, high-quality female/male voice packs, clear redistribution licenses.
- Hindi/Hinglish pronunciation and tested voice quality on low and high RAM A52s variants.
- Native LLM CPU inference latency + 100-turn soak tests and device thermal/battery behavior.
- Real Samsung Text Call role/send controls and independently verified two-way caller voice delivery.
- Accessibility, privacy, Play policy, design QA, crashes and external beta release acceptance.

## Previous release history
# CallCompanion v0.8.1 — Qwen CPU responsiveness fix

Fix for v0.8 apparently getting stuck on "Offline Qwen is thinking on CPU…" on Galaxy A52s 5G:
- llama.cpp now keeps its context and token-batch in memory after loading, instead of recreating them for every utterance.
- Shared model context is cleared between requests to avoid cross-call data leakage.
- Lower 1,024-token context, 64-token prefill chunks and maximum 48 output tokens are designed for CPU-only constrained devices.
- A 35-second cooperative CPU generation budget checks after each prefill/decode step. A single native CPU kernel may exceed the deadline; this is not a hard OS-enforced kill.
- Talk screen displays live native phase (tokenizing, prefill, generating, failed/timeout) and time elapsed; Stop lets you abandon the UI request and signal the inference kernel.
- Queued, cancelled requests are dropped rather than silently running after later taps. No network permissions were added.
- **This does not prove end-to-end inference performance on A52s.** A local model's speed remains device/temperature/RAM dependent; if it cannot produce within a useful latency, compare another GGUF or use the faster deterministic rule flow. No real SIM call testing in GitHub Actions.

## Install after v0.8
Install APK from newest green Build Android APK GitHub Actions run. Existing signature may differ across debug builds; Android might require uninstalling the old APK first (will erase app-private imported GGUF/settings, but not the original verified file in Downloads). Reimport from Downloads if needed, load it, test a short phrase, and observe the phase/elapsed time. Leave automatic Samsung call replies OFF pending direct A52s UI acceptance.

## Previous notes
# CallCompanion v0.8 — Offline Qwen3.5 Android AI prototype

**Actual generative AI implementation is included.** This release compiles native arm64 llama.cpp using Android NDK; the user's existing Qwen3.5 0.8B Q4_K_M GGUF is imported on-device with Android's file picker. No server, Termux daemon, API key or INTERNET permission. Import/load/inference on the Samsung A52s and automatic cellular call delivery are **NOT device-tested**.

## Install and load downloaded GGUF
1. Get the newest **successful** Android APK artifact from this repository's Build Android APK GitHub Action. Debug APK is arm64 only (Galaxy A52s supports arm64).
2. Install APK. If Android reports signature mismatch with old debug APK, uninstall earlier CallCompanion first (loses old local settings). Keep the original downloaded GGUF in Downloads.
3. In **Talk** choose **1 · Choose downloaded GGUF**. Navigate **Downloads → CallCompanion → models → Qwen3.5-0.8B-Q4_K_M.gguf**.
4. Wait for a background **copy to app-private storage**, roughly 553 MB additional storage. This is a one-time import, not an Internet download.
5. Tap **2 · Load offline AI into memory**. Wait for a success message; model load can take several seconds and inference performance depends on RAM/CPU.
6. Type a short Hindi/Hinglish phrase in Talk and tap Send; if TTS offline voice installed, answer is spoken locally. Tap microphone only if Android has an on-device recognizer installed and permission granted. With model not loaded, app explicitly falls back to earlier limited scripted replies.
7. Optional owner instructions and **public** facts are edited in Privacy. Never enter secrets.
8. Termux may be closed. Model is loaded by the Android native library, not a localhost server.

## Call path and privacy
- **Samsung Bixby Text Call integration is experimental and unverified on the A52s.** A call is NOT guaranteed to be answered, caller transcript readable, or Bixby to speak generated replies.
- In Home: enable Samsung screen observation after manually granting Android Accessibility permissions, then test floating **AI Attend** with a consenting dummy caller. Keep auto-reply/auto-attend OFF until on-device diagnostics demonstrate the needed native controls.
- The **Use OFFLINE Qwen instead of scripts on calls** preference is independent and OFF by default. It generates Bixby replies on a separate worker and re-checks current caller text and unique Send button before attempting submission. If native Bixby controls differ, nothing is sent.
- **Real-time latency cannot be predicted from build success.** CPU prefill and generation of up to 64 tokens can take many seconds; no guaranteed 2–4 second replies.
- Optional encrypted **secretary brief** stores topic, number of turns and next action using Android Keystore AES-GCM. When local Qwen is loaded and selected, it may also store an AI-generated reason/priority/next action summary. This summary can contain private *derived* information; it stays encrypted on-device, can be erased via Home, and should be verified for hallucinations. Caller audio, raw transcript and caller number are not stored by this app. Samsung Text Call has its own separate data handling.
- Apps including ours must not collect caller OTP/password/payment secrets; AI is instructed to avoid committing to appointments or callback promises. Small models still hallucinate, so use only for consenting test calls until validated.
- No `INTERNET`, `READ_CALL_LOG`, `READ_CONTACTS`, `READ_SMS`, `READ_PHONE_STATE`, or `CAPTURE_AUDIO_OUTPUT` permissions. `RECORD_AUDIO` is only requested for voluntary foreground Talk-mode microphone input.

## Native build provenance
- Android arm64-v8a, NDK 27.2.12479018, CMake 3.22.1.
- Pinned upstream `ggml-org/llama.cpp` commit: `c35b66744f13cb0dcc476af063e112122eee9355`. The CI workflow verifies the commit hash. License (MIT) remains with the upstream source. No upstream native sources are committed into this repository.
- Build and static/unit tests in `.github/workflows/build-apk.yml`; **no real-phone generation performance or live-call acceptance tests** run in GitHub Actions.

## Previous release notes

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
