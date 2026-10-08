package in.textcall.lab;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

/**
 * User-initiated microphone input via the OS on-device recognizer ONLY.
 * No fallback to cloud recognizers, no call capture or background recording.
 */
final class LocalSpeechInput {
    interface Callback {
        void onUpdate(String state);
        void onPartial(String text);
        void onFinal(String text);
        void onError(String error);
    }
    private final Context context;
    private SpeechRecognizer recognizer;
    private boolean listening;

    LocalSpeechInput(Context c) { context = c; }

    boolean isSupported() {
        return Build.VERSION.SDK_INT >= 31
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    }
    void listen(String language, Callback callback) {
        if (listening) return;
        if (!isSupported()) {
            callback.onError("This device has no registered ON-DEVICE speech recognizer. Offline speech pack may be unavailable. Use typed input; cloud fallback is disabled.");
            return;
        }
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    callback.onUpdate("Listening only inside this app…");
                }
                @Override public void onBeginningOfSpeech() { callback.onUpdate("Hearing speech…"); }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { callback.onUpdate("Processing speech locally…"); }
                @Override public void onError(int error) {
                    listening = false;
                    String explanation = error == SpeechRecognizer.ERROR_NO_MATCH
                            ? "Could not understand speech. Please try again."
                            : error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            ? "No speech detected. Tap the microphone again."
                            : error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                            ? "This speech language is not installed on the device."
                            : error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                            ? "On-device language model unavailable."
                            : "Offline recognizer error " + error + ". Check installed language packs.";
                    callback.onError(explanation);
                    cleanup();
                }
                @Override public void onResults(Bundle results) {
                    listening = false;
                    ArrayList<String> text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String best = text == null || text.isEmpty() ? "" : text.get(0).trim();
                    cleanup();
                    if (best.isEmpty()) callback.onError("No recognizable words. Try again.");
                    else callback.onFinal(best);
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) callback.onPartial(list.get(0));
                }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                    language == null || language.isEmpty() ? "hi-IN" : language);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            listening = true;
            recognizer.startListening(intent);
            callback.onUpdate("Starting on-device speech recognition…");
        } catch (Exception e) {
            listening = false;
            cleanup();
            callback.onError("Offline speech service did not start: " + e.getClass().getSimpleName());
        }
    }
    void stop() {
        listening = false;
        cleanup();
    }
    boolean isListening() { return listening; }
    private void cleanup() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            try { recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
    }
}
