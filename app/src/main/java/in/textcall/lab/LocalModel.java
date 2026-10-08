package in.textcall.lab;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Private, offline model runtime. JNI/llama.cpp runs only on this phone.
 * Model import is a single app-private copy from the SAF picker; input file
 * stays untouched. No service, network or Termux dependency after install.
 */
public final class LocalModel {
    private static final LocalModel INSTANCE = new LocalModel();
    public interface Callback { void done(boolean ok, String message); }
    public interface TextCallback { void done(String reply, String error, long millis); }
    private final ExecutorService serial = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CallCompanion-LLM");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final String LOCAL_FILE = "secretary-model.gguf";
    private static volatile boolean nativeAvailable;
    private static final String nativeFailure;
    static {
        String error = "";
        try { System.loadLibrary("callcompanion_llm"); nativeAvailable = true; }
        catch (Throwable t) { error = t.getClass().getSimpleName(); }
        nativeFailure = error;
    }
    private volatile boolean loaded;
    private volatile String status = nativeAvailable ? "No offline model loaded" :
            "Native llama.cpp library unavailable (" + nativeFailure + ")";
    private LocalModel() { }
    public static LocalModel get() { return INSTANCE; }
    public boolean isLoaded() { return loaded; }
    public String status() { return status; }
    public static File modelFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), LOCAL_FILE);
    }
    public static boolean isImported(Context context) {
        File f = modelFile(context);
        return f.exists() && f.length() > 200_000_000L;
    }
    /** Copies trusted user-chosen GGUF without loading entire file into RAM. */
    public void importUri(Context context, Uri uri, Callback callback) {
        Context app = context.getApplicationContext();
        serial.execute(() -> {
            File dest = modelFile(app);
            File temporary = new File(dest.getParentFile(), LOCAL_FILE + ".part");
            if (loaded) {
                callback.done(false, "Unload model / restart app before replacing existing GGUF.");
                return;
            }
            try {
                status = "Importing selected GGUF…";
                long bytes = 0;
                try (InputStream input = app.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) throw new IllegalStateException("File picker did not supply model bytes");
                    byte[] buffer = new byte[1024 * 1024];
                    for (int n; (n = input.read(buffer)) != -1;) {
                        output.write(buffer, 0, n);
                        bytes += n;
                        if (bytes > 2_200_000_000L) throw new IllegalStateException("Model too large for this mobile build");
                    }
                    output.getFD().sync();
                }
                if (bytes < 200_000_000L) throw new IllegalStateException("GGUF too small or incomplete");
                try (java.io.RandomAccessFile reader = new java.io.RandomAccessFile(temporary, "r")) {
                    if (reader.readInt() != 0x47475546)
                        throw new IllegalArgumentException("Selected file is not a GGUF");
                }
                if (dest.exists() && !dest.delete()) throw new IllegalStateException("Cannot replace previous model");
                if (!temporary.renameTo(dest)) throw new IllegalStateException("Cannot finalize model import");
                status = "Imported locally: " + (bytes / (1024 * 1024)) + " MiB. Load model next.";
                callback.done(true, status);
            } catch (Exception e) {
                temporary.delete();
                status = "Import failed: " + e.getMessage();
                callback.done(false, status);
            }
        });
    }
    public void load(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        serial.execute(() -> {
            if (!nativeAvailable) { callback.done(false, status); return; }
            if (loaded) { callback.done(true, "Model already loaded"); return; }
            if (!isImported(app)) { callback.done(false, "Import GGUF from Downloads first"); return; }
            status = "Loading GGUF into CPU inference engine…";
            String error = nativeLoad(modelFile(app).getAbsolutePath());
            loaded = error.isEmpty();
            status = loaded ? "Qwen GGUF loaded locally. Ready for offline responses."
                    : "Model load failed: " + error;
            callback.done(loaded, status);
        });
    }
    public void reply(String owner, String facts, String rules,
                      List<String> history, String caller, TextCallback callback) {
        if (!loaded) { callback.done("", "Model not loaded", 0); return; }
        serial.execute(() -> {
            long begin = SystemClock.elapsedRealtime();
            String prompt = PromptFormatter.format(owner, facts, rules, history, caller);
            String response = nativeGenerate(prompt, 64);
            String cleaned = PromptFormatter.clean(response);
            if (cleaned.isEmpty()) callback.done("", "No usable generated reply", SystemClock.elapsedRealtime() - begin);
            else callback.done(cleaned, "", SystemClock.elapsedRealtime() - begin);
        });
    }
    private static native String nativeLoad(String absolutePath);
    private static native String nativeGenerate(String prompt, int maxNewTokens);
}
