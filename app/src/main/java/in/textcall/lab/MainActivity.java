package in.textcall.lab;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private SharedPreferences p;
    private CheckBox enabled;
    private CheckBox autoSend;
    private EditText owner;
    private EditText instructions;
    private EditText callerId;
    private EditText sendId;
    private EditText testText;
    private TextView state;
    private TextView diagnostics;
    private TextView testOutput;

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        p = Prefs.get(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int m = dip(15);
        content.setPadding(m,m,m,m);
        scroll.addView(content);

        TextView title = label("TextCall Lab  •  A52s 5G", 24);
        title.setTextColor(Color.rgb(0, 80, 145));
        content.addView(title);
        content.addView(label("₹0 / no account / no API / no internet permission", 14));
        content.addView(label("VERSION 0.1 — EXPERIMENTAL SCREEN PROBE", 15));
        content.addView(label("This is a LOCAL scripted responder, NOT a human-level AI model. Samsung One UI access is NOT yet verified on your A52s.", 15));

        Button accessibility = button("1. OPEN ACCESSIBILITY SETTINGS");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        content.addView(accessibility);

        enabled = new CheckBox(this);
        enabled.setText("2. Monitor Samsung Text Call after I manually answer via Bixby");
        enabled.setChecked(p.getBoolean(Prefs.ENABLED, false));
        enabled.setOnCheckedChangeListener((button, checked) -> p.edit().putBoolean(Prefs.ENABLED, checked).apply());
        content.addView(enabled);

        autoSend = new CheckBox(this);
        autoSend.setText("DANGER: automatically SEND real replies (OFF by default)");
        autoSend.setChecked(p.getBoolean(Prefs.AUTO_SEND, false));
        autoSend.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) p.edit().putBoolean(Prefs.AUTO_SEND, false).apply();
            else new android.app.AlertDialog.Builder(this)
                    .setTitle("Confirm live auto-sending")
                    .setMessage("Only enable after confirming the caller text ID and SEND ID on a trusted test call. Wrong IDs could send replies to the wrong messages. Never use on a real customer call until tested. Continue?")
                    .setPositiveButton("I tested it", (dialog, which) -> p.edit().putBoolean(Prefs.AUTO_SEND, true).apply())
                    .setNegativeButton("Not yet", (dialog, which) -> autoSend.setChecked(false))
                    .show();
        });
        content.addView(autoSend);

        content.addView(label("3. OWNER INFORMATION (local only)", 17));
        owner = input("Owner/business name", p.getString(Prefs.OWNER, "Owner"), false);
        content.addView(owner);
        instructions = input("Allowed public info about business (keep concise)", p.getString(Prefs.INSTRUCTIONS, ""), true);
        content.addView(instructions);
        content.addView(label("4. ONE UI VIEW IDs (identify from test call diagnostics)", 17));
        content.addView(label("Leave these blank until diagnosed. This prevents accidental auto-replies.", 13));
        callerId = input("Caller transcript resource ID", p.getString(Prefs.CALLER_ID, ""), false);
        content.addView(callerId);
        sendId = input("Send button resource ID", p.getString(Prefs.SEND_ID, ""), false);
        content.addView(sendId);
        Button save = button("SAVE SETTINGS");
        save.setOnClickListener(v -> {
            p.edit().putString(Prefs.OWNER, owner.getText().toString().trim())
                    .putString(Prefs.INSTRUCTIONS, instructions.getText().toString().trim())
                    .putString(Prefs.CALLER_ID, callerId.getText().toString().trim())
                    .putString(Prefs.SEND_ID, sendId.getText().toString().trim()).apply();
            state.setText("Settings saved. Start a trusted test call using Samsung Bixby Text Call.");
        });
        content.addView(save);

        content.addView(label("5. OFFLINE REPLY TEST (no phone call needed)", 17));
        testText = input("Type a caller sentence: Hello, price kya hai?", "Hello, who are you?", false);
        content.addView(testText);
        Button reply = button("GENERATE OFFLINE TEST REPLY");
        reply.setOnClickListener(v -> testOutput.setText(OfflineResponder.reply(
                testText.getText().toString(), owner.getText().toString(), instructions.getText().toString())));
        content.addView(reply);
        testOutput = label("Test response appears here", 16);
        content.addView(testOutput);

        content.addView(label("6. LIVE PROBE DIAGNOSTICS (phone-local)", 17));
        state = label("Service status not yet checked", 15);
        content.addView(state);
        Button refresh = button("REFRESH DIAGNOSTICS");
        refresh.setOnClickListener(v -> refresh());
        content.addView(refresh);
        Button clear = button("CLEAR LOCAL DIAGNOSTICS + DISABLE AUTO-SEND");
        clear.setOnClickListener(v -> {
            p.edit().remove(Prefs.DIAGNOSTICS).remove(Prefs.STATUS)
                    .putBoolean(Prefs.AUTO_SEND, false).apply();
            autoSend.setChecked(false);
            refresh();
        });
        content.addView(clear);
        diagnostics = label("No live Samsung UI capture yet.", 12);
        diagnostics.setTextIsSelectable(true);
        content.addView(diagnostics);
        content.addView(label("PRIVACY: These logs may contain callers' private text. Do not upload or share unredacted logs. This app never uses the internet.\n\nTo test, keep auto-send OFF, open Bixby Text Call on a trusted incoming call, let the caller say one sentence, then come back and tap Refresh. Full end-to-end success depends on whether One UI exposes the transcript and input controls.", 14));
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (p == null || state == null || diagnostics == null) return;
        String previous = p.getString(Prefs.STATUS, "Waiting for Bixby Text Call.");
        state.setText("Service permission: " + (isServiceEnabled() ? "ENABLED" : "NOT ENABLED")
                + "\n" + previous);
        diagnostics.setText(p.getString(Prefs.DIAGNOSTICS,
                "No data yet. Try a trusted call with monitoring ON and auto-send OFF."));
    }

    private boolean isServiceEnabled() {
        String all = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return all != null && all.contains(getPackageName() + "/" + BixbyAccessibilityService.class.getName());
    }

    private TextView label(String text, int sp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(36, 40, 50));
        t.setPadding(0, dip(8), 0, dip(8));
        return t;
    }
    private EditText input(String hint, String value, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(15);
        if (multiline) {
            e.setMinLines(2);
            e.setGravity(Gravity.TOP);
        } else e.setSingleLine(true);
        return e;
    }
    private Button button(String name) {
        Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        return b;
    }
    private int dip(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }
}
