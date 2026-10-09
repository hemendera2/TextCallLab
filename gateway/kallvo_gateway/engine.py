"""Server-side offline speech recognition, local response and matching Supertonic voice."""
import json
import os
from pathlib import Path
from threading import Lock
from urllib.request import Request, urlopen
from urllib.parse import urlsplit

def quick_response(message,history):
    t=message.lower().strip()
    hindi=any("\u0900"<=c<="\u097f" for c in t) or any(
        phrase in (" "+t+" ") for phrase in (" hai "," kya "," kal "," baje "," mujhe "," aap "))
    if any(x in t for x in ("otp","password","cvv","ओटीपी","पासवर्ड")):
        return "कृपया ओटीपी या पासवर्ड साझा न करें।" if hindi else "Never share your OTP or password."
    if any(x in t for x in ("ambulance","heart attack","accident","आपातकाल","इमरजेंसी")):
        return "आपात स्थिति में तुरंत 112 पर कॉल करें।" if hindi else "Please call emergency services immediately."
    if any(x in t for x in ("appointment","booking","meeting","मिलना","अपॉइंटमेंट","मीटिंग")):
        return ("कृपया दिन और समय बताइए। मैं अपॉइंटमेंट कन्फर्म नहीं कर सकता।" if hindi
                else "Which day and time? I cannot confirm a booking.")
    if history and "कृपया दिन और समय" in history[-1][1] and any(
            x in t for x in ("baje","kal","बजे","कल","tomorrow","am","pm")):
        return "समय समझ गया। लेकिन अपॉइंटमेंट मालिक की पुष्टि के बिना कन्फर्म नहीं है।"
    if t in ("hello","hi","namaste","नमस्ते","हैलो"):
        return "नमस्ते। मैं KALLVO एआई सेक्रेटरी हूँ। कैसे मदद करूँ?"
    return ""

def _local_ollama():
    url=os.getenv("KALLVO_OLLAMA_URL","http://127.0.0.1:11434/api/chat")
    parsed=urlsplit(url)
    if parsed.scheme!="http" or parsed.hostname not in ("localhost","127.0.0.1","::1") or parsed.path!="/api/chat":
        raise RuntimeError("Ollama URL must point only to local loopback /api/chat")
    return url

class LiveEngine:
    def __init__(self):
        # All costly imports delayed until server startup; unit tests need no models.
        import numpy as np
        import sherpa_onnx
        from faster_whisper import WhisperModel
        self.np=np
        self.voice_lock=Lock()
        self.asr_lock=Lock()
        source=Path(os.environ["KALLVO_SUPERTONIC_DIR"]).resolve()
        assets={}
        for name in ("duration_predictor.int8.onnx","text_encoder.int8.onnx",
                     "vector_estimator.int8.onnx","vocoder.int8.onnx",
                     "tts.json","unicode_indexer.bin","voice.bin"):
            matches=list(source.rglob(name))
            if len(matches)!=1:
                raise RuntimeError("Voice model missing or ambiguous: "+name)
            assets[name]=str(matches[0])
        self.sherpa=sherpa_onnx
        self.tts=sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                supertonic=sherpa_onnx.OfflineTtsSupertonicModelConfig(
                    duration_predictor=assets["duration_predictor.int8.onnx"],
                    text_encoder=assets["text_encoder.int8.onnx"],
                    vector_estimator=assets["vector_estimator.int8.onnx"],
                    vocoder=assets["vocoder.int8.onnx"],
                    tts_json=assets["tts.json"],unicode_indexer=assets["unicode_indexer.bin"],
                    voice_style=assets["voice.bin"]),
                num_threads=2,debug=False,provider="cpu")))
        self.asr=WhisperModel(os.getenv("KALLVO_ASR_MODEL","base"),
                              device="cpu",compute_type="int8",cpu_threads=4)
        self.url=_local_ollama()
        self.model=os.getenv("KALLVO_OLLAMA_MODEL","qwen3:0.6b")
        self.speaker_id=int(os.getenv("KALLVO_VOICE_SID","1"))
        if self.speaker_id not in range(10):
            raise RuntimeError("Speaker ID must be 0 through 9")

    def transcribe(self,pcm,rate):
        import math
        from scipy.signal import resample_poly
        audio=self.np.frombuffer(pcm,dtype="<i2").astype("float32")/32768.0
        if rate!=16000:
            g=math.gcd(rate,16000)
            audio=resample_poly(audio,16000//g,rate//g)
        with self.asr_lock:
            segments,_=self.asr.transcribe(
                audio,beam_size=1,language=None,condition_on_previous_text=False,
                vad_filter=False,initial_prompt="Hindi and Hinglish telephone conversation.")
            return " ".join(x.text for x in segments).strip()[:500]

    def reply(self,message,history):
        fast=quick_response(message,history)
        if fast:
            return fast
        messages=[{"role":"system","content":
            "You are KALLVO, an automated AI secretary, not human. "
            "For Hindi or Hinglish reply in one short Devanagari Hindi sentence; "
            "for English use English. Ask one useful question. "
            "Never invent prices, booking confirmations, callbacks, or commitments. "
            "Never ask for OTP or payment. Emergencies: emergency services. /no_think"}]
        for caller,assistant in history[-3:]:
            messages.extend([{"role":"user","content":caller[:160]},
                             {"role":"assistant","content":assistant[:160]}])
        messages.append({"role":"user","content":message[:250]})
        request=Request(self.url,data=json.dumps({
            "model":self.model,"messages":messages,"stream":False,"think":False,
            "options":{"num_predict":40,"num_ctx":512,"temperature":0.3}}).encode(),
            headers={"Content-Type":"application/json"},method="POST")
        try:
            with urlopen(request,timeout=6) as response:
                result=json.load(response).get("message",{}).get("content","").strip()
            return result[:250] if result else "माफ़ कीजिए, कृपया सवाल दोहराएँ।"
        except Exception:
            return "माफ़ कीजिए, अभी सही जवाब नहीं दे पा रहा। कृपया दोबारा बताइए।"

    def synthesize(self,message,rate,speaker):
        import math
        from scipy.signal import resample_poly
        config=self.sherpa.GenerationConfig()
        config.sid=int(speaker)
        config.num_steps=5
        config.speed=1.0
        config.extra["lang"]="hi" if any("\u0900"<=c<="\u097f" for c in message) else "en"
        with self.voice_lock:
            audio=self.tts.generate(message[:300],config)
        samples=self.np.asarray(audio.samples,dtype="float32")
        if audio.sample_rate!=rate:
            g=math.gcd(audio.sample_rate,rate)
            samples=resample_poly(samples,rate//g,audio.sample_rate//g)
        return (self.np.clip(samples,-1,1)*32767).astype("<i2").tobytes()
