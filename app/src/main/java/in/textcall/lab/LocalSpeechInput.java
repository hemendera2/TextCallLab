package in.textcall.lab;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
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
    interface LanguageCheck { void done(String message, boolean installed); }
    /** Real OS language-pack capability check; no audio recording or cloud fallback. */
    void checkHindi(LanguageCheck result) {
        if (!isSupported()) {
            result.done("No on-device speech recognizer registered on this phone.", false);
            return;
        }
        if (Build.VERSION.SDK_INT < 33) {
            result.done("On-device recognizer exists. Installed Hindi pack cannot be checked by this Android version.", false);
            return;
        }
        // Only invoke Android 13+ APIs behind the explicit runtime gate above.
        checkHindiApi33(result);
    }

    // Android lint does not propagate TargetApi to this anonymous API-33-only
    // RecognitionSupportCallback implementation. Runtime gate is repeated here.
    @android.annotation.TargetApi(33)
    @android.annotation.SuppressLint("NewApi")
    private void checkHindiApi33(LanguageCheck result) {
        if (Build.VERSION.SDK_INT < 33) {
            result.done("Hindi pack inspection requires Android 13 or newer.", false);
            return;
        }
        final SpeechRecognizer probe;
        try {
            probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
            request.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            probe.checkRecognitionSupport(request, context.getMainExecutor(),
                new RecognitionSupportCallback() {
                    private void close() {
                        try { probe.destroy(); } catch (Exception ignored) { }
                    }
                    @Override public void onSupportResult(RecognitionSupport support) {
                        boolean installed = false;
                        for (String tag : support.getInstalledOnDeviceLanguages()) {
                            if (tag.equalsIgnoreCase("hi-IN") || tag.equalsIgnoreCase("hi")
                                    || tag.toLowerCase(java.util.Locale.ROOT).startsWith("hi-")) {
                                installed = true; break;
                            }
                        }
                        String message = installed ? "Offline Hindi recognition pack is installed. Test real Hindi audio next."
                                : support.getSupportedOnDeviceLanguages().isEmpty()
                                  ? "Hindi not listed as installed; on-device recognizer may not support this language."
                                  : "Hindi pack not installed; check Android offline speech language downloads.";
                        close();
                        result.done(message, installed);
                    }
                    @Override public void onError(int error) {
                        close();
                        result.done("Hindi recognition support query unavailable (error "+error+"). Try microphone test.", false);
                    }
                });
        } catch (Exception e) {
            result.done("Could not check offline Hindi recognizer: " + e.getClass().getSimpleName(), false);
        }
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
