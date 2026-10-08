package in.textcall.lab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.Switch;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * KALLVO's minimal owner-facing shell. Technician controls remain isolated in
 * the older MainActivity; no live-call success is implied by local AI readiness.
 */
public final class SecretaryActivity extends Activity {
    private static final int BG = Color.rgb(247,248,252);
    private static final int INK = Color.rgb(24,32,49);
    private static final int SOFT = Color.rgb(105,116,136);
    private static final int LINE = Color.rgb(230,233,240);
    private static final int BLUE = Color.rgb(60,91,219);
    private static final int PALE = Color.rgb(238,242,254);
    private static final int GREEN = Color.rgb(21,128,100);
    private static final int WHITE = Color.WHITE;
    private static final int MIC_REQUEST = 6401;
    private static final int FILE_REQUEST = 6402;
    private static final String TTS_APP = "com.CodeBySonu.VoxSherpa";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LocalVoiceEngine voice;
    private LocalSpeechInput speech;
    private ConversationEngine chat;
    private int tab=0;
    private String detail="";
    private String genderFilter="";
    private String callState="Ready";
    private boolean foreground;
    private boolean listeningLoop;
    private boolean thinking;
    private long generationId;
    private long began;
    private TextView progressView;
    private TextView voiceHealthView;
    private LinearLayout frame;
    private int currentScreen=0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        prefs=Prefs.get(this);
        speech=new LocalSpeechInput(this);
        voice=new LocalVoiceEngine(this,prefs);
        voice.setStatusListener(message -> {
            if (voiceHealthView != null && "voice".equals(detail)) voiceHealthView.setText(message);
        });
        resetChat();
        voice.start(ok -> runOnUiThread(() -> {
            if (!isFinishing()) show();
        }));
        show();
    }
    @Override protected void onResume() {
        super.onResume();
        foreground=true;
        if(frame!=null) show();
    }
    @Override protected void onPause() {
        foreground=false;
        stopTurn();
        super.onPause();
    }
    @Override protected void onDestroy() {
        stopTurn();
        voice.shutdown();
        super.onDestroy();
    }
    private void resetChat() {
        chat=new ConversationEngine(
                prefs.getString(Prefs.PROFILE_NAME,"Owner"),
                prefs.getString(Prefs.PROFILE_INFO,""),
                prefs.getString(Prefs.PROFILE_RULES,""));
        callState="Ready to talk";
    }
    private int dp(float value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable shape(int fill,int radius,int stroke) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if(stroke!=0) d.setStroke(dp(1),stroke);
        return d;
    }
    private TextView text(String value,int size,int color,boolean medium) {
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setLineSpacing(dp(2),1.04f);
        t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",
                Typeface.NORMAL));
        return t;
    }
    private LinearLayout vertical() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l=new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }
    private void pad(LinearLayout l,int gap) {
        View spacer=new View(this);
        l.addView(spacer,new LinearLayout.LayoutParams(1,dp(gap)));
    }
    private LinearLayout card() {
        LinearLayout v=vertical();
        v.setBackground(shape(WHITE,20,LINE));
        v.setPadding(dp(17),dp(17),dp(17),dp(17));
        return v;
    }
    private TextView press(String label,boolean primary,Runnable action) {
        TextView t=text(label,14,primary?WHITE:INK,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14),dp(14),dp(14),dp(14));
        t.setMinHeight(dp(49));
        t.setBackground(shape(primary?BLUE:PALE,14,0));
        t.setClickable(true);t.setFocusable(true);
        t.setOnClickListener(v->action.run());
        return t;
    }
    private EditText edit(String hint,String value,int lines) {
        EditText field=new EditText(this);
        field.setText(value);
        field.setTextSize(14);
        field.setTextColor(INK);
        field.setHintTextColor(SOFT);
        field.setHint(hint);
        field.setMinLines(lines);
        field.setMaxLines(lines==1?1:6);
        field.setSingleLine(lines==1);
        field.setPadding(dp(13),dp(12),dp(13),dp(12));
        field.setBackground(shape(BG,13,LINE));
        return field;
    }
    private TextView pill(String label,boolean selected) {
        TextView p=text(label,12,selected?BLUE:SOFT,true);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(13),dp(8),dp(13),dp(8));
        p.setBackground(shape(selected?PALE:BG,30,0));
        return p;
    }
    private void section(LinearLayout into,String over,String headline) {
        into.addView(text(over.toUpperCase(Locale.ROOT),11,BLUE,true));
        pad(into,4);
        into.addView(text(headline,22,INK,true));
    }
    private void caption(LinearLayout into,String value) {
        into.addView(text(value,12,SOFT,false));
    }
    private void gapCard(LinearLayout into,LinearLayout card) {
        into.addView(card);
        pad(into,13);
    }
    private void sectionTitle(LinearLayout into,String title) {
        pad(into,13);
        into.addView(text(title,12,SOFT,true));
        pad(into,9);
    }
    private void navRow(LinearLayout into,String label,String subtitle,String arrow,Runnable onTap) {
        LinearLayout row=horizontal();
        LinearLayout words=vertical();
        words.addView(text(label,15,INK,true));
        if(!subtitle.isEmpty()) {
            pad(words,4);
            words.addView(text(subtitle,12,SOFT,false));
        }
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(text(arrow,19,SOFT,false));
        row.setPadding(dp(1),dp(13),dp(1),dp(13));
        row.setOnClickListener(v->onTap.run());
        into.addView(row);
    }
    private void show() {
        LinearLayout root=vertical();
        root.setBackgroundColor(BG);

        LinearLayout header=horizontal();
        header.setPadding(dp(23),dp(16),dp(23),dp(13));
        TextView mark=text("✦",22,WHITE,true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(BLUE,12,0));
        header.addView(mark,new LinearLayout.LayoutParams(dp(39),dp(39)));
        LinearLayout labels=vertical();
        labels.setPadding(dp(11),0,0,0);
        labels.addView(text("KALLVO",18,INK,true));
        labels.addView(text("AI SECRETARY",10,SOFT,true));
        header.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        TextView tag=pill("PRIVATE • LOCAL",true);
        header.addView(tag);
        root.addView(header);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        frame=vertical();
        frame.setPadding(dp(20),dp(12),dp(20),dp(26));
        scroll.addView(frame);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout bottom=horizontal();
        bottom.setBackgroundColor(WHITE);
        bottom.setPadding(dp(13),dp(8),dp(13),dp(11));
        final String[] names={"Home","Talk","Settings"};
        final String[] symbols={"⌂","◎","⚙"};
        for(int i=0;i<3;i++) {
            final int selected=i;
            LinearLayout item=vertical();
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4),dp(7),dp(4),dp(7));
            item.setBackground(shape(tab==i?PALE:WHITE,13,0));
            item.addView(text(symbols[i],21,tab==i?BLUE:SOFT,true));
            TextView title=text(names[i],11,tab==i?BLUE:SOFT,tab==i);
            title.setGravity(Gravity.CENTER);
            item.addView(title);
            item.setOnClickListener(v->{
                stopTurn();
                tab=selected;
                detail="";
                genderFilter="";
                show();
            });
            bottom.addView(item,new LinearLayout.LayoutParams(0,-2,1f));
        }
        root.addView(bottom);
        setContentView(root);
        progressView=null;
        voiceHealthView=null;

        if(!detail.isEmpty()) {
            if("voice".equals(detail)) showVoices();
            else if("profile".equals(detail)) showProfile();
            else if("calls".equals(detail)) showCalls();
            else if("privacy".equals(detail)) showPrivacy();
            else if("models".equals(detail)) showModels();
        } else if(tab==0) showHome();
        else if(tab==1) showTalk();
        else showSettings();
    }

    private void showHome() {
        LinearLayout hero=vertical();
        hero.setBackground(shape(INK,23,0));
        hero.setPadding(dp(20),dp(22),dp(20),dp(23));
        hero.addView(text("YOUR PRIVATE SECRETARY",11,Color.rgb(174,194,255),true));
        pad(hero,9);
        hero.addView(text("More time for\nwhat matters.",28,WHITE,true));
        pad(hero,9);
        hero.addView(text("Practice a voice conversation, prepare your assistant, and review call notes.",
                13,Color.rgb(194,204,222),false));
        pad(hero,19);
        TextView start=press("Try a voice conversation   →",true,()->{
            tab=1;show();
        });
        start.setBackground(shape(BLUE,13,0));
        hero.addView(start);
        gapCard(frame,hero);

        LinearLayout status=card();
        status.addView(text("DEVICE STATUS",11,SOFT,true));
        pad(status,12);
        status.addView(text(LocalModel.get().isLoaded()
                ? "●  Offline AI loaded" : "○  Offline AI not loaded",
                15,LocalModel.get().isLoaded()?GREEN:INK,true));
        pad(status,8);
        status.addView(text(voice.ready() ? "●  Voice engine connected" : "○  Voice engine unavailable",
                14,voice.ready()?GREEN:SOFT,false));
        pad(status,12);
        status.addView(text("SIM call integration · Not verified on this phone",
                12,SOFT,false));
        gapCard(frame,status);

        sectionTitle(frame,"YOUR LAST CALL NOTE");
        LinearLayout brief=card();
        String summary=new PrivateBriefStore(this).read();
        brief.addView(text(summary,14,INK,false));
        pad(brief,12);
        brief.addView(press("Open call setup",false,()->{tab=2;detail="calls";show();}));
        gapCard(frame,brief);

        LinearLayout row=card();
        navRow(row,"Set your voice",voice.chosenVoiceLabel(),"›",()->{
            tab=2;detail="voice";show();
        });
        navRow(row,"Give your secretary context","Owner details and conversation rules","›",()->{
            tab=2;detail="profile";show();
        });
        gapCard(frame,row);
    }

    private void showTalk() {
        section(frame,"TALK TO KALLVO","A conversation, not a script.");
        pad(frame,7);
        caption(frame,"Voice and AI run on your phone. No call is placed.");
        pad(frame,16);

        if(!LocalModel.get().isLoaded()) {
            LinearLayout model=card();
            model.addView(text(LocalModel.isImported(this)?"AI model imported":"Set up your AI brain",
                    17,INK,true));
            pad(model,6);
            caption(model,LocalModel.isImported(this)
                    ? "Your GGUF is on this device. Load it to start."
                    : "Choose the Qwen GGUF already saved in Downloads.");
            pad(model,12);
            if(!LocalModel.isImported(this)) {
                model.addView(press("Choose GGUF file",false,this::chooseGGUF));
                pad(model,8);
            }
            model.addView(press("Load offline model",true,()->{
                callState="Loading offline model…";
                show();
                LocalModel.get().load(this,(ok,message)->runOnUiThread(()->{
                    callState=message;
                    if(!isFinishing()) show();
                }));
            }));
            gapCard(frame,model);
        } else {
            LinearLayout model=horizontal();
            model.setBackground(shape(Color.rgb(231,247,239),14,0));
            model.setPadding(dp(13),dp(12),dp(13),dp(12));
            model.addView(text("●  Model loaded · test reply speed",13,GREEN,true));
            frame.addView(model);
            pad(frame,15);
        }
        LinearLayout live=card();
        LinearLayout top=horizontal();
        LinearLayout words=vertical();
        words.addView(text("Voice session",18,INK,true));
        pad(words,3);
        words.addView(text(listeningLoop?"Hands-free active":"Tap and speak",12,SOFT,false));
        top.addView(words,new LinearLayout.LayoutParams(0,-2,1f));
        top.addView(pill(thinking?"THINKING":listeningLoop?"LISTENING":"PRIVATE",true));
        live.addView(top);
        pad(live,16);
        progressView=text(callState,13,SOFT,false);
        live.addView(progressView);
        pad(live,13);
        live.addView(press("🎙  Speak now",true,()->{listeningLoop=false;askMicrophone();}));
        pad(live,8);
        live.addView(press(listeningLoop?"Stop hands-free":"Hands-free conversation",
                false,()->{
                    if(listeningLoop) stopTurn();
                    else {listeningLoop=true;askMicrophone();}
                    show();
                }));
        if(thinking) {
            pad(live,8);
            live.addView(press("Stop generating   ■",false,()->{
                stopTurn();
                callState="Stopped";
                show();
            }));
        }
        gapCard(frame,live);

        LinearLayout exchange=card();
        exchange.addView(text("CONVERSATION",11,SOFT,true));
        pad(exchange,12);
        List<String> turns=chat.recentTurns();
        if(turns.isEmpty()) {
            exchange.addView(text("The conversation starts here.",14,SOFT,false));
        } else {
            for(String part:turns) {
                boolean assistant=part.startsWith("Assistant:");
                String display=part.replaceFirst("^(Caller|Assistant):\\s*","");
                LinearLayout bubble=vertical();
                bubble.setPadding(dp(12),dp(10),dp(12),dp(10));
                bubble.setBackground(shape(assistant?PALE:BG,13,0));
                bubble.addView(text(assistant?"KALLVO":"YOU",10,assistant?BLUE:SOFT,true));
                pad(bubble,4);
                bubble.addView(text(display,14,INK,false));
                exchange.addView(bubble);
                pad(exchange,8);
            }
        }
        pad(exchange,10);
        EditText typed=edit("Type a message…","",2);
        exchange.addView(typed);
        pad(exchange,10);
        exchange.addView(press("Send & hear reply",true,()->{
            String value=typed.getText().toString().trim();
            if(value.isEmpty()) {toast("Type a message first");return;}
            processTalk(value);
            show();
        }));
        pad(exchange,8);
        exchange.addView(press("Clear this conversation",false,()->{
            stopTurn();resetChat();show();
        }));
        gapCard(frame,exchange);
    }

    private void showSettings() {
        section(frame,"PERSONALIZE","Your secretary, your rules.");
        pad(frame,15);
        LinearLayout preferences=card();
        navRow(preferences,"AI model & speed",LocalModel.get().isLoaded()
                ? "Loaded: speed needs testing" : "Load or change your GGUF",
                "›",()->{detail="models";show();});
        navRow(preferences,"Voice studio",voice.chosenVoiceLabel(),"›",()->{detail="voice";show();});
        navRow(preferences,"Your secretary","Name, public facts and instructions","›",()->{
            detail="profile";show();
        });
        navRow(preferences,"Calls & Bixby","Experimental Samsung integration","›",()->{
            detail="calls";show();
        });
        navRow(preferences,"Privacy & data","Permissions, stored notes, reset","›",()->{
            detail="privacy";show();
        });
        gapCard(frame,preferences);

        LinearLayout support=card();
        support.addView(text("ENGINEERING",11,SOFT,true));
        pad(support,7);
        support.addView(text("Advanced call diagnostics",15,INK,true));
        pad(support,7);
        caption(support,"For compatibility tests only. Not needed for daily use.");
        pad(support,12);
        support.addView(press("Open technician panel   ↗",false,()->{
            stopTurn();
            startActivity(new Intent(this,MainActivity.class));
        }));
        gapCard(frame,support);
        caption(frame,"KALLVO is a working prototype, not yet a certified automated SIM-call agent.");
    }
    private void backTitle(String eyebrow,String title) {
        TextView back=text("‹  Settings",13,BLUE,true);
        back.setPadding(0,dp(3),0,dp(15));
        back.setOnClickListener(v->{detail="";show();});
        frame.addView(back);
        section(frame,eyebrow,title);
        pad(frame,14);
    }
    private void showVoices() {
        backTitle("VOICE STUDIO","Choose the sound.");
        LinearLayout engine=card();
        engine.addView(text("Speech engine",16,INK,true));
        pad(engine,5);
        caption(engine,"For natural voice, select an installed neural TTS engine. This voice is for in-app conversations; Samsung Bixby controls the voice heard on a SIM call.");
        pad(engine,13);
        TextView chosen=text(voice.activeEngine().isEmpty()
                ? "System speech engine" : voice.activeEngine(),12,SOFT,false);
        engine.addView(chosen);
        pad(engine,10);
        voiceHealthView=text(voice.lastStatus(),12,SOFT,false);
        engine.addView(voiceHealthView);
        pad(engine,6);
        int music=voice.musicVolume();
        engine.addView(text(music==0
                ? "Media volume is MUTED. Use Volume Up during preview."
                : music<0?"Media volume unavailable"
                   :"Media volume: "+music+" (turn up if needed)",12,music==0?Color.rgb(191,57,36):SOFT,false));
        pad(engine,10);
        engine.addView(press("Reconnect speech engine",false,()->{
            voice.useEngine(prefs.getString(Prefs.TTS_ENGINE,""),ok->runOnUiThread(()->{
                if(!isFinishing())show();
                if(!ok)toast("Engine unavailable; check Voice studio details.");
            }));
        }));
        pad(engine,8);
        engine.addView(press("Change installed engine",false,()->enginePicker()));
        pad(engine,10);
        engine.addView(press("Manage offline voice packs   ↗",false,this::speechSettings));
        gapCard(frame,engine);

        LinearLayout identity=card();
        identity.addView(text("VOICE CHARACTER",11,SOFT,true));
        pad(identity,11);
        caption(identity,"Male / female options require an actual named speaker pack. Pitch presets do not change gender.");
        pad(identity,12);
        LinearLayout select=horizontal();
        for(String kind:new String[]{"All","Female","Male"}) {
            TextView button=pill(kind,genderFilter.equals(kind)||genderFilter.isEmpty()&&"All".equals(kind));
            button.setOnClickListener(v->{genderFilter=kind;show();});
            select.addView(button,new LinearLayout.LayoutParams(0,-2,1f));
        }
        identity.addView(select);
        pad(identity,11);
        List<Voice> voices=voice.voices();
        int visible=0;
        for(Voice v:voices) {
            String gender=LocalVoiceEngine.gender(v);
            if(!genderFilter.isEmpty()&&!"All".equals(genderFilter)
                    &&!genderFilter.equals(gender)) continue;
            visible++;
            boolean active=v.getName().equals(prefs.getString(Prefs.VOICE,""));
            final Voice selected=v;
            TextView pick=text((active?"●  ":"○  ")+LocalVoiceEngine.displayVoice(v),13,
                    active?BLUE:INK,active);
            pick.setPadding(dp(10),dp(11),dp(8),dp(11));
            pick.setBackground(shape(active?PALE:WHITE,12,active?0:LINE));
            pick.setOnClickListener(view->{
                prefs.edit().putString(Prefs.VOICE,selected.getName())
                        .putString(Prefs.LANGUAGE,selected.getLocale().toLanguageTag()).apply();
                show();
                if(!voice.speak(shortSample(selected.getLocale())))
                    toast(voice.lastStatus());
            });
            identity.addView(pick);
            pad(identity,7);
        }
        if(visible==0) {
            caption(identity,"No matching offline speaker installed. Pick an offline neural voice pack from your speech engine.");
        }
        pad(identity,8);
        identity.addView(press("▶  Test selected voice (no AI)",true,()->{
            boolean started=voice.speak(shortSample(Locale.forLanguageTag(
                    prefs.getString(Prefs.LANGUAGE,"hi-IN"))));
            if(!started)toast(voice.lastStatus());
        }));
        pad(identity,12);
        TextView pace=text("Speaking speed · "+prefs.getInt(Prefs.SPEED,100)+"%",12,SOFT,true);
        identity.addView(pace);
        SeekBar seek=new SeekBar(this);
        seek.setMax(40);
        seek.setProgress(Math.max(0,Math.min(40,prefs.getInt(Prefs.SPEED,100)-80)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int value,boolean user) {
                if(user) prefs.edit().putInt(Prefs.SPEED,value+80).apply();
                pace.setText("Speaking speed · "+(value+80)+"%");
            }
            public void onStartTrackingTouch(SeekBar b) { }
            public void onStopTrackingTouch(SeekBar b) { }
        });
        identity.addView(seek);
        gapCard(frame,identity);

        LinearLayout info=card();
        info.addView(text("Need a more human voice?",16,INK,true));
        pad(info,8);
        caption(info,"Piper & Kokoro are neural speech engines. Third-party app installation and voice downloads are optional; KALLVO never bundles another app's GPL code.");
        pad(info,12);
        info.addView(press("Open VoxSherpa and test voice directly   ↗",false,()->{
            Intent launch=getPackageManager().getLaunchIntentForPackage(TTS_APP);
            if(launch!=null)startActivity(launch);
            else toast("Open VoxSherpa TTS from app drawer → Generate → test Hindi F2.");
        }));
        pad(info,8);
        info.addView(press("Explore offline neural engine   ↗",false,()->{
            try {
                Intent open=new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id="+TTS_APP));
                startActivity(open);
            } catch(Exception error) {
                toast("Search 'VoxSherpa TTS' on Google Play");
            }
        }));
        gapCard(frame,info);
    }
    private String shortSample(Locale locale) {
        return "hi".equals(locale.getLanguage())
                ? "नमस्ते, आपका स्वागत है।"
                : "Hello! Welcome.";
    }
    private String sample(Locale locale) {
        if("hi".equals(locale.getLanguage()))
            return "नमस्ते! मैं आपका एआई सेक्रेटरी हूँ। बताइए, मैं आपकी क्या मदद कर सकता हूँ?";
        return "Hello! I'm your AI secretary. How can I help you today?";
    }
    private void enginePicker() {
        List<TextToSpeech.EngineInfo> options=voice.engines();
        if(options.isEmpty()){speechSettings();return;}
        CharSequence[] titles=new CharSequence[options.size()+1];
        titles[0]="Use system default";
        for(int i=0;i<options.size();i++) titles[i+1]=options.get(i).label;
        new AlertDialog.Builder(this).setTitle("Select speech engine")
            .setItems(titles,(dialog,which)->{
                String packageName=which==0?"":options.get(which-1).name;
                prefs.edit().putString(Prefs.TTS_ENGINE,packageName)
                        .remove(Prefs.VOICE).apply();
                voice.useEngine(packageName,ready->runOnUiThread(()->{
                    detail="voice";
                    show();
                    if(!ready)toast("Engine not ready. Install a voice pack in Android settings.");
                }));
            }).setNegativeButton("Cancel",null).show();
    }

    private void showModels() {
        backTitle("OFFLINE AI","Fast model controls.");
        LinearLayout options=card();
        options.addView(text(LocalModel.get().isLoaded() ? "Model is loaded"
                    : LocalModel.isImported(this) ? "Model imported" : "No GGUF imported",17,INK,true));
        pad(options,8);
        caption(options,"Your current Qwen3.5 0.8B is very slow on A52s. Try a smaller Qwen3 0.6B GGUF, then run the benchmark. Model is never downloaded by the app.");
        pad(options,12);
        options.addView(press("Replace model from Downloads",true,()->new AlertDialog.Builder(this)
            .setTitle("Replace local AI model?")
            .setMessage("The current model will be unloaded. Your original downloaded GGUF file stays untouched.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Choose", (d,w)->{stopTurn();chooseGGUF();}).show()));
        pad(options,9);
        options.addView(press("Unload model from RAM",false,()->
            LocalModel.get().unload((ok,msg)->runOnUiThread(()->{callState=msg;show();}))));
        gapCard(frame,options);
        LinearLayout measure=card();
        measure.addView(text("REAL DEVICE SPEED TEST",11,BLUE,true));
        pad(measure,7);
        caption(measure,"Tests genuine offline inference without instant intent shortcuts.");
        pad(measure,10);
        TextView reading=text(prefs.getString("model_benchmark_status","Not benchmarked yet"),13,INK,false);
        measure.addView(reading);
        pad(measure,11);
        measure.addView(press("Benchmark one short AI reply",true,()->{
            if(!LocalModel.get().isLoaded()){toast("Load the GGUF in Talk first");return;}
            if(thinking)return;
            thinking=true;
            long id=++generationId;
            reading.setText("Testing CPU model with 12-second limit…");
            LocalModel.get().reply("Owner","","Be brief",new ArrayList<>(),
                "Hello, how are you?",(reply,error,millis)->runOnUiThread(()->{
                if(id!=generationId)return;
                thinking=false;
                String result=reply.isEmpty()?"Model too slow: "+error
                        :"Generated in "+(millis/1000f)+"s: "+reply;
                prefs.edit().putString("model_benchmark_status",result).apply();
                callState=result;
                reading.setText(result);
            }));
            ui.postDelayed(()->{
                if(thinking && id==generationId)
                    reading.setText("CPU status: "+LocalModel.get().progress());
            },3000);
        }));
        pad(measure,12);
        caption(measure,"Under 5s: desired. 5–12s: slow. Over 12s: timeout. Measured speed depends on your phone.");
        gapCard(frame,measure);
    }

    private void showProfile() {
        backTitle("YOUR SECRETARY","What should it know?");
        LinearLayout card=card();
        caption(card,"Only include details you would share with any caller. Never store private passwords or financial data.");
        pad(card,12);
        EditText owner=edit("Owner name",prefs.getString(Prefs.PROFILE_NAME,"Owner"),1);
        card.addView(owner); pad(card,10);
        EditText publicInfo=edit("Public details: hours, work, services",
                prefs.getString(Prefs.PROFILE_INFO,""),4);
        card.addView(publicInfo); pad(card,10);
        EditText instructions=edit("How should your assistant respond?",
                prefs.getString(Prefs.PROFILE_RULES,""),4);
        card.addView(instructions); pad(card,14);
        card.addView(press("Save secretary profile",true,()->{
            prefs.edit().putString(Prefs.PROFILE_NAME,owner.getText().toString().trim())
                    .putString(Prefs.PROFILE_INFO,publicInfo.getText().toString().trim())
                    .putString(Prefs.PROFILE_RULES,instructions.getText().toString().trim())
                    .apply();
            resetChat();
            toast("Profile saved on device");
            detail="";
            show();
        }));
        gapCard(frame,card);
    }
    private void showCalls() {
        backTitle("PHONE INTEGRATION","Call handling.");
        LinearLayout status=card();
        status.addView(text("Samsung Bixby Text Call",16,INK,true));
        pad(status,7);
        caption(status,"Experimental Samsung UI bridge. A52s 5G caller-audio delivery has not passed a real-call test. A remote caller hears Bixby, not your selected KALLVO voice.");
        pad(status,10);
        status.addView(press("Open Android Accessibility",false,()->startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        gapCard(frame,status);

        LinearLayout toggles=card();
        settingToggle(toggles,"Observe Samsung call screen",Prefs.ENABLED,
                "Allow test-only call UI diagnostics.");
        settingToggle(toggles,"Show AI Attend shortcut",Prefs.FLOATING,
                "Try Bixby Text Call during an incoming call.");
        settingToggle(toggles,"Auto-attend test calls",Prefs.AUTO_ATTEND,
                "May answer incoming calls. Unverified.");
        settingToggle(toggles,"Auto-send replies",Prefs.LIVE_REPLY,
                "Only when incoming speaker and Send control are unambiguous.");
        settingToggle(toggles,"Use offline Qwen for calls",Prefs.USE_LLM,
                "May add significant latency on this device.");
        settingToggle(toggles,"Save encrypted call brief",Prefs.SAVE_BRIEF,
                "Optional sensitive AI-derived summary on device.");
        gapCard(frame,toggles);

        LinearLayout logs=card();
        logs.addView(text("LAST DIAGNOSTIC",11,SOFT,true));
        pad(logs,7);
        logs.addView(text(prefs.getString(Prefs.STATUS,"No Samsung call test yet."),13,INK,false));
        pad(logs,8);
        logs.addView(press("View detailed diagnostic",false,()->new AlertDialog.Builder(this)
                .setTitle("Samsung UI diagnostic")
                .setMessage(prefs.getString(Prefs.DIAGNOSTICS,"No test data."))
                .setPositiveButton("Close",null).show()));
        gapCard(frame,logs);
    }
    private void settingToggle(LinearLayout holder,String title,String key,String desc) {
        LinearLayout row=horizontal();
        LinearLayout labels=vertical();
        labels.addView(text(title,14,INK,true));
        pad(labels,4);
        labels.addView(text(desc,12,SOFT,false));
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);
        toggle.setChecked(prefs.getBoolean(key,false));
        row.addView(toggle);
        toggle.setOnCheckedChangeListener((button,on)->{
            if(!on){
                prefs.edit().putBoolean(key,false).apply();
                return;
            }
            new AlertDialog.Builder(this).setTitle("Experimental feature")
                    .setMessage("This can read or act on a sensitive incoming call screen. Test with a consenting friend only. Samsung A52s support is not confirmed.")
                    .setNegativeButton("Cancel",(d,w)->toggle.setChecked(false))
                    .setPositiveButton("Enable",(d,w)->prefs.edit().putBoolean(key,true).apply())
                    .setOnCancelListener(d->toggle.setChecked(false)).show();
        });
        row.setPadding(0,dp(14),0,dp(14));
        holder.addView(row);
    }
    private void showPrivacy() {
        backTitle("PRIVACY","Your data stays yours.");
        LinearLayout notes=card();
        notes.addView(text("Local inference",16,INK,true));
        pad(notes,6);
        caption(notes,"This app has no Internet, contacts, SMS, or call-log permission. Your speech engine and Samsung Bixby may have separate privacy practices.");
        pad(notes,13);
        notes.addView(text("Last encrypted call note",15,INK,true));
        pad(notes,6);
        notes.addView(text(new PrivateBriefStore(this).read(),13,SOFT,false));
        pad(notes,12);
        notes.addView(press("Erase call note",false,()->{
            new PrivateBriefStore(this).clear();
            toast("Stored brief erased");
            show();
        }));
        pad(notes,8);
        notes.addView(press("Clear local diagnostics",false,()->{
            prefs.edit().remove(Prefs.DIAGNOSTICS).remove(Prefs.STATUS).apply();
            toast("Diagnostics erased");
        }));
        gapCard(frame,notes);

        LinearLayout reset=card();
        reset.addView(text("Reset preferences",16,INK,true));
        pad(reset,9);
        reset.addView(press("Reset KALLVO settings",false,()->new AlertDialog.Builder(this)
                .setTitle("Reset local settings?")
                .setMessage("Delete owner profile, voice selection, call permissions toggles and summaries? Imported GGUF remains until Android app data is deleted.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Reset",(d,w)->{
                    stopTurn();
                    new PrivateBriefStore(this).clear();
                    prefs.edit().clear().commit();
                    prefs=Prefs.get(this);
                    resetChat();
                    detail="";
                    voice.useEngine("",ok->runOnUiThread(this::show));
                    show();
                }).show()));
        gapCard(frame,reset);
    }

    private void chooseGGUF() {
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(picker,FILE_REQUEST);
    }
    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req==FILE_REQUEST && result==RESULT_OK && data!=null && data.getData()!=null){
            callState="Importing selected model…";
            LocalModel.get().importUri(this,data.getData(),(ok,message)->
                    runOnUiThread(()->{
                        callState=message;
                        if(foreground)show();
                        if(!ok)toast(message);
                    }));
            show();
        }
    }
    private void askMicrophone() {
        if(!speech.isSupported()){
            callState="Offline speech recognition unavailable; type a message instead.";
            show();
            listeningLoop=false;
            return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST);
            return;
        }
        beginListening();
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants) {
        super.onRequestPermissionsResult(code,names,grants);
        if(code==MIC_REQUEST){
            if(grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED && foreground)beginListening();
            else {listeningLoop=false;callState="Microphone permission not granted";show();}
        }
    }
    private void beginListening() {
        if(!foreground||tab!=1||!detail.isEmpty()||thinking)return;
        voice.stop();
        speech.listen(prefs.getString(Prefs.SPEECH_LANGUAGE,"hi-IN"),
                new LocalSpeechInput.Callback(){
            public void onUpdate(String state){status(state);}
            public void onPartial(String value){status("Listening: "+value);}
            public void onFinal(String value){processTalk(value);show();}
            public void onError(String value){listeningLoop=false;status(value);}
        });
    }
    private void processTalk(String phrase) {
        if(thinking){status("Finish or stop the current answer first");return;}
        speech.stop();
        String quick=FastReply.respond(phrase,
                prefs.getString(Prefs.PROFILE_NAME,"Owner"),chat.recentTurns());
        if(!quick.isEmpty()) {
            chat.recordExchange(phrase,quick);
            status("Instant routine reply · offline rules, no model wait");
            show();
            speak(quick);
            return;
        }
        if(!LocalModel.get().isLoaded()){
            String answer=chat.respond(phrase);
            status("Using short scripted fallback. Load GGUF for natural AI replies.");
            show();
            speak(answer);
            return;
        }
        thinking=true;
        began=SystemClock.elapsedRealtime();
        long current=++generationId;
        status("Qwen starting…");
        watch(current);
        LocalModel.get().reply(prefs.getString(Prefs.PROFILE_NAME,"Owner"),
                prefs.getString(Prefs.PROFILE_INFO,""),
                prefs.getString(Prefs.PROFILE_RULES,""),
                chat.recentTurns(),phrase,(reply,error,millis)->runOnUiThread(()->{
                    if(current!=generationId||!foreground)return;
                    thinking=false;
                    if(reply.isEmpty()){
                        listeningLoop=false;
                        status("Offline AI too slow: "+error+
                                ". Try smaller GGUF via Settings → AI model & speed.");
                        prefs.edit().putString("model_benchmark_status","Timed out: "+error).apply();
                        show();
                        return;
                    }
                    chat.recordExchange(phrase,reply);
                    status("AI reply ready · "+(millis/1000f)+"s");
                    show();
                    speak(reply);
                }));
    }
    private void speak(String answer) {
        if(!voice.speak(answer,()->{
            status("Voice completed. Ready for the next question.");
            if(listeningLoop && foreground && tab==1)beginListening();
        },()->{
            listeningLoop=false;
            status("Voice failed: "+voice.lastStatus()+
                    ". Open Settings → Voice studio, then test voice without AI.");
        })) {
            listeningLoop=false;
            status("Voice unavailable: "+voice.lastStatus());
        }
    }
    private void watch(long id) {
        ui.postDelayed(()->{
            if(id!=generationId||!thinking||!foreground)return;
            long elapsed=(SystemClock.elapsedRealtime()-began)/1000;
            status("Qwen · "+elapsed+"s · "+LocalModel.get().progress());
            watch(id);
        },1000);
    }
    private void status(String s) {
        callState=s;
        if(progressView!=null)progressView.setText(s);
    }
    private void stopTurn() {
        ++generationId;
        if(thinking)LocalModel.get().cancel();
        thinking=false;
        listeningLoop=false;
        if(speech!=null)speech.stop();
        if(voice!=null)voice.stop();
    }
    private void speechSettings() {
        try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}
        catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
    }
    private void toast(String s) {Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
