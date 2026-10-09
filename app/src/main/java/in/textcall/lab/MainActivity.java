package in.textcall.lab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.AdapterView;
import java.util.List;
import java.util.Locale;

/** Local-first native shell: no analytics, no networking and no privileged call control. */
public final class MainActivity extends Activity {
    private static final int BACK = Color.rgb(245, 248, 253);
    private static final int NAVY = Color.rgb(17, 31, 58);
    private static final int SUB = Color.rgb(91, 105, 127);
    private static final int BLUE = Color.rgb(37, 93, 238);
    private static final int BORDER = Color.rgb(224, 232, 244);
    private final String[] tabs = {"Home", "Talk", "Voices", "Privacy"};
    private SharedPreferences p;
    private LocalVoiceEngine voice;
    private LinearLayout body;
    private int currentTab = 0;
    private String previewReply = "";
    private boolean voiceInitialized = false;
    private LocalSpeechInput inputSpeech;
    private ConversationEngine session;
    private boolean autoConversation = false;
    private boolean foreground = false;
    private boolean pendingMicStart = false;
    private String talkStatus = "Ready for a new private voice session.";
    private TextView talkStatusView;
    private final int REQUEST_MIC = 4107;
    private final int REQUEST_MODEL = 4208;
    private long talkEpoch = 0L;
    private boolean modelThinking = false;
    private long modelThinkingSince = 0L;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        p = Prefs.get(this);
        voice = new LocalVoiceEngine(this, p);
        inputSpeech = new LocalSpeechInput(this);
        resetSession();
        render();
        voice.start(ready -> runOnUiThread(() -> {
            voiceInitialized = ready;
            if (!isFinishing()) render();
        }));
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (body != null) render();
        if (pendingMicStart) { pendingMicStart = false; startMic(); }
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACK);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(20), dp(22), dp(16));
        TextView logo = text("◉", 28, BLUE, true);
        header.addView(logo);
        LinearLayout name = new LinearLayout(this);
        name.setOrientation(LinearLayout.VERTICAL);
        name.setPadding(dp(12), 0, 0, 0);
        name.addView(text("CALLCOMPANION", 17, NAVY, true));
        name.addView(text("PERSONAL VOICE LAB  /  A52s", 10, SUB, true));
        header.addView(name);
        root.addView(header);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(6), dp(20), dp(22));
        scroller.addView(body);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(dp(8), dp(10), dp(8), dp(12));
        for (int i = 0; i < tabs.length; i++) {
            final int at = i;
            TextView item = text(tabs[i], 13, i == currentTab ? BLUE : SUB, i == currentTab);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(7), dp(12), dp(7), dp(12));
            item.setBackground(round(i == currentTab ? Color.rgb(236, 242, 255) : Color.WHITE, 15, 0));
            item.setOnClickListener(v -> { if (at != 1) stopTalk(); currentTab = at; render(); });
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        root.addView(nav);
        setContentView(root);
        if (currentTab == 0) showHome();
        else if (currentTab == 1) showTalk();
        else if (currentTab == 2) showVoice();
        else showPrivacy();
    }

    private void showHome() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(22));
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(17, 33, 70), Color.rgb(34, 62, 125), Color.rgb(39, 91, 151)});
        gradient.setCornerRadius(dp(26));
        hero.setBackground(gradient);
        hero.addView(text("✦  LOCAL-FIRST  ·  NO CLOUD ACCOUNT", 11, Color.rgb(146, 226, 255), true));
        space(hero, 10);
        hero.addView(text("Your voice.\nYour rules.", 32, Color.WHITE, true));
        space(hero, 5);
        hero.addView(text("A private voice playground and Samsung call-screen research companion.", 13,
                Color.rgb(216, 231, 254), false));
        View art = new SignalArt(this);
        hero.addView(art, new LinearLayout.LayoutParams(-1, dp(140)));
        body.addView(hero);
        space(body, 14);

        LinearLayout highlights = horizontal();
        highlights.addView(pill("₹0 API fees", Color.rgb(232, 239, 255), NAVY),
                new LinearLayout.LayoutParams(0, -2, 1f));
        highlights.addView(pill("On-device", Color.rgb(232, 239, 255), NAVY),
                new LinearLayout.LayoutParams(0, -2, 1f));
        highlights.addView(pill("No recorder", Color.rgb(232, 239, 255), NAVY),
                new LinearLayout.LayoutParams(0, -2, 1f));
        body.addView(highlights);
        space(body, 16);

        LinearLayout active = card();
        active.addView(text("CALL-SCREEN CONNECTION", 11, BLUE, true));
        space(active, 9);
        active.addView(text("Samsung Text Call bridge", 19, NAVY, true));
        space(active, 6);
        boolean enabled = isServiceEnabled();
        active.addView(text(enabled ? "Accessibility enabled on this device" :
                "One-time Samsung permission required", 13, enabled ?
                Color.rgb(23, 125, 97) : SUB, false));
        space(active, 12);
        active.addView(text("This app cannot intercept SIM audio or independently speak to a caller. Samsung Bixby Text Call must be started manually.", 12, SUB, false));
        space(active, 12);
        active.addView(action(enabled ? "Review phone permissions  ↗" :
                "Enable Samsung screen access  ↗", BLUE, Color.WHITE, () ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        space(active, 12);
        active.addView(toggle("Observe Samsung call UI", Prefs.ENABLED,
                "Metadata-only diagnostics. No call transcripts are saved."));
        space(active, 8);
        active.addView(toggle("Floating call shortcut (experimental)", Prefs.FLOATING,
                "Shows AI Attend button on a recognized Samsung call screen."));
        space(active, 8);
        active.addView(toggle("Automatically try Samsung Text Call", Prefs.AUTO_ATTEND,
                "Experimental: may attempt Bixby Text Call for all incoming calls. OFF by default."));
        space(active, 8);
        active.addView(toggle("Auto-reply to confirmed caller text", Prefs.LIVE_REPLY,
                "Experimental: role-labeled caller bubble and exact Send control required."));
        space(active, 8);
        active.addView(toggle("Use OFFLINE Qwen instead of scripts on calls", Prefs.USE_LLM,
                "Requires imported GGUF. AI runs on phone CPU. Reply may take much longer than fixed rules; OFF by default."));
        space(active, 8);
        active.addView(toggle("Save encrypted secretary brief", Prefs.SAVE_BRIEF,
                "Stores topic/next action. With offline Qwen, may include sensitive AI-derived details. Raw transcript and audio are not stored."));
        space(active, 12);
        active.addView(text("IMPORTANT: NOT YET TESTED on this A52s. Unknown Samsung controls fail closed. Test with consenting callers only. Real caller hears Samsung Bixby, not the in-app voice preset.", 12, SUB, false));
        body.addView(active);
        space(body, 14);
        LinearLayout brief = card();
        brief.addView(text("MY SECRETARY BRIEF", 11, BLUE, true));
        space(brief, 6);
        brief.addView(text("Last encrypted call note", 18, NAVY, true));
        space(brief, 9);
        TextView latest = text(new PrivateBriefStore(this).read(), 13, NAVY, false);
        brief.addView(latest);
        space(brief, 8);
        brief.addView(action("View Samsung bridge diagnostics", Color.rgb(234, 241, 255), BLUE, () -> {
            String status = p.getString(Prefs.STATUS, "No Samsung test event yet.");
            String diagnostic = p.getString(Prefs.DIAGNOSTICS, "No structural diagnostic available.");
            new AlertDialog.Builder(this).setTitle("Samsung UI status — no raw call content")
                    .setMessage(status + "\n\n" + diagnostic)
                    .setPositiveButton("Close", null).show();
        }));
        space(brief, 7);
        brief.addView(action("Erase encrypted brief", Color.rgb(246, 248, 252), NAVY, () -> {
            new PrivateBriefStore(this).clear();
            latest.setText(getString(R.string.kallvo_no_call_brief));
            toast("Brief erased.");
        }));
        body.addView(brief);
        space(body, 16);

        LinearLayout next = card();
        next.addView(text("VOICE STUDIO", 11, BLUE, true));
        space(next, 7);
        next.addView(text("Make the assistant sound right", 18, NAVY, true));
        space(next, 7);
        next.addView(text("Choose among installed offline voices, adjust tone and test instant spoken replies.", 13, SUB, false));
        space(next, 13);
        next.addView(action("Explore voice options  →", NAVY, Color.WHITE, () -> {
            currentTab = 2; render();
        }));
        body.addView(next);
        space(body, 16);

        LinearLayout truth = card();
        truth.addView(text("CAPABILITY STATUS", 11, BLUE, true));
        space(truth, 8);
        truth.addView(statusRow("Offline scripted reply", "Available", true));
        truth.addView(statusRow("Offline speech preview", voiceInitialized ? "Available with installed voice" : "Voice engine unavailable / loading", voiceInitialized));
        truth.addView(statusRow("On-device voice conversation", "Talk tab: experimental offline STT + rules + TTS", true));
        truth.addView(statusRow("Samsung call automation", "Experimental, A52s live test pending", false));
        truth.addView(statusRow("Offline generative model", LocalModel.get().isLoaded()
                ? "Loaded: llama.cpp local inference" : "Import/load Qwen GGUF in Talk", LocalModel.get().isLoaded()));
        body.addView(truth);
    }

    private void showTalk() {
        LinearLayout model = card();
        model.addView(text("LOCAL AI BRAIN · QWEN GGUF", 11, BLUE, true));
        space(model, 7);
        model.addView(text(LocalModel.get().isLoaded()
                ? "Offline AI model is READY"
                : LocalModel.isImported(this) ? "GGUF imported • tap Load" : "Import your downloaded GGUF", 20, NAVY, true));
        space(model, 5);
        TextView modelStatus = text(LocalModel.get().status(), 12, SUB, false);
        model.addView(modelStatus);
        space(model, 8);
        model.addView(action("1 · Choose downloaded GGUF", Color.rgb(235,243,255), BLUE, () -> {
            if (LocalModel.get().isLoaded()) { toast("Model already loaded. Restart app to replace it."); return; }
            Intent file = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            file.setType("*/*");
            file.addCategory(Intent.CATEGORY_OPENABLE);
            file.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(file, REQUEST_MODEL);
        }));
        space(model, 7);
        model.addView(action("2 · Load offline AI into memory", BLUE, Color.WHITE, () -> {
            modelStatus.setText(getString(R.string.kallvo_loading_gf));
            LocalModel.get().load(this, (ok, msg) -> runOnUiThread(() -> {
                modelStatus.setText(msg);
                toast(msg);
                render();
            }));
        }));
        space(model, 7);
        model.addView(text("Your 553 MB GGUF was downloaded to Downloads/CallCompanion/models. Choose that file. Import copies it ONCE into private app storage (about 553 MB extra); no network. Replies may take several seconds or longer on A52s.", 12, SUB, false));
        body.addView(model);
        space(body, 14);

        body.addView(text("Talk to your assistant", 27, NAVY, true));
        body.addView(text("Local conversation lab: you speak into THIS phone's microphone; it answers on the speaker. It does NOT hear or speak inside an existing SIM call.", 13, SUB, false));
        space(body, 14);

        LinearLayout controls = card();
        controls.addView(text("TWO-WAY VOICE LAB", 11, BLUE, true));
        space(controls, 9);
        controls.addView(text(inputSpeech.isSupported()
                ? "On-device speech service detected"
                : "Offline speech recognition unavailable", 14,
                inputSpeech.isSupported() ? Color.rgb(23, 125, 97) : Color.rgb(158, 77, 35), true));
        controls.addView(text("Microphone is only used with permission while this screen is active. No call recording, no cloud fallback, and no saved transcript history.", 12, SUB, false));
        space(controls, 12);
        String listening = inputSpeech.isListening() ? "Listening…" : "Tap to speak";
        controls.addView(action("🎙  " + listening, BLUE, Color.WHITE, () -> {
            autoConversation = false;
            requestMic();
        }));
        space(controls, 8);
        controls.addView(action(autoConversation ? "Stop hands-free session  ■" : "Start hands-free turn-taking  ▶",
                autoConversation ? Color.rgb(255, 235, 231) : Color.rgb(233, 242, 255),
                autoConversation ? Color.rgb(172, 54, 43) : BLUE, () -> {
                    if (autoConversation) { stopTalk(); render(); }
                    else {
                        autoConversation = true;
                        requestMic();
                        render();
                    }
                }));
        space(controls, 7);
        controls.addView(text("Hands-free mode: listens to one phrase, answers, then listens again only while the app remains on screen. It cannot interrupt a SIM call.", 11, SUB, false));
        space(controls, 12);
        talkStatusView = text(talkStatus, 13, SUB, false);
        controls.addView(talkStatusView);
        if (modelThinking) {
            space(controls, 9);
            controls.addView(action("Stop slow AI inference  ■", Color.rgb(250,235,234),
                    Color.rgb(171,55,40), () -> { stopTalk(); render(); }));
        }
        body.addView(controls);
        space(body, 14);

        LinearLayout sessionCard = card();
        sessionCard.addView(text("PRIVATE SESSION", 11, BLUE, true));
        space(sessionCard, 7);
        sessionCard.addView(text("Conversation memory", 19, NAVY, true));
        sessionCard.addView(text("Current topic and up to 6 recent exchanges are held in RAM until the session ends. When GGUF is loaded, replies come from the offline AI model; otherwise scripted fallback is used.", 12, SUB, false));
        space(sessionCard, 10);
        List<String> turns = session.recentTurns();
        if (turns.isEmpty()) sessionCard.addView(text("No turns yet. Load Qwen and ask a natural Hindi/Hinglish question, or try a typed sample.", 13, SUB, false));
        else for (String t : turns) {
            boolean assistant = t.startsWith("Assistant:");
            TextView bubble = text(t, 13, assistant ? BLUE : NAVY, false);
            bubble.setPadding(dp(11), dp(10), dp(11), dp(10));
            bubble.setBackground(round(assistant ? Color.rgb(233, 242, 255)
                    : Color.rgb(244, 247, 252), 13, 0));
            sessionCard.addView(bubble);
            space(sessionCard, 6);
        }
        space(sessionCard, 12);
        EditText typed = input("Type a sample phrase if your phone has no offline recognizer", "", 2);
        sessionCard.addView(typed);
        space(sessionCard, 9);
        sessionCard.addView(action("Send typed message & hear reply", NAVY, Color.WHITE, () -> {
            String utterance = typed.getText().toString().trim();
            if (utterance.isEmpty()) { toast("Enter a sample sentence first."); return; }
            processTalk(utterance);
        }));
        space(sessionCard, 8);
        sessionCard.addView(action("Clear private session & stop microphone", Color.rgb(239, 243, 250), NAVY,
                () -> { stopTalk(); resetSession(); render(); }));
        body.addView(sessionCard);
        space(body, 14);
        body.addView(text("Safety: The assistant discloses that it is automated. It cannot book appointments, initiate calls or forward messages, and will not request passwords or OTPs.", 12, SUB, false));
    }

    private void resetSession() {
        session = new ConversationEngine(
                p.getString(Prefs.PROFILE_NAME, "Owner"),
                p.getString(Prefs.PROFILE_INFO, ""),
                p.getString(Prefs.PROFILE_RULES, ""));
        talkStatus = "Ready for a private voice session.";
    }
    private void stopTalk() {
        autoConversation = false;
        pendingMicStart = false;
        ++talkEpoch;
        if (modelThinking) LocalModel.get().cancel();
        modelThinking = false;
        if (inputSpeech != null) inputSpeech.stop();
        if (voice != null) voice.stop();
        setTalkStatus("Session paused. Microphone off.");
    }
    private void setTalkStatus(String status) {
        talkStatus = status;
        if (talkStatusView != null) talkStatusView.setText(status);
    }
    private void requestMic() {
        if (!inputSpeech.isSupported()) {
            setTalkStatus("On-device speech recognizer unavailable. Use typed text; cloud recognition is disabled.");
            autoConversation = false;
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMicStart = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        startMic();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_MIC) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                pendingMicStart = true;
                if (foreground) { pendingMicStart = false; startMic(); }
            } else { pendingMicStart = false; autoConversation = false; setTalkStatus("Microphone not permitted. Typed replies remain available."); }
        }
    }
    private void startMic() {
        if (!foreground || currentTab != 1 || inputSpeech.isListening()) return;
        voice.stop();
        inputSpeech.listen(p.getString(Prefs.SPEECH_LANGUAGE, "hi-IN"), new LocalSpeechInput.Callback() {
            @Override public void onUpdate(String s) { setTalkStatus(s); }
            @Override public void onPartial(String s) {
                setTalkStatus("Listening: " + (s.length() > 120 ? s.substring(0,120) : s));
            }
            @Override public void onFinal(String text) { processTalk(text); }
            @Override public void onError(String s) {
                autoConversation = false;
                setTalkStatus(s);
            }
        });
    }
    private void processTalk(String phrase) {
        if (modelThinking) {
            setTalkStatus("An offline reply is already running. Tap Stop before a new request.");
            return;
        }
        inputSpeech.stop();
        if (LocalModel.get().isLoaded()) {
            final long epoch = ++talkEpoch;
            modelThinking = true;
            modelThinkingSince = android.os.SystemClock.elapsedRealtime();
            setTalkStatus("Qwen is starting local inference…");
            watchInference(epoch);
            final List<String> history = session.recentTurns();
            LocalModel.get().reply(p.getString(Prefs.PROFILE_NAME, "Owner"),
                    p.getString(Prefs.PROFILE_INFO, ""),
                    p.getString(Prefs.PROFILE_RULES, ""),
                    history, phrase, (reply, error, elapsed) -> runOnUiThread(() -> {
                        if (epoch != talkEpoch || currentTab != 1 || !foreground) return;
                        modelThinking = false;
                        if (reply.isEmpty()) {
                            autoConversation = false;
                            setTalkStatus("Offline model returned no reply: " + error);
                            return;
                        }
                        session.recordExchange(phrase, reply);
                        setTalkStatus("Qwen generated reply in " + elapsed + "ms. Speaking…");
                        render();
                        speakTalkReply(reply);
                    }));
        } else {
            String answer = session.respond(phrase);
            if (answer.isEmpty()) return;
            setTalkStatus("Scripted reply generated; load GGUF for real AI.");
            render();
            speakTalkReply(answer);
        }
    }
    private void watchInference(long epoch) {
        uiHandler.postDelayed(() -> {
            if (epoch != talkEpoch || !modelThinking || currentTab != 1 || !foreground) return;
            long seconds = (android.os.SystemClock.elapsedRealtime() - modelThinkingSince) / 1000L;
            String stage = LocalModel.get().progress();
            setTalkStatus("Qwen CPU: " + seconds + "s · " + stage
                    + (seconds > 35 ? " · Timeout requested; waiting for CPU to stop." : ""));
            watchInference(epoch);
        }, 1000L);
    }
    private void speakTalkReply(String answer) {
        boolean started = voice.speak(answer, () -> {
            setTalkStatus("Reply finished.");
            if (autoConversation && foreground && currentTab == 1) startMic();
        });
        if (!started) {
            autoConversation = false;
            setTalkStatus("No local TTS voice available. Install/select one in Voices.");
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_MODEL && result == RESULT_OK && data != null && data.getData() != null) {
            final android.net.Uri uri = data.getData();
            setTalkStatus("Importing GGUF to app-private storage…");
            LocalModel.get().importUri(this, uri, (ok, status) -> runOnUiThread(() -> {
                setTalkStatus(status);
                render();
                toast(status);
            }));
        }
    }

    private void showVoice() {
        body.addView(text("Voice studio", 28, NAVY, true));
        LinearLayout recognition = card();
        recognition.addView(text("VOICE INPUT LANGUAGE", 11, BLUE, true));
        recognition.addView(text("Used for in-app microphone recognition only; unsupported offline languages show an error without sending speech to a server.", 12, SUB, false));
        space(recognition, 8);
        final String[] recognitionTags = {"hi-IN", "en-IN", "en-US"};
        final String[] labels = {"Hindi / Hinglish (India)", "English (India)", "English (United States)"};
        LinearLayout choices = horizontal();
        for (int i=0; i<recognitionTags.length; i++) {
            final String tag = recognitionTags[i];
            TextView chip = pill(labels[i], p.getString(Prefs.SPEECH_LANGUAGE, "hi-IN").equals(tag)
                    ? BLUE : Color.rgb(237,243,251),
                    p.getString(Prefs.SPEECH_LANGUAGE, "hi-IN").equals(tag) ? Color.WHITE : NAVY);
            chip.setTextSize(10);
            chip.setOnClickListener(v -> { p.edit().putString(Prefs.SPEECH_LANGUAGE, tag).apply(); render(); });
            choices.addView(chip, new LinearLayout.LayoutParams(0,-2,1f));
        }
        recognition.addView(choices);
        body.addView(recognition);
        space(body, 14);
        body.addView(text("Choose language and voice variants installed on your phone. Your preferences are saved locally.", 13, SUB, false));
        space(body, 17);
        LinearLayout voiceCard = card();
        voiceCard.addView(text("VOICE LIBRARY", 11, BLUE, true));
        space(voiceCard, 10);

        List<Locale> languages = voice.languages();
        if (!voiceInitialized || languages.isEmpty()) {
            voiceCard.addView(text("No offline voices detected yet. Install an offline voice pack in Samsung / Android TTS settings, then reopen this app.", 13, SUB, false));
            space(voiceCard, 12);
            voiceCard.addView(action("Manage device speech voices  ↗", NAVY, Color.WHITE,
                    () -> openSpeechSettings()));
        } else {
            voiceCard.addView(text(languages.size() + " installed locale variants available · no network-required voices shown.", 12, SUB, false));
            space(voiceCard, 13);
            voiceCard.addView(text("LANGUAGE / ACCENT", 11, SUB, true));
            Spinner selector = new Spinner(this);
            String[] names = new String[languages.size()];
            int initially = 0;
            String savedLang = p.getString(Prefs.LANGUAGE, "");
            for (int i = 0; i < languages.size(); i++) {
                Locale l = languages.get(i);
                names[i] = l.getDisplayName(Locale.ENGLISH);
                if (l.toLanguageTag().equals(savedLang)) initially = i;
                if (savedLang.isEmpty() && "en".equals(l.getLanguage()) && "IN".equals(l.getCountry())) initially = i;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            selector.setAdapter(adapter);
            voiceCard.addView(selector);
            LinearLayout available = vertical();
            voiceCard.addView(available);
            selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Locale chosen = languages.get(position);
                    List<Voice> availableVoices = voice.voicesFor(chosen);
                    String currentVoice = p.getString(Prefs.VOICE, "");
                    boolean matches = false;
                    for (Voice candidate : availableVoices) {
                        if (candidate.getName().equals(currentVoice)) { matches = true; break; }
                    }
                    SharedPreferences.Editor editor = p.edit().putString(Prefs.LANGUAGE, chosen.toLanguageTag());
                    if (!matches && !availableVoices.isEmpty()) editor.putString(Prefs.VOICE, availableVoices.get(0).getName());
                    editor.apply();
                    populateVoices(available, availableVoices);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            selector.setSelection(initially);
            populateVoices(available, voice.voicesFor(languages.get(initially)));
            space(voiceCard, 8);
            voiceCard.addView(action("Install more device voices  ↗", Color.rgb(232, 239, 255), BLUE,
                    () -> openSpeechSettings()));
        }
        body.addView(voiceCard);
        space(body, 14);

        LinearLayout character = card();
        character.addView(text("VOICE CHARACTER", 11, BLUE, true));
        space(character, 6);
        character.addView(text("Deep • Natural • Bright", 20, NAVY, true));
        character.addView(text("These are pitch presets. Android TTS does not reliably identify voice gender; deep/bright are not guaranteed male/female voices.", 12, SUB, false));
        space(character, 12);
        LinearLayout styles = horizontal();
        final java.util.List<TextView> styleButtons = new java.util.ArrayList<>();
        for (String style : new String[]{"Deep", "Natural", "Bright"}) {
            boolean selected = p.getString(Prefs.STYLE, "Natural").equals(style);
            TextView b = pill(style, selected ? BLUE : Color.rgb(237, 243, 251), selected ? Color.WHITE : NAVY);
            b.setOnClickListener(v -> {
                p.edit().putString(Prefs.STYLE, style).apply();
                for (TextView chip : styleButtons) {
                    boolean active = chip.getText().toString().equals(style);
                    chip.setBackground(round(active ? BLUE : Color.rgb(237, 243, 251), 13, 0));
                    chip.setTextColor(active ? Color.WHITE : NAVY);
                }
            });
            styleButtons.add(b);
            styles.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        character.addView(styles);
        space(character, 13);
        int speed = p.getInt(Prefs.SPEED, 100);
        TextView speedLabel = text(getString(R.string.kallvo_speech_pace,speed), 12, SUB, true);
        character.addView(speedLabel);
        SeekBar seek = new SeekBar(this);
        seek.setMax(50); seek.setProgress(speed - 75);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                int value = 75 + progress;
                speedLabel.setText(MainActivity.this.getString(R.string.kallvo_speech_pace,value));
                if (fromUser) p.edit().putInt(Prefs.SPEED, value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        character.addView(seek);
        body.addView(character);
        space(body, 14);

        LinearLayout demo = card();
        demo.addView(text("LIVE OFFLINE PREVIEW", 11, BLUE, true));
        space(demo, 6);
        demo.addView(text("Hear a reply, without making a call", 19, NAVY, true));
        space(demo, 8);
        EditText userText = input("Try: Hello, can we schedule a meeting?", "Hello, who are you?", 2);
        demo.addView(userText);
        space(demo, 12);
        TextView result = text(previewReply.isEmpty() ?
                "This version creates rule-based responses, not generative AI conversations." : previewReply, 14, NAVY, false);
        demo.addView(result);
        space(demo, 13);
        demo.addView(action("Generate & speak locally  ▶", BLUE, Color.WHITE, () -> {
            previewReply = OfflineResponder.reply(userText.getText().toString(),
                    p.getString(Prefs.PROFILE_NAME, "Owner"), p.getString(Prefs.PROFILE_INFO, ""));
            result.setText(previewReply);
            if (!voice.speak(previewReply))
                toast("An installed offline voice is required. Choose one above.");
            else toast("Playing on phone speaker. NOT connected to a call.");
        }));
        space(demo, 8);
        demo.addView(action("Stop playback  ■", Color.rgb(239, 243, 250), NAVY, () -> voice.stop()));
        body.addView(demo);
    }

    private void populateVoices(LinearLayout list, List<Voice> choices) {
        list.removeAllViews();
        space(list, 8);
        list.addView(text("OFFLINE VOICE VARIANTS · " + choices.size(), 11, SUB, true));
        space(list, 7);
        for (Voice v : choices) {
            boolean selected = v.getName().equals(p.getString(Prefs.VOICE, ""));
            String shortName = v.getName();
            if (shortName.length() > 48) shortName = shortName.substring(0, 48) + "…";
            TextView row = action((selected ? "✓  " : "◯  ") + shortName,
                    selected ? Color.rgb(229, 237, 255) : Color.rgb(247, 249, 253),
                    selected ? BLUE : NAVY, () -> {
                        p.edit().putString(Prefs.VOICE, v.getName())
                                .putString(Prefs.LANGUAGE, v.getLocale().toLanguageTag()).apply();
                        populateVoices(list, choices);
                    });
            list.addView(row);
            space(list, 6);
        }
    }

    private void showPrivacy() {
        body.addView(text("Private by design", 28, NAVY, true));
        body.addView(text("Permissions, local storage and call behavior are always visible and under your control.", 13, SUB, false));
        space(body, 16);

        LinearLayout status = card();
        status.addView(text("SECURITY POSTURE", 11, BLUE, true));
        space(status, 10);
        status.addView(statusRow("No internet permission", "Enforced in manifest", true));
        status.addView(statusRow("Microphone permission", "Only for opt-in, in-app speech recognition; no recording saved", true));
        status.addView(statusRow("No contacts / SMS / call history", "Enforced in manifest", true));
        status.addView(statusRow("Samsung-only screen probe", "Opt-in, metadata only", true));
        status.addView(statusRow("Automatic Samsung call replies", "Disabled by default, experimental opt-in", false));
        status.addView(statusRow("Samsung Text Call processing", "Separate Samsung service", false));
        body.addView(status);
        space(body, 14);

        LinearLayout profile = card();
        profile.addView(text("LOCAL IDENTITY", 11, BLUE, true));
        space(profile, 8);
        profile.addView(text("Assistant profile", 19, NAVY, true));
        profile.addView(text("Only enter information you are comfortable sharing in a public call. Never enter passwords, OTPs, payment details or secrets.", 12, SUB, false));
        space(profile, 10);
        EditText name = input("Display name", p.getString(Prefs.PROFILE_NAME, "Boss"), 1);
        profile.addView(name);
        space(profile, 8);
        EditText info = input("Public facts / business details (optional)", p.getString(Prefs.PROFILE_INFO, ""), 3);
        profile.addView(info);
        space(profile, 8);
        EditText rules = input("Conversation instructions (public-safe guidelines, not an LLM prompt)",
                p.getString(Prefs.PROFILE_RULES, ""), 3);
        profile.addView(rules);
        space(profile, 10);
        profile.addView(action("Save profile on this device", BLUE, Color.WHITE, () -> {
            p.edit().putString(Prefs.PROFILE_NAME, name.getText().toString().trim())
                    .putString(Prefs.PROFILE_INFO, info.getText().toString().trim())
                    .putString(Prefs.PROFILE_RULES, rules.getText().toString().trim()).apply();
            resetSession();
            toast("Local profile saved.");
        }));
        body.addView(profile);
        space(body, 14);

        LinearLayout control = card();
        control.addView(text("DEVICE CONTROL", 11, BLUE, true));
        space(control, 9);
        control.addView(statusRow("Accessibility service", isServiceEnabled() ? "Enabled" : "Disabled", isServiceEnabled()));
        space(control, 10);
        control.addView(action("Review protected Android permissions  ↗", Color.rgb(239, 243, 250), NAVY, () ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        space(control, 8);
        control.addView(action("Clear local diagnostics now", Color.rgb(239, 243, 250), NAVY, () -> {
            p.edit().remove(Prefs.DIAGNOSTICS).remove(Prefs.STATUS).apply();
            toast("Diagnostics cleared.");
        }));
        space(control, 8);
        control.addView(action("Reset all app settings and profile", Color.rgb(255, 237, 235),
                Color.rgb(161, 42, 42), () -> new AlertDialog.Builder(this)
                        .setTitle("Delete local app settings?")
                        .setMessage("Clear the local profile, voice selection and diagnostic metadata, and turn off monitoring. Android's accessibility permission must be disabled separately if enabled.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dialog, which) -> {
                            voice.stop();
                            p.edit().clear().apply();
                            p = Prefs.get(this);
                            new PrivateBriefStore(this).clear();
                            previewReply = "";
                            render();
                            toast("Local settings reset. Disable Accessibility separately.");
                        }).show()));
        body.addView(control);
        space(body, 14);
        body.addView(text("IMPORTANT: Neither this screen nor a GitHub build proves zero security risk. Samsung's Text Call service and any installed speech engine have their own terms. Voice preview does not connect to a real SIM caller.", 12, SUB, false));
    }

    private LinearLayout card() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(17), dp(17), dp(17), dp(17));
        layout.setBackground(round(Color.WHITE, 22, BORDER));
        return layout;
    }
    private TextView statusRow(String title, String value, boolean positive) {
        TextView v = text((positive ? "✓  " : "•  ") + title + "\n    " + value, 13,
                positive ? NAVY : SUB, false);
        v.setPadding(0, dp(5), 0, dp(7));
        return v;
    }
    private TextView toggle(String title, String key, String desc) {
        // Opening an in-app confirmation is deliberate: sensitive monitoring is never enabled implicitly.
        boolean enabled = p.getBoolean(key, false);
        TextView control = text((enabled ? "☑  " : "☐  ") + title + "\n    " + desc, 13, NAVY, enabled);
        control.setPadding(dp(12), dp(12), dp(12), dp(12));
        control.setBackground(round(Color.rgb(242, 246, 253), 13, 0));
        control.setOnClickListener(v -> {
            boolean next = !p.getBoolean(key, false);
            if (!next) {
                p.edit().putBoolean(key, false).apply();
                render();
                return;
            }
            String warning = Prefs.USE_LLM.equals(key)
                    ? "Experimental: process caller text through your imported Qwen GGUF entirely on-device; no external API. Model quality and speed on A52s unmeasured, and Samsung Bixby bridge still unverified. Only use for consenting dummy calls."
                    : Prefs.AUTO_ATTEND.equals(key)
                    ? "Experimental: this may answer all incoming calls using Bixby if Samsung's EXACT controls are exposed. Your A52s is not validated. Test only with someone you trust."
                    : Prefs.LIVE_REPLY.equals(key)
                    ? "Experimental automatic replies: this will SEND locally scripted messages only when caller role and Samsung Send button are both unambiguous. No guarantee on A52s. Avoid sensitive calls."
                    : Prefs.SAVE_BRIEF.equals(key)
                    ? "Stores encrypted topic, actions and (if Qwen is loaded) AI-generated summary. A derived summary may contain private details. No raw audio or transcript is stored by this app. Erase anytime."
                    : "Sensitive Samsung screen access. The Android accessibility service must be enabled separately. No cloud connection.";
            new AlertDialog.Builder(this)
                    .setTitle("Confirm experimental permission")
                    .setMessage(warning)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Enable", (d, w) -> {
                        p.edit().putBoolean(key, true).apply();
                        render();
                    }).show();
        });
        return control;
    }
    private LinearLayout horizontal() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private TextView pill(String s, int color, int textColor) {
        TextView v = text(s, 11, textColor, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(7), dp(12), dp(7), dp(12));
        v.setBackground(round(color, 13, 0));
        return v;
    }
    private TextView action(String s, int bg, int fg, Runnable onTap) {
        TextView v = text(s, 13, fg, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(11), dp(14), dp(11), dp(14));
        v.setMinHeight(dp(48));
        v.setBackground(round(bg, 13, 0));
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(view -> onTap.run());
        return v;
    }
    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setLineSpacing(dp(2), 1.0f);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }
    private EditText input(String hint, String value, int minLines) {
        EditText e = new EditText(this);
        e.setTextSize(14);
        e.setTextColor(NAVY);
        e.setHintTextColor(SUB);
        e.setHint(hint);
        e.setMinLines(minLines);
        e.setSingleLine(minLines == 1);
        e.setText(value);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(round(Color.rgb(246, 249, 254), 13, BORDER));
        return e;
    }
    private void space(LinearLayout v, int h) {
        View filler = new View(this);
        v.addView(filler, new LinearLayout.LayoutParams(1, dp(h)));
    }
    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName()
                + "/" + BixbyAccessibilityService.class.getName());
    }
    private void openSpeechSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) { toast("Open Settings → General management → Text-to-speech."); }
        }
    }
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    @Override protected void onPause() {
        foreground = false;
        ++talkEpoch;
        if (modelThinking) LocalModel.get().cancel();
        modelThinking = false;
        if (inputSpeech != null) inputSpeech.stop();
        autoConversation = false;
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (inputSpeech != null) inputSpeech.stop();
        if (voice != null) voice.shutdown();
        super.onDestroy();
    }

    /** Purely decorative in-app hero graphic drawn locally (no image downloads). */
    static final class SignalArt extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader cachedGradient;
        SignalArt(android.content.Context context) { super(context); }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            super.onSizeChanged(w,h,oldw,oldh);
            float cx=w*.68f, cy=h*.53f;
            cachedGradient=new LinearGradient(cx-55,cy-60,cx+50,cy+65,
                    Color.rgb(132,237,245),Color.rgb(70,126,253),Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f * getResources().getDisplayMetrics().density);
            paint.setColor(Color.argb(100, 130, 220, 255));
            float cx = w * .68f, cy = h * .53f;
            for (int i = 0; i < 4; i++) {
                float r = h * (.17f + i * .14f);
                paint.setAlpha(140 - i * 26);
                canvas.drawCircle(cx, cy, r, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            paint.setShader(cachedGradient);
            canvas.drawCircle(cx, cy, h * .19f, paint);
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawArc(cx - h * .075f, cy - h * .08f, cx + h * .075f, cy + h * .08f,
                    20, 140, false, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 19; i++) {
                float x = w * .06f + i * w * .039f;
                float wave = (float) Math.sin(i * .89f);
                float bar = (8 + Math.abs(wave) * 30) * getResources().getDisplayMetrics().density / 3f;
                paint.setColor(Color.argb(170, 194, 244, 255));
                canvas.drawRoundRect(x, cy - bar, x + 3, cy + bar, 3, 3, paint);
            }
        }
    }
}
