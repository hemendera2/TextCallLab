package in.textcall.lab;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline-only TTS preview. Voice gender is not exposed reliably by Android;
 * "deep" and "bright" are pitch presets, never claimed as biological voice identities.
 * No microphone, recording, network SDK, cloud model or telephony bridge.
 */
final class LocalVoiceEngine {
    interface Callback { void onReady(boolean ready); }
    private TextToSpeech tts;
    private boolean ready;
    private final Context context;
    private final SharedPreferences settings;
    private final List<Voice> offline = new ArrayList<>();

    LocalVoiceEngine(Context context, SharedPreferences settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }
    void start(Callback callback) {
        if (tts != null) { callback.onReady(ready); return; }
        tts = new TextToSpeech(context, code -> {
            ready = code == TextToSpeech.SUCCESS && tts != null;
            offline.clear();
            if (ready) {
                try {
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) for (Voice v : voices) {
                        if (v != null && v.getLocale() != null && !v.isNetworkConnectionRequired()) offline.add(v);
                    }
                    Collections.sort(offline, Comparator
                            .comparing((Voice v) -> v.getLocale().getDisplayName(Locale.ENGLISH))
                            .thenComparing(Voice::getName));
                } catch (Exception ignored) { ready = false; }
            }
            callback.onReady(ready);
        });
    }
    boolean ready() { return ready; }
    List<Voice> voices() { return new ArrayList<>(offline); }
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
    boolean speak(String text) { return speak(text, null); }
    boolean speak(String text, Runnable onFinish) {
        if (!ready || tts == null || text == null || text.trim().isEmpty()) return false;
        Voice chosen = null;
        String saved = settings.getString(Prefs.VOICE, "");
        for (Voice v : offline) if (v.getName().equals(saved)) { chosen = v; break; }
        if (chosen == null && !offline.isEmpty()) {
            for (Voice v : offline) if ("en".equals(v.getLocale().getLanguage())) {
                chosen = v;
                if ("IN".equals(v.getLocale().getCountry())) break;
            }
            if (chosen == null) chosen = offline.get(0);
        }
        if (chosen == null) return false;
        if (tts.setVoice(chosen) == TextToSpeech.ERROR) return false;
        String style = settings.getString(Prefs.STYLE, "Natural");
        float pitch = "Deep".equals(style) ? 0.82f : "Bright".equals(style) ? 1.16f : 1f;
        tts.setPitch(pitch);
        int speed = Math.max(75, Math.min(125, settings.getInt(Prefs.SPEED, 100)));
        tts.setSpeechRate(speed / 100f);
        final String utteranceId = java.util.UUID.randomUUID().toString();
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) {
                if (utteranceId.equals(id) && onFinish != null)
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onFinish);
            }
            @Override public void onError(String id) { }
        });
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS;
    }
    void stop() { if (tts != null) tts.stop(); }
    void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        ready = false;
        offline.clear();
    }
}
