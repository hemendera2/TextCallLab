package in.textcall.lab;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Android neural/offline speech bridge, including VoxSherpa.
 * A voice listed by TTS is NOT proof that synthesis can actually produce audio.
 * Track progress and asynchronous error callbacks; never count a TTS error as
 * successful playback, nor silently substitute another voice.
 */
final class LocalVoiceEngine {
    interface Callback { void onReady(boolean ok); }
    interface VoiceStatus { void onStatus(String status); }
    private final Context context;
    private final SharedPreferences settings;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Voice> offline = new ArrayList<>();
    private TextToSpeech tts;
    private boolean ready;
    private String activeEngine = "";
    private String lastStatus = "Voice engine not initialized";
    private String currentUtterance = "";
    private String stage = "";
    private long serial;
    private long playbackEpoch;
    private VoiceStatus statusListener;

    LocalVoiceEngine(Context context, SharedPreferences settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }
    void setStatusListener(VoiceStatus listener) {
        statusListener = listener;
        if (listener != null) listener.onStatus(lastStatus);
    }
    String lastStatus() { return lastStatus; }
    private void update(String status) {
        lastStatus = status;
        if (statusListener != null) main.post(() -> {
            if (statusListener != null) statusListener.onStatus(status);
        });
    }
    void start(Callback callback) { useEngine(settings.getString(Prefs.TTS_ENGINE, ""), callback); }

    void useEngine(String packageName, Callback callback) {
        serial++;
        final long ticket = serial;
        playbackEpoch++;
        currentUtterance = "";
        TextToSpeech previous = tts;
        tts = null;
        ready = false;
        activeEngine = "";
        offline.clear();
        if (previous != null) {
            try { previous.stop(); previous.shutdown(); } catch (Exception ignored) { }
        }
        final String requested = packageName == null ? "" : packageName;
        update("Connecting " + (requested.isEmpty() ? "Android default voice" : requested) + "…");
        TextToSpeech.OnInitListener listener = result -> main.post(() -> {
            if (ticket != serial) return;
            ready = result == TextToSpeech.SUCCESS && tts != null;
            offline.clear();
            if (ready) {
                // Android exposes the default engine, but no getCurrentEngine().
                // An explicit engine package is a request, not proof of synthesis.
                String systemDefault = tts.getDefaultEngine();
                activeEngine = requested.isEmpty()
                        ? (systemDefault == null ? "" : systemDefault) : requested;
                boolean installed = requested.isEmpty();
                if (!requested.isEmpty()) {
                    try {
                        for (TextToSpeech.EngineInfo e : tts.getEngines()) {
                            if (requested.equals(e.name)) { installed = true; break; }
                        }
                    } catch (Exception ignored) { }
                }
                if (!installed) {
                    ready = false;
                    update("Requested TTS engine package not installed: " + requested);
                } else {
                    try {
                        Set<Voice> listed = tts.getVoices();
                        if (listed != null) for (Voice voice : listed) {
                            if (voice == null || voice.getLocale() == null
                                    || voice.isNetworkConnectionRequired()) continue;
                            Set<String> features = voice.getFeatures();
                            if (features != null
                                    && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                            offline.add(voice);
                        }
                        offline.sort(Comparator.comparingInt((Voice v) -> -v.getQuality())
                                .thenComparing(v -> v.getLocale().getDisplayName(Locale.ENGLISH))
                                .thenComparing(Voice::getName));
                        update("Speech engine initialized (audio still untested) • "
                                + offline.size() + " offline speaker(s) listed");
                    } catch (Exception error) {
                        ready = false;
                        update("Voice list could not load: " + error.getClass().getSimpleName());
                    }
                }
            } else {
                update("Speech engine initialization failed; check Android Text-to-speech settings");
            }
            if (callback != null) callback.onReady(ready);
        });
        if (requested.isEmpty()) tts = new TextToSpeech(context, listener);
        else tts = new TextToSpeech(context, listener, requested);
    }

    String activeEngine() { return activeEngine; }
    boolean ready() { return ready; }
    List<Voice> voices() { return new ArrayList<>(offline); }
    List<TextToSpeech.EngineInfo> engines() {
        try { return tts == null ? new ArrayList<>() : tts.getEngines(); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }
    List<Locale> languages() {
        List<Locale> langs = new ArrayList<>();
        for (Voice v : offline) if (!langs.contains(v.getLocale())) langs.add(v.getLocale());
        return langs;
    }
    List<Voice> voicesFor(Locale locale) {
        List<Voice> list = new ArrayList<>();
        for (Voice v : offline) if (v.getLocale().equals(locale)) list.add(v);
        return list;
    }
    static String gender(Voice voice) {
        if (voice == null) return "";
        String id = voice.getName().toLowerCase(Locale.ROOT);
        // VoxSherpa Supertonic F/M voice IDs and named Kokoro speakers.
        if (id.matches(".*supertonic.*f[1-9].*")
                || id.matches(".*(^|[^a-z])(af|bf|hf|ef|ff|if|pf|jf|zf)_[a-z0-9_]+.*"))
            return "Female";
        if (id.matches(".*supertonic.*m[1-9].*")
                || id.matches(".*(^|[^a-z])(am|bm|hm|em|fm|im|pm|jm|zm)_[a-z0-9_]+.*"))
            return "Male";
        return "";
    }
    static String displayVoice(Voice voice) {
        if (voice == null) return "Unavailable";
        String id = voice.getName();
        String gender = gender(voice);
        String locale = voice.getLocale().getDisplayName(Locale.ENGLISH);
        String name = id;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(':'));
        if (slash >= 0 && slash+1 < name.length()) name = name.substring(slash+1);
        if (name.length() > 48) name = name.substring(name.length()-48);
        return (gender.isEmpty() ? "" : gender + " · ") + locale + " · " + name;
    }
    String chosenVoiceLabel() {
        String id = settings.getString(Prefs.VOICE, "");
        for (Voice v : offline) if (v.getName().equals(id)) return displayVoice(v);
        return offline.isEmpty() ? "No offline voice available" : "Choose a voice";
    }
    boolean speak(String text) { return speak(text, null); }
    boolean speak(String text, Runnable onSuccess) {
        return speak(text, onSuccess, null);
    }
    boolean speak(String text, Runnable onSuccess, Runnable onFailure) {
        if (!ready || tts == null) {
            update("Voice engine not ready. Reconnect it from Voice studio.");
            if (onFailure != null) main.post(onFailure);
            return false;
        }
        if (text == null || text.trim().isEmpty()) {
            update("No text to speak");
            if (onFailure != null) main.post(onFailure);
            return false;
        }
        Voice chosen = null;
        String id = settings.getString(Prefs.VOICE, "");
        if (!id.isEmpty()) {
            for (Voice voice : offline) if (voice.getName().equals(id)) { chosen = voice; break; }
            if (chosen == null) {
                update("Selected speaker disappeared. Refresh voice pack and choose again.");
                if (onFailure != null) main.post(onFailure);
                return false; // never silently change female to an unrelated voice
            }
        }
        if (chosen == null) {
            String desired = settings.getString(Prefs.LANGUAGE, "hi-IN");
            for (Voice v : offline) if (v.getLocale().toLanguageTag().equalsIgnoreCase(desired)) {
                chosen = v; break;
            }
        }
        if (chosen == null) for (Voice v : offline) if ("hi".equals(v.getLocale().getLanguage())) {
            chosen = v; break;
        }
        if (chosen == null && !offline.isEmpty()) chosen = offline.get(0);
        if (chosen == null) {
            update("No offline speech voice. Install speaker model in TTS engine.");
            if (onFailure != null) main.post(onFailure);
            return false;
        }
        final Voice target = chosen;
        if (tts.setVoice(target) == TextToSpeech.ERROR) {
            update("Voice refused by engine: " + target.getName());
            if (onFailure != null) main.post(onFailure);
            return false;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        tts.setAudioAttributes(attrs);
        tts.setPitch(1.0f);
        tts.setSpeechRate(Math.max(80,Math.min(120,settings.getInt(Prefs.SPEED,100)))/100f);

        final String utterance = UUID.randomUUID().toString();
        final long epoch = ++playbackEpoch;
        currentUtterance = utterance;
        stage = "Queued";
        update("Voice queued • " + displayVoice(target));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            private void accept(String state) {
                main.post(() -> {
                    if (epoch != playbackEpoch || !utterance.equals(currentUtterance)) return;
                    stage = state;
                    update(state + " • " + displayVoice(target));
                });
            }
            @Override public void onStart(String id) {
                if (utterance.equals(id)) accept("Synthesizing / playback starting");
            }
            @Override public void onBeginSynthesis(String id,int sampleRate,int format,int channels) {
                if (utterance.equals(id)) accept("Audio synthesis started (" + sampleRate + " Hz)");
            }
            @Override public void onDone(String id) {
                if (!utterance.equals(id)) return;
                main.post(() -> {
                    if (epoch != playbackEpoch || !id.equals(currentUtterance)) return;
                    stage = "Complete";
                    update("Speech completed • " + displayVoice(target));
                    if (onSuccess != null) onSuccess.run();
                });
            }
            @Override public void onError(String id) { onError(id, -1); }
            @Override public void onError(String id,int errorCode) {
                if (!utterance.equals(id)) return;
                main.post(() -> {
                    if (epoch != playbackEpoch || !id.equals(currentUtterance)) return;
                    stage = "Failed";
                    update("TTS synthesis/playback FAILED (code " + errorCode
                            + "). Test model in VoxSherpa Generate tab.");
                    if (onFailure != null) onFailure.run();
                });
            }
            @Override public void onStop(String id,boolean interrupted) {
                if (utterance.equals(id)) accept("Voice stopped" + (interrupted?" (interrupted)":""));
            }
        });
        int queueResult = tts.speak(text, TextToSpeech.QUEUE_FLUSH,null,utterance);
        if (queueResult != TextToSpeech.SUCCESS) {
            update("TTS speech request rejected immediately by the engine");
            if (onFailure != null) main.post(onFailure);
            return false;
        }
        // A successful queue only means accepted, NOT that audible audio exists.
        main.postDelayed(() -> {
            if (epoch != playbackEpoch || !utterance.equals(currentUtterance)) return;
            if ("Queued".equals(stage)) {
                update("Voice still queued after 8s. Neural engine/model may be stuck.");
            } else if ("Synthesizing / playback starting".equals(stage)) {
                update("Voice is still synthesizing after 8s; audio not confirmed.");
            }
        },8000L);
        main.postDelayed(() -> {
            if (epoch != playbackEpoch || !utterance.equals(currentUtterance)) return;
            if (!"Complete".equals(stage) && !"Failed".equals(stage)) {
                update("Voice has not finished in 25s. Check VoxSherpa Generate directly, then retry.");
            }
        },25000L);
        return true;
    }
    int musicVolume() {
        AudioManager manager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        return manager == null ? -1 : manager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }
    void stop() {
        ++playbackEpoch;
        currentUtterance = "";
        if (tts != null) tts.stop();
    }
    void shutdown() {
        serial++;
        ++playbackEpoch;
        currentUtterance = "";
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        ready = false;
        offline.clear();
    }
}
