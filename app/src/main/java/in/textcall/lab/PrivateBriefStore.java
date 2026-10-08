package in.textcall.lab;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Device-only encrypted brief. Never stores caller audio, caller name/number or
 * transcript. Optional AI-derived brief may contain sensitive inferred details,\n * encrypted locally. Encryption failure fails closed; no plaintext fallback.
 */
final class PrivateBriefStore {
    private static final String KEY = "callcompanion_brief_v1";
    private static final String PREF = "private_call_brief";
    private final Context app;

    PrivateBriefStore(Context context) { app = context.getApplicationContext(); }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(KEY, null);
        if (existing != null) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    boolean save(String category, String followUp, int turns) {
        return save(category, followUp, turns, "");
    }
    boolean save(String category, String followUp, int turns, String aiSummary) {
        try {
            String safeCategory = safe(category, 40);
            String safeAction = safe(followUp, 150);
            String text = new Date().toString() + "\nCategory: " + safeCategory
                    + "\nIncoming turns: " + Math.max(0, Math.min(turns, 100))
                    + "\nNext action: " + safeAction
                    + "\nCaller name/number: Not collected"
                    + "\nTranscript/audio: Not stored"
                    + (aiSummary == null || aiSummary.trim().isEmpty() ? ""
                        : "\nAI-generated summary (verify before acting): " + safe(aiSummary, 480));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] ciphertext = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "."
                    + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
            app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString("last", encoded).apply();
            return true;
        } catch (Exception error) { return false; }
    }

    String read() {
        String payload = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString("last", "");
        if (payload.isEmpty()) return "No call brief saved yet.";
        try {
            String[] parts = payload.split("\\.", -1);
            if (parts.length != 2) return "Encrypted brief unavailable.";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception error) { return "Encrypted brief could not be opened on this device."; }
    }
    void clear() {
        app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply();
    }
    private static String safe(String s,int max) {
        if (s == null) return "";
        String v = s.replace('\n',' ').replace('\r',' ');
        return v.length() > max ? v.substring(0,max) : v;
    }
}