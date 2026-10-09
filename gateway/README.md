# KALLVO real-call gateway: executable prototype, not yet enabled on a telephone line

Architecture: owner SIM number -> operator-approved call forward -> Exotel Exophone -> inbound App Bazaar Voicebot applet -> authenticated WebSocket WSS -> caller PCM16 -> local faster-whisper STT -> fast safety rules or local Ollama -> sherpa-onnx Supertonic 3 TTS -> Exotel outbound PCM16 -> caller.

This is a separate Linux backend. Android alone cannot intercept and inject arbitrary SIM call speech, and Samsung Text Call does not use KALLVO's selected voice. A working build does not mean anyone called a real number yet.

## What exists in source
- Authenticated inbound Exotel Voicebot WSS at /voicebot; handles connected/start/media/clear/stop, responds with PCM media/marks, and clears pending playback on barge-in.
- In-RAM short speech segmentation, 12-second maximum incoming buffer, one coalesced pending utterance, short conversation history per call, all memory cleared on stop.
- Hindi/Hinglish transcription via server-side faster-whisper, conservative instant answers for known intents, loopback-only Ollama Qwen for open questions.
- Server-side Supertonic 3 INT8 (same seven-file pack + speaker ID 0..9 as Android), output resampled to Exotel 8/16/24 kHz mono PCM16.
- Only authorized owner can select the next call's speaker through POST /control/voice with a separate admin bearer token. Android Calls settings may pair via this HTTPS endpoint. Credentials stored in Android Keystore, no PSTN audio routed through the phone app.
- No automatic bookings, cloud API vendor for model inference, call recording or saved transcript. Service does not persist callers' phone numbers or call content.

## Required owner/operator setup (not automatically provisioned)
1. Obtain an Exotel account in a supported India region, complete KYC, ask provider to ENABLE inbound Voicebot/AgentStream, assign Exophone.
2. Configure inbound App Bazaar flow containing the BIDIRECTIONAL Voicebot applet, with Basic Auth username/password in its WSS URL configuration; add passthru/human-routing fallback. One-way Stream applet cannot speak to the caller.
3. Provision an always-on Linux host with ample CPU/RAM for faster-whisper + Ollama + Supertonic. Python 3.11/3.12 recommended. This is not a zero-cost static webpage.
4. From repository root on Linux: cd gateway; python3 -m venv .venv; . .venv/bin/activate; pip install -r requirements.txt.
5. Install Ollama on the same Linux host, pull qwen3:0.6b, verify local URL http://127.0.0.1:11434/api/chat works. A larger model and proper hardware may be needed for accuracy/speed.
6. Get the identical Supertonic 3 zip already pinned in Android NeuralVoice.java from https://github.com/chukfinley/supertonic-tts/releases/download/model-v1/supertonic3-model.zip . Verify SHA256 34dcc85eb9e743d7dd2875d151149ea4d9b4f13890f8068db10aae8bdcbc7843 before extracting. Set KALLVO_SUPERTONIC_DIR to extraction path.
7. Put actual high-entropy private secrets into host secret manager/environment variables: KALLVO_STREAM_BASIC_USER (16+ chars), KALLVO_STREAM_BASIC_PASSWORD (24+ chars), KALLVO_ADMIN_TOKEN (24+ chars); set ASR and voice dirs in gateway/.env.example. NEVER commit secrets or caller audio.
8. Launch as a continuously running service: uvicorn kallvo_gateway.server:app --host 127.0.0.1 --port 8765 (from gateway directory); proxy publicly with secure HTTPS/WSS domain, valid TLS and websocket forwarding. Exotel URL: wss://YOUR_DOMAIN/voicebot?sample-rate=8000 . Configure actual Basic auth in Exotel so it sends Authorization header. Do not disable authentication.
9. Inside Android app: Settings -> Calls & Bixby -> Pair live-call voice, enter https://YOUR_DOMAIN/control/voice plus owner admin token. Choose same Supertonic voice in Voice settings; the voice speaker ID is synced for future inbound calls.
10. BEFORE forwarding a personal Jio number, test the Exophone from a trusted caller, at least five real Hindi/Hinglish question/response turns, interruptions, latency, selected voice and human fallback. Only then enable conditional/always call forwarding with your mobile carrier's consent and routing policies.

## Unit tests
From repository root: python3 -m unittest discover -s gateway/tests -v
Mock protocol/audio tests do not need real ASR, TTS, Ollama or Exotel credentials.

## OPEN deployment acceptance gates
- ACCOUNT_KYC_AND_VOICEBOT=OPEN
- PUBLIC_TLS_WSS_HOST_AND_SECRETS=OPEN
- ACTUAL_FASTER_WHISPER_ASR_HEARD_CALLER=OPEN
- VOICE_SOUND_IDENTICAL_TO_ANDROID_SELECTION=OPEN
- REAL_PSTN_5_PLUS_TURNS_AND_INTERRUPTION=OPEN
- PRODUCTION_LATENCY_UPTIME_AND_COSTS=OPEN

A successful CI build only checks source-level protocol simulation, not live phone calls. Energy-based VAD is heuristic and will need real caller testing/tuning; ambient noise may affect segmentation. Provider rate, uptime, compliance and fallback can create charges. Never ask the owner to paste API credentials into chat.

Official technical docs: https://developer.exotel.com/docs/agentstream/websocket-protocol and https://support.exotel.com/support/solutions/articles/3000108630-working-with-the-stream-and-voicebot-applet
