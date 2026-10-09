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
    private int BG = Color.rgb(247,248,252);
    private int INK = Color.rgb(24,32,49);
    private int SOFT = Color.rgb(105,116,136);
    private int LINE = Color.rgb(230,233,240);
    private int BLUE = Color.rgb(60,91,219);
    private int PALE = Color.rgb(238,242,254);
    private int GREEN = Color.rgb(21,128,100);
    private int WHITE = Color.WHITE;
    private boolean dark;
    private void applyTheme() {
        dark=prefs!=null && prefs.getBoolean("kallvo_dark",false);
        BG=dark?Color.rgb(14,19,30):Color.rgb(247,248,252);
        INK=dark?Color.rgb(237,242,252):Color.rgb(24,32,49);
        SOFT=dark?Color.rgb(162,173,193):Color.rgb(105,116,136);
        LINE=dark?Color.rgb(51,61,81):Color.rgb(230,233,240);
        BLUE=dark?Color.rgb(124,153,255):Color.rgb(60,91,219);
        PALE=dark?Color.rgb(37,49,78):Color.rgb(238,242,254);
        GREEN=dark?Color.rgb(99,217,171):Color.rgb(21,128,100);
        WHITE=dark?Color.rgb(26,34,49):Color.WHITE;
    }
    private static final int MIC_REQUEST = 6401;
    private static final int FILE_REQUEST = 6402;
    private static final String TTS_APP = "com.CodeBySonu.VoxSherpa";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
     private NeuralVoice neural;
    private LocalSpeechInput speech;
    private ConversationEngine chat;
    private int tab=1;
    private android.window.OnBackInvokedCallback systemBack;
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
        SharedPreferences appearance=Prefs.get(this);
        setTheme(appearance.getBoolean("kallvo_dark",false)
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        tab=state==null ? getIntent().getIntExtra("restore_tab",1) : state.getInt("tab",1);
        if(tab!=2) tab=1;
        detail=state==null?"":state.getString("detail","");
        if(tab!=2)detail="";
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        prefs=Prefs.get(this);
        applyTheme();
        neural=new NeuralVoice(this);
        neural.listen(status -> runOnUiThread(() -> {
            if (voiceHealthView!=null && "voice".equals(detail)) voiceHealthView.setText(status);
            if (progressView!=null && tab==1 && !thinking)progressView.setText(status);
        }));
        speech=new LocalSpeechInput(this);
         resetChat();
         show();
        if(android.os.Build.VERSION.SDK_INT>=33){
            systemBack=this::navigateBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,systemBack);
        }
        if(LocalModel.isImported(this)) {
            callState="Restoring saved offline AI…";
            LocalModel.get().loadIfPresent(this,(ok,message)->runOnUiThread(()->{
                callState=message;
                if(!isFinishing() && !isDestroyed())show();
            }));
        }
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putInt("tab",tab);
        out.putString("detail",detail);
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed(){ navigateBack(); }
    private void navigateBack(){
        if(!detail.isEmpty()){
            stopTurn(); detail=""; tab=2; show();
        }else if(tab==2){
            stopTurn(); tab=1; show();
        }else{
            moveTaskToBack(true);
        }
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
        if(android.os.Build.VERSION.SDK_INT>=33 && systemBack!=null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBack);
        stopTurn();
         neural.shutdown();
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
        applyTheme();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=vertical();
        root.setBackgroundColor(BG);

        LinearLayout header=horizontal();
        header.setPadding(dp(23),dp(16),dp(23),dp(13));
        TextView mark=text("✦",22,Color.WHITE,true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(BLUE,12,0));
        header.addView(mark,new LinearLayout.LayoutParams(dp(39),dp(39)));
        LinearLayout labels=vertical();
        labels.setPadding(dp(11),0,0,0);
        labels.addView(text("KALLVO",18,INK,true));
        labels.addView(text("AI SECRETARY",10,SOFT,true));
        header.addView(labels,new LinearLayout.LayoutParams(0,-2,1f));
        TextView tag=pill("ON-DEVICE",true);
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
        final String[] names={"Assistant","Settings"};
        final String[] symbols={"◉","⚙"};
        for(int i=0;i<names.length;i++) {
            final int selected=i+1;
            LinearLayout item=vertical();
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(5),dp(7),dp(5),dp(7));
            item.setBackground(shape(tab==selected?PALE:WHITE,13,0));
            TextView ico=text(symbols[i],21,tab==selected?BLUE:SOFT,true);
            ico.setGravity(Gravity.CENTER);
            item.addView(ico,new LinearLayout.LayoutParams(-1,dp(28)));
            TextView title=text(names[i],12,tab==selected?BLUE:SOFT,tab==selected);
            title.setGravity(Gravity.CENTER);
            item.addView(title,new LinearLayout.LayoutParams(-1,dp(24)));
            item.setOnClickListener(v->{
                if(tab==selected && detail.isEmpty())return;
                stopTurn();
                tab=selected;
                detail="";
                show();
            });
            bottom.addView(item,new LinearLayout.LayoutParams(0,dp(64),1f));
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
        } else if(tab==1) showTalk();
        else showSettings();
    }

    private void showTalk() {
        section(frame,"TALK TO KALLVO","A conversation, not a script.");
        pad(frame,7);
        caption(frame,"Hindi + Hinglish first. Voice understanding requires a local Hindi speech pack. No SIM call is placed.");
        pad(frame,16);
        showActionInbox();

        if(!LocalModel.get().isLoaded()) {
            LinearLayout model=card();
            model.addView(text(LocalModel.isImported(this)?"AI model imported":"Set up your AI brain",
                    17,INK,true));
            pad(model,6);
            caption(model,LocalModel.isImported(this)
                    ? "Saved on this device. KALLVO reloads it automatically when opened."
                    : "Choose the Qwen GGUF already saved in Downloads.");
            pad(model,12);
            if(!LocalModel.isImported(this)) {
                TextView aiProgress=text("Choose a downloaded GGUF, or install the recommended model in-app.",12,SOFT,false);
                model.addView(aiProgress);
                pad(model,8);
                model.addView(press("Install Hindi AI · ~484 MB",true,()->offerAIDownload(aiProgress)));
                pad(model,8);
                model.addView(press("Use existing GGUF from Downloads",false,this::chooseGGUF));
                pad(model,8);
            }
            if(LocalModel.get().isLoading()) {
                caption(model,"Loading saved AI into memory…");
            }else if(LocalModel.isImported(this)) {
                model.addView(press("Retry loading saved AI",true,()->{
                    callState="Loading saved offline model…"; show();
                    LocalModel.get().load(this,(ok,message)->runOnUiThread(()->{
                        callState=message;
                        if(!isFinishing()&&!isDestroyed()) show();
                    }));
                }));
            }
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
        String lastNote=new PrivateBriefStore(this).read();
        if(!"No call brief saved yet.".equals(lastNote)){
            LinearLayout note=card();
            note.addView(text("LAST CALL FOLLOW-UP",11,BLUE,true));
            pad(note,8);
            note.addView(text(lastNote,13,INK,false));
            String suggested="";
            for(String line:lastNote.split("\\n")){
                if(line.startsWith("Next action: ")) {
                    suggested=line.substring("Next action: ".length()).trim();
                    break;
                }
            }
            if(!suggested.isEmpty() && !"None".equalsIgnoreCase(suggested)){
                String task=suggested;
                pad(note,11);
                note.addView(press("Confirm and add as follow-up",false,()->{
                    try{
                        new SecretaryTaskStore(this).add(task,false);
                        toast("Follow-up saved");
                        show();
                    }catch(Exception error){toast("Task not added: "+error.getMessage());}
                }));
            }
            gapCard(frame,note);
        }
    }

    /** Owner-controlled follow-ups. Nothing is auto-committed from an AI call. */
    private void showActionInbox() {
        LinearLayout box=card();
        box.addView(text("YOUR FOLLOW-UPS",11,BLUE,true));
        pad(box,7);
        SecretaryTaskStore store=new SecretaryTaskStore(this);
        try {
            List<SecretaryTaskStore.Task> items=store.list();
            int important=0;
            for(SecretaryTaskStore.Task item:items)if(item.important)important++;
            box.addView(text(items.isEmpty()?"No outstanding tasks"
                    : items.size()+" to do"+(important>0?" · "+important+" important":""),
                    17,INK,true));
            pad(box,8);
            if(items.isEmpty()) {
                caption(box,"Add a task or confirm a follow-up from a call note. It stays private on this device.");
            }
            for(SecretaryTaskStore.Task item:items) {
                LinearLayout row=horizontal();
                LinearLayout label=vertical();
                label.addView(text((item.important?"IMPORTANT · ":"")+item.title,
                        13,item.important?INK:SOFT,item.important));
                row.addView(label,new LinearLayout.LayoutParams(0,-2,1f));
                TextView done=pill("✓ Done",false);
                done.setClickable(true);
                done.setOnClickListener(v->{
                    try {
                        store.finish(item.id);
                        show();
                    }catch(Exception error){toast("Task could not be saved");}
                });
                row.addView(done);
                row.setPadding(0,dp(8),0,dp(8));
                box.addView(row);
            }
            pad(box,10);
            EditText newTask=edit("Add a follow-up or reminder…","",1);
            box.addView(newTask);
            pad(box,8);
            Switch importantToggle=new Switch(this);
            importantToggle.setText("Important");
            importantToggle.setTextSize(13);
            importantToggle.setTextColor(INK);
            box.addView(importantToggle);
            pad(box,8);
            box.addView(press("Save follow-up",true,()->{
                String label=newTask.getText().toString().trim();
                if(label.isEmpty()){toast("Enter a task first");return;}
                try {
                    store.add(label,importantToggle.isChecked());
                    show();
                }catch(Exception error){toast(error.getMessage()==null
                        ? "Could not save encrypted task":error.getMessage());}
            }));
        }catch(Exception error){
            caption(box,"Encrypted follow-ups could not be opened. Existing data was not overwritten.");
        }
        gapCard(frame,box);
    }

    private void showSettings() {
        section(frame,"PERSONALIZE","Your secretary, your rules.");
        pad(frame,15);
        LinearLayout preferences=card();
        navRow(preferences,"AI model & speed",LocalModel.get().isLoaded()
                ? "Offline AI ready" : LocalModel.isImported(this) ? "AI saved on phone" : "One-time setup needed",
                "›",()->{detail="models";show();});
        navRow(preferences,"Hindi neural voice",neural.isInstalled()
                ? NeuralVoice.speakerLabel(neural.speaker())+" · installed" : "Set up in KALLVO",
                "›",()->{detail="voice";show();});
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
        Switch theme=new Switch(this);
        theme.setText("Dark mode");
        theme.setTextColor(INK);
        theme.setTextSize(15);
        theme.setPadding(dp(12),dp(12),dp(12),dp(12));
        theme.setChecked(dark);
        theme.setOnCheckedChangeListener((v,on)->{
            prefs.edit().putBoolean("kallvo_dark",on).apply();
            getIntent().putExtra("restore_tab",2);
            recreate();
        });
        LinearLayout appearance=card();
        appearance.addView(theme);
        gapCard(frame,appearance);
        caption(frame,"On-device AI is experimental. Cellular call automation is not yet certified.");
    }
    private void backTitle(String eyebrow,String title) {
        TextView back=text("‹  Settings",13,BLUE,true);
        back.setPadding(0,dp(3),0,dp(15));
        back.setOnClickListener(v->{stopTurn();detail="";tab=2;show();});
        frame.addView(back);
        section(frame,eyebrow,title);
        pad(frame,14);
    }
    private void showVoices() {
        backTitle("NATURAL HINDI VOICE","One voice. One app.");
        LinearLayout intro=card();
        intro.addView(text("Built-in neural Hindi",18,INK,true));
        pad(intro,7);
        caption(intro,"10 real speaker styles, five female and five male. No separate TTS application or system voice settings.");
        pad(intro,14);
        voiceHealthView=text(neural.status(),13,SOFT,false);
        intro.addView(voiceHealthView);
        pad(intro,12);
        if(!neural.isInstalled()) {
            intro.addView(press("Install voice pack · ~125 MB",true,()->{
                new AlertDialog.Builder(this)
                    .setTitle("One-time neural voice setup")
                    .setMessage("KALLVO downloads an open-weight Hindi Supertonic 3 model from GitHub Releases, verifies SHA-256 and stores it only in app-private storage. No caller speech or AI messages are sent to any server. Data charges may apply under your mobile plan.")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Download", (d,w)->{
                        neural.install((ok,msg)->runOnUiThread(()->{
                            if("voice".equals(detail))show();
                            toast(msg);
                        }));
                        if(voiceHealthView!=null)voiceHealthView.setText("Preparing download…");
                    }).show();
            }));
        }else{
            intro.addView(text("✓ Voice pack installed • ready for offline playback",13,GREEN,true));
        }
        gapCard(frame,intro);

        LinearLayout chooser=card();
        chooser.addView(text("YOUR VOICE",11,BLUE,true));
        pad(chooser,7);
        TextView selected=text(NeuralVoice.speakerLabel(neural.speaker()),21,INK,true);
        chooser.addView(selected);
        pad(chooser,12);
        String[] pair={"Female","Male"};
        LinearLayout row=horizontal();
        for(int i=0;i<2;i++){
            final int startId=i==0?0:5;
            TextView choice=press(pair[i]+"  "+(neural.speaker()<5?"":"").trim(),
                    neural.speaker()/5==i,()->{
                        neural.setSpeaker(startId);
                        show();
                    });
            row.addView(choice,new LinearLayout.LayoutParams(0,dp(49),1f));
            if(i==0)row.addView(new View(this),new LinearLayout.LayoutParams(dp(8),1));
        }
        chooser.addView(row);
        pad(chooser,11);
        chooser.addView(press("Choose speaker style  (1–5)",false,()->{
            String[] labels=new String[5];
            boolean female=neural.speaker()<5;
            for(int i=0;i<5;i++)labels[i]=(female?"Female ":"Male ")+(i+1);
            new AlertDialog.Builder(this).setTitle("Pick a voice")
                .setSingleChoiceItems(labels,neural.speaker()%5,(dialog,which)->{
                    neural.setSpeaker((female?0:5)+which);
                    dialog.dismiss();
                    show();
                }).setNegativeButton("Cancel",null).show();
        }));
        pad(chooser,11);
        chooser.addView(press("▶  Hear sample in Hindi",true,()->{
            neural.speak("नमस्ते, मैं आपका एआई सेक्रेटरी हूँ। बताइए, मैं आपकी क्या मदद कर सकता हूँ?",
                (ok,msg)->runOnUiThread(()->{
                    if(voiceHealthView!=null)voiceHealthView.setText(msg);
                    if(!ok)toast(msg);
                }));
        }));
        gapCard(frame,chooser);

        LinearLayout speechCard=card();
        speechCard.addView(text("HINDI UNDERSTANDING",11,BLUE,true));
        pad(speechCard,7);
        caption(speechCard,"Speech recognition and voice synthesis are separate. Check if your phone has an actual on-device Hindi recognizer.");
        pad(speechCard,12);
        speechCard.addView(press("Check Hindi speech recognition",false,()->{
            speech.checkHindi((msg,ok)->runOnUiThread(()->{
                new AlertDialog.Builder(this)
                    .setTitle(ok?"Hindi offline recognition installed":"Hindi recognition needs attention")
                    .setMessage(msg).setPositiveButton("OK",null).show();
            }));
        }));
        gapCard(frame,speechCard);
        caption(frame,"The phone's Samsung Bixby voice is separate for protected SIM calls. In-app Hindi neural speech uses only KALLVO.");
    }
    private String shortSample(Locale locale) {
        return "hi".equals(locale.getLanguage())?"नमस्ते, आपका स्वागत है।":"Hello and welcome.";
    }

    private void offerAIDownload(TextView target) {
        new AlertDialog.Builder(this).setTitle("Install Hindi AI inside KALLVO?")
            .setMessage("One-time download ~484 MB from Hugging Face. File is verified with SHA-256, then runs offline. Mobile data may be charged by your carrier. Qwen3 0.6B is smaller, not guaranteed faster or more accurate than the installed model.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Install",(d,w)->{
                if(target!=null)target.setText("Downloading AI…");
                LocalModel.get().downloadRecommended(this,(ok,msg)->runOnUiThread(()->{
                    callState=msg;
                    if(target!=null)target.setText(msg);
                    if(ok) {
                        callState="AI downloaded once. Loading from phone…";
                        LocalModel.get().load(this,(loaded,info)->runOnUiThread(()->{
                            callState=info;
                            if(foreground)show();
                            if(!loaded)toast(info);
                        }));
                    } else {
                        toast(msg);
                        if(foreground)show();
                    }
                }));
                final Runnable[] refresh=new Runnable[1];
                refresh[0]=()->{
                    if(LocalModel.get().isDownloading()&&foreground){
                        if(target!=null)target.setText(LocalModel.get().status());
                        ui.postDelayed(refresh[0],1000);
                    }
                };
                ui.postDelayed(refresh[0],1000);
            }).show();
    }

    private void showModels() {
        backTitle("OFFLINE AI","Fast model controls.");
        LinearLayout options=card();
        options.addView(text(LocalModel.get().isLoaded() ? "Model is loaded"
                    : LocalModel.isImported(this) ? "Model imported" : "No GGUF imported",17,INK,true));
        pad(options,8);
        caption(options,"AI downloads or imports once. The saved model reloads automatically when you reopen KALLVO. Only replace it if needed.");
        pad(options,12);
        TextView aiProgress=text(LocalModel.get().status(),12,SOFT,false);
        options.addView(aiProgress);
        pad(options,10);
        if(!LocalModel.isImported(this)) {
            options.addView(press("Install recommended Hindi AI · ~484 MB",true,
                    ()->offerAIDownload(aiProgress)));
            pad(options,9);
        } else {
            caption(options,"Saved locally • no repeat download required.");
            pad(options,9);
        }
        options.addView(press("Choose existing GGUF file",false,()->new AlertDialog.Builder(this)
            .setTitle("Replace local AI model?")
            .setMessage("The current model will be unloaded. Your original downloaded GGUF file stays untouched.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Choose", (d,w)->{stopTurn();chooseGGUF();}).show()));

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
        caption(notes,"Internet permission is used for owner-requested, checksum-verified AI and voice model downloads. Inference and Hindi speech playback run locally. The app does not request contacts, SMS or call-log access. Samsung Bixby has separate terms.");
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
        pad(notes,8);
        notes.addView(press("Erase all follow-up tasks",false,()->new AlertDialog.Builder(this)
                .setTitle("Permanently erase all tasks?")
                .setMessage("Your encrypted follow-ups will be deleted from this phone. This cannot be undone.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Erase tasks",(d,w)->{
                    if(new SecretaryTaskStore(this).eraseAll()){
                        toast("Tasks erased");show();
                    }else toast("Task deletion failed");
                }).show()));
        gapCard(frame,notes);

        LinearLayout reset=card();
        reset.addView(text("Reset preferences",16,INK,true));
        pad(reset,9);
        reset.addView(press("Reset KALLVO settings",false,()->new AlertDialog.Builder(this)
                .setTitle("Reset local settings?")
                .setMessage("Delete profile, selected voice, call toggles, call notes and all follow-up tasks? Downloaded AI and voice packs are preserved. This cannot be undone.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Reset",(d,w)->{
                    stopTurn();
                    new PrivateBriefStore(this).clear();
                    if(!new SecretaryTaskStore(this).eraseAll()){
                        toast("Reset blocked: could not clear encrypted tasks");return;
                    }
                    prefs.edit().clear().commit();
                    prefs=Prefs.get(this);
                    resetChat();
                    detail="";
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
                        if(ok){
                            LocalModel.get().load(this,(loaded,info)->runOnUiThread(()->{
                                callState=info;
                                if(foreground)show();
                                if(!loaded)toast(info);
                            }));
                        } else {
                            if(foreground)show();
                            toast(message);
                        }
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
        neural.stop();
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
        if(!neural.isInstalled()){
            listeningLoop=false;
            status("Offline AI reply ready as text. Install KALLVO Hindi voice in Settings to hear it.");
            return;
        }
        status("Preparing built-in Hindi neural voice…");
        neural.speak(answer,(ok,message)->runOnUiThread(()->{
            if(!foreground)return;
            status(message);
            if(ok && listeningLoop && tab==1)beginListening();
            if(!ok)listeningLoop=false;
        }));
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
         if(neural!=null)neural.stop();
    }
    private void speechSettings() {
        try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}
        catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
    }
    private void toast(String s) {Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
