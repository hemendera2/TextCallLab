# TextCall Lab — Samsung Bixby Text Call (experimental)

**Status: source prototype / unverified on A52s 5G.** Not an installed or tested APK. Does **not** promise natural conversational AI.

## What it does

- Runs an Android Accessibility Service **restricted to Samsung in-call UI** (`com.samsung.android.incallui`).
- After **you manually answer with Samsung Bixby Text Call**, inspects visible accessible UI and records small **local-only** UI diagnostics.
- Generates a **scripted, deterministic, 100% on-device** response (no paid API, no Internet permission).
- Can *attempt* typing and clicking SEND if **you explicitly enable auto-send** **and** configure *both* the exact caller-text view ID and exact send-button view ID. **OFF by default.**
- Does NOT answer normal voice calls, record microphone audio, bypass Samsung OS access restrictions, or install an LLM model.

### Important: Not yet generative AI

`OfflineResponder.java` uses keyword-based responses; it cannot freely converse like a human. We deliberately validate the Samsung screen bridge **before** adding a local generative model. On an A52s, LLM performance/latency will depend on the installed model and available RAM.

### Critical caution about automated send

On some Samsung versions, caller and your own replies may share the same view ID. In that case the simple prototype **cannot distinguish the speaker**. **DO NOT enable auto-send.** This is a diagnostic proof of concept, not production call handling. If accessibility node texts/IDs are absent, no reliable solution via this method has been established.

## Build free using only your Android phone + GitHub

No PC is needed for this method, but **internet access to GitHub is needed to compile**. Running the installed APK itself does not require an external AI API.

1. On phone browser, sign in to [GitHub](https://github.com/) and create a **new public repository** named `TextCallLab` (use no real caller data in the repo). Public GitHub Actions on standard hosted runners are free under GitHub's current published rules.
2. Add/upload **all files and folders** from this project into that repository while preserving directories. A mobile git client that can upload a folder or the GitHub web upload UI in **Desktop site** mode can help. Do not upload a ZIP unchanged as the repository contents; files must be extracted first.
3. In the repository, open **Actions** → **Build Android APK** → **Run workflow** (a push to main should also trigger it).
4. Once the build succeeds, open the workflow run → **Artifacts** → download **TextCallLab-debug-apk**. Extract the downloaded artifact ZIP on your Android phone, then install `app-debug.apk`.
5. If Android blocks sideloaded accessibility permission, open `Settings → Apps → TextCall Lab → ⋮ → Allow restricted settings` (if present). Then go to `Settings → Accessibility → Installed apps → TextCall Lab - Bixby screen access`, enable it yourself.
6. In TextCall Lab, enable monitoring. **Keep AUTO-SEND OFF.** Ask someone you trust to place a test call, tap Samsung's native **Bixby Text Call**, have the caller say a simple English sentence (supported language varies by device), end the call.
7. Return to the app, tap **Refresh diagnostics**. Check whether it displayed nodes and text IDs from Samsung's call screen. The app may or may not see the actual caller speech on your Samsung firmware.
8. Before sharing a screenshot/log with anyone, **redact names, phone numbers, and private caller texts**. Never enable auto-send until caller/assistant message distinction has been independently verified in an actual test.

## If build fails

Read the failure line from Actions and share the error message (not API keys or caller texts). This source has not been built in the current environment; GitHub Actions performs the actual Android compile.

## Security and consent

This experiment contains no `INTERNET`, `RECORD_AUDIO`, `READ_CALL_LOG` or `READ_PHONE_STATE` permissions. Accessibility itself is powerful: grant it only if you trust the code. Use only on your phone, ask trusted callers for consent on tests, and clearly identify the voice as an automated assistant.

This kind of autonomous Accessibility automation can be incompatible with Google Play accessibility policies for non-accessibility tools; this repo is intended as a personal sideloaded experiment, not a Play Store submission.

## Technical details

- Android application ID: `in.textcall.lab`
- `minSdk 29`, `targetSdk 35`, Java 17, Android Gradle Plugin 8.6.1
- GitHub Actions builds debug APK on standard Ubuntu hosted runner
- Source: original code; built to study a Samsung-supported call text workflow rather than bypass cellular call permissions

### Simplest phone-only upload method

Use **Termux** (free, from F-Droid), **git**, and **GitHub CLI (`gh`)**. See `PHONE_ONLY_STEPS.txt` for the commands. This uploads source from your phone into a new public GitHub repo; GitHub Actions produces the APK for you. You never need a PC. The commands need a GitHub login, and mobile network data may be consumed for the build/download.
