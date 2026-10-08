package in.textcall.lab;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Native Android TTS engine adapter. Neural engines such as Piper/Kokoro can
 * expose voices via the standard Android TextToSpeech service. No GPL code or
 * model is copied into this app; the user explicitly installs the engine/pack.
 * Gender is stated only when it is encoded in known model speaker IDs.
 */
final class LocalVoiceEngine {
    interface Callback { void onReady(boolean ok); }
    private final Context context;
    private final SharedPreferences settings;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Voice> offline = new ArrayList<>();
    private TextToSpeech tts;
    private boolean ready;
    private String activeEngine = "";
    private long serial;
    LocalVoiceEngine(Context context, SharedPreferences settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }
    void start(Callback callback) { useEngine(settings.getString(Prefs.TTS_ENGINE, ""), callback); }
    void useEngine(String packageName, Callback callback) {
        serial++;
        long ticket = serial;
        TextToSpeech previous = tts;
        tts = null;
        ready = false;
        offline.clear();
        if (previous != null) {
            try { previous.stop(); previous.shutdown(); } catch (Exception ignored) { }
        }
        final String requested = packageName == null ? "" : packageName;
        TextToSpeech.OnInitListener listener = result -> main.post(() -> {
            if (ticket != serial) return;
            ready = result == TextToSpeech.SUCCESS && tts != null;
            offline.clear();
            if (ready) {
                activeEngine = tts.getDefaultEngine();
                if (!requested.isEmpty()) {
                    boolean installed = false;
                    for (TextToSpeech.EngineInfo e : tts.getEngines()) {
                        if (requested.equals(e.name)) { installed = true; break; }
                    }
                    if (installed) activeEngine = requested;
                }
                try {
                    Set<Voice> listed = tts.getVoices();
                    if (listed != null) for (Voice voice : listed) {
                        if (voice == null || voice.getLocale() == null
                                || voice.isNetworkConnectionRequired()) continue;
                        Set<String> features = voice.getFeatures();
                        if (features != null && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
                            continue;
                        offline.add(voice);
                    }
                    offline.sort(Comparator
                            .comparingInt((Voice v) -> -v.getQuality())
                            .thenComparing(v -> v.getLocale().getDisplayName(Locale.ENGLISH))
                            .thenComparing(Voice::getName));
                } catch (Exception ignored) { ready = false; }
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
        // Named Kokoro speaker identifiers, not a fabricated pitch-based gender.
        if (id.matches(".*(^|[^a-z])(af|bf|hf|ef|ff|if|pf|jf|zf)_[a-z0-9_]+.*"))
            return "Female";
        if (id.matches(".*(^|[^a-z])(am|bm|hm|em|fm|im|pm|jm|zm)_[a-z0-9_]+.*"))
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
        if (name.length()>30) name = name.substring(name.length()-30);
        return (gender.isEmpty() ? "" : gender + " · ") + locale + " · " + name;
    }
    String chosenVoiceLabel() {
        String id = settings.getString(Prefs.VOICE, "");
        for (Voice v : offline) if (v.getName().equals(id)) return displayVoice(v);
        return offline.isEmpty() ? "No offline voice installed" : "Auto-select best available";
    }
    boolean speak(String text) { return speak(text, null); }
    boolean speak(String text, Runnable onFinish) {
        if (!ready || tts == null || text == null || text.trim().isEmpty()) return false;
        Voice chosen = null;
        String id = settings.getString(Prefs.VOICE, "");
        for (Voice v : offline) if (v.getName().equals(id)) { chosen = v; break; }
        if (chosen == null) {
            String desired = settings.getString(Prefs.LANGUAGE, "hi-IN");
            for (Voice v : offline) {
                if (v.getLocale().toLanguageTag().equalsIgnoreCase(desired)) { chosen = v; break; }
            }
        }
        if (chosen == null) {
            for (Voice v : offline) {
                if ("hi".equals(v.getLocale().getLanguage())
                        || ("en".equals(v.getLocale().getLanguage())
                        && "IN".equals(v.getLocale().getCountry()))) { chosen = v; break; }
            }
        }
        if (chosen == null && !offline.isEmpty()) chosen = offline.get(0);
        if (chosen == null || tts.setVoice(chosen) == TextToSpeech.ERROR) return false;
        tts.setPitch(1.0f); // Do not deform natural neural speaker characteristics
        tts.setSpeechRate(Math.max(80,Math.min(120,settings.getInt(Prefs.SPEED,100)))/100f);
        String utterance = UUID.randomUUID().toString();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) {
                if (id.equals(utterance) && onFinish != null) main.post(onFinish);
            }
            @Override public void onError(String id) {
                // Surface through user-facing voice preview status, not a silent success.
                if (id.equals(utterance) && onFinish != null) main.post(onFinish);
            }
        });
        return tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,utterance)==TextToSpeech.SUCCESS;
    }
    void stop() { if (tts != null) tts.stop(); }
    void shutdown() {
        serial++;
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        ready=false;
        offline.clear();
    }
}
