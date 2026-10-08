package in.textcall.lab;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String ENABLED = "enabled";
    static final String AUTO_SEND = "auto_send";
    static final String OWNER = "owner";
    static final String INSTRUCTIONS = "instructions";
    static final String CALLER_ID = "caller_id";
    static final String SEND_ID = "send_id";
    static final String STATUS = "status";
    static final String DIAGNOSTICS = "diagnostics";

    private Prefs() { }
    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("text_call_lab_local", Context.MODE_PRIVATE);
    }
    static String value(SharedPreferences p, String key, String fallback) {
        return p.getString(key, fallback);
    }
    static void status(Context c, String status, String diagnostics) {
        get(c).edit().putString(STATUS, status)
            .putString(DIAGNOSTICS, diagnostics).apply();
    }
}
