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
    static final String VOICE = "voice_id";
    static final String TTS_ENGINE = "tts_engine_package";
    static final String LANGUAGE = "locale_tag";
    static final String STYLE = "voice_style";
    static final String SPEED = "speech_rate";
    static final String FLOATING = "floating_panel";
    static final String AUTO_ATTEND = "auto_attend_samsung_v1";
    static final String LIVE_REPLY = "live_reply_samsung_v1";
    static final String USE_LLM = "use_offline_qwen_for_calls_v1";
    static final String SAVE_BRIEF = "save_encrypted_call_brief_v1";
    static final String PROBE = "diagnostic_enabled";
    static final String PROFILE_NAME = "profile_name";
    static final String PROFILE_INFO = "profile_info";
    static final String PROFILE_RULES = "profile_rules";
    static final String SPEECH_LANGUAGE = "speech_language";
    private static final String SAFE_MIGRATED = "safe_migrated_v2";

    private Prefs() { }
    static SharedPreferences get(Context c) {
        SharedPreferences p = c.getSharedPreferences("text_call_lab_local", Context.MODE_PRIVATE);
        // One-time privacy migration: discard ALL diagnostics from the older APK.
        // Require explicit user consent to resume scanning. Never auto-send.
        if (!p.getBoolean(SAFE_MIGRATED, false)) {
            p.edit().remove(DIAGNOSTICS).remove(STATUS)
                    .putBoolean(ENABLED, false)
                    .putBoolean(AUTO_SEND, false)
                    .putBoolean(SAFE_MIGRATED, true).commit();
        }
        return p;
    }
    static String value(SharedPreferences p, String key, String fallback) {
        return p.getString(key, fallback);
    }
    static void status(Context c, String status, String diagnostics) {
        get(c).edit().putString(STATUS, status)
            .putString(DIAGNOSTICS, diagnostics).apply();
    }
}
