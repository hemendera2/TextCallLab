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
import java.util.concurrent.atomic.AtomicLong;

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
    private final AtomicLong generationKey = new AtomicLong();
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
    private volatile boolean loading;
    public boolean isLoading() { return loading; }
    private volatile String status = nativeAvailable ? "No offline model loaded" :
            "Native llama.cpp library unavailable (" + nativeFailure + ")";
    private LocalModel() { }
    public static LocalModel get() { return INSTANCE; }
    public boolean isLoaded() { return loaded; }
    public String status() { return status; }
    /** Lightweight, non-blocking native progress; no model or caller data included. */
    public String progress() {
        if (!nativeAvailable) return status;
        try { return nativeProgress(); }
        catch (Throwable e) { return "Progress unavailable (" + e.getClass().getSimpleName() + ")"; }
    }
    /** Cooperative cancel; native inference checks it after each prefill/decode chunk. */
    public void cancel() {
        generationKey.incrementAndGet();
        if (!nativeAvailable) return;
        try { nativeCancel(); } catch (Throwable ignored) { }
    }
    public static File modelFile(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), LOCAL_FILE);
    }
    public static long modelSizeMiB(Context context) {
        File file=modelFile(context);
        return file.exists() ? file.length()/(1024L*1024L) : 0;
    }
    public static boolean isImported(Context context) {
        File f = modelFile(context);
        return f.exists() && f.length() > 200_000_000L;
    }
    /** Never delete the previous verified model before its replacement is complete. */
    private static void replaceVerified(File temporary, File dest) throws Exception {
        try {
            java.nio.file.Files.move(temporary.toPath(), dest.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            java.nio.file.Files.move(temporary.toPath(), dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
    /** Copies trusted user-chosen GGUF without loading entire file into RAM. */
    public void importUri(Context context, Uri uri, Callback callback) {
        Context app = context.getApplicationContext();
        serial.execute(() -> {
            File dest = modelFile(app);
            File temporary = new File(dest.getParentFile(), LOCAL_FILE + ".part");
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
                if (loaded) { nativeUnload(); loaded = false; }
                replaceVerified(temporary, dest);
                status = "Imported locally: " + (bytes / (1024 * 1024)) + " MiB. Load model next.";
                callback.done(true, status);
            } catch (Exception e) {
                temporary.delete();
                status = "Import failed: " + e.getMessage();
                callback.done(false, status);
            }
        });
    }
    /** Official small Qwen GGUF: install inside KALLVO without Termux. Network used ONLY here. */
    private static final String MODEL_URL =
        "https://huggingface.co/lmstudio-community/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf";
    private static final String MODEL_SHA =
        "cd47557a67d7e8f2891d98b5e1dbf2988544569fdf4f1bdb30e92b71aa61b548";
    private volatile boolean downloading;
    public boolean isDownloading(){return downloading;}
    public void downloadRecommended(Context context, Callback callback) {
        if(isImported(context)) { callback.done(true,"AI already installed. Reopen KALLVO to load it; no download needed."); return; }
        if(downloading) {callback.done(false,"Model download already in progress");return;}
        Context app=context.getApplicationContext();
        downloading=true;
        serial.execute(()->{
            File dest=modelFile(app);
            File tmp=new File(dest.getAbsolutePath()+".download");
            try {
                java.net.HttpURLConnection c=null;
                try{
                    long have=tmp.exists()?tmp.length():0L;
                    if(have>520_000_000L){tmp.delete();have=0;}
                    c=(java.net.HttpURLConnection)new java.net.URL(MODEL_URL).openConnection();
                    c.setConnectTimeout(25000);c.setReadTimeout(30000);
                    c.setInstanceFollowRedirects(true);
                    if(have>0)c.setRequestProperty("Range","bytes="+have+"-");
                    int code=c.getResponseCode();
                    if(code!=200&&code!=206)throw new Exception("Download error HTTP "+code);
                    if(code!=206)have=0;
                    long size=have+c.getContentLengthLong();
                    if(size>520_000_000L)throw new Exception("Unexpected model size");
                    try(java.io.RandomAccessFile file=new java.io.RandomAccessFile(tmp,"rw");
                        java.io.InputStream in=c.getInputStream()){
                        if(have==0)file.setLength(0);
                        else file.seek(have);
                        byte[] buffer=new byte[65536];int n;long total=have;
                        while((n=in.read(buffer))!=-1){
                            total+=n;if(total>520_000_000L)throw new Exception("Invalid model size");
                            file.write(buffer,0,n);
                            if(size>0)status="Downloading offline Hindi AI: "+(100*total/size)+"%";
                        }
                    }
                }finally{if(c!=null)c.disconnect();}
                status="Verifying Qwen GGUF checksum…";
                java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-256");
                try(java.io.FileInputStream in=new java.io.FileInputStream(tmp)){
                    byte[] buffer=new byte[65536];int n;
                    while((n=in.read(buffer))!=-1)sha.update(buffer,0,n);
                }
                StringBuilder actual=new StringBuilder();
                for(byte b:sha.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(!MODEL_SHA.equalsIgnoreCase(actual.toString())) {
                    tmp.delete();throw new Exception("Model SHA-256 mismatch");
                }
                try(java.io.RandomAccessFile reader=new java.io.RandomAccessFile(tmp,"r")){
                    if(reader.readInt()!=0x47475546)throw new Exception("File is not GGUF");
                }
                if(loaded){nativeUnload();loaded=false;}
                replaceVerified(tmp,dest);
                status="Offline Qwen3 0.6B installed • load model in Talk";
                callback.done(true,status);
            }catch(Exception error){
                status="AI download failed: "+error.getMessage();
                callback.done(false,status);
            }finally{downloading=false;}
        });
    }

    /** Release loaded GGUF without uninstalling the app; queued behind inference. */
    public void unload(Callback callback) {
        cancel();
        serial.execute(() -> {
            if (nativeAvailable) nativeUnload();
            loaded = false;
            status = "Model unloaded. Select another GGUF or load again.";
            callback.done(true, status);
        });
    }
    /** Restores only from the verified app-private file, never triggers network. */
    public void loadIfPresent(Context context, Callback callback) {
        if (!isImported(context) || loaded || downloading || loading) return;
        load(context, callback);
    }
    public void load(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        if (loading) { callback.done(false, "Offline AI is already loading"); return; }
        loading = true;
        status = "Loading saved GGUF from this phone…";
        serial.execute(() -> {
            try {
                if (!nativeAvailable) { callback.done(false, status); return; }
                if (loaded) { callback.done(true, "Model already loaded"); return; }
                if (!isImported(app)) { callback.done(false, "Install an AI model once in Settings"); return; }
                String error = nativeLoad(modelFile(app).getAbsolutePath());
                loaded = error.isEmpty();
                status = loaded ? "Saved offline AI restored. Ready."
                        : "Saved model could not load: " + error;
                callback.done(loaded, status);
            } catch (Throwable failure) {
                loaded = false;
                status = "Saved model could not load: " + failure.getClass().getSimpleName();
                callback.done(false, status);
            } finally {
                loading = false;
            }
        });
    }
    public void reply(String owner, String facts, String rules,
                      List<String> history, String caller, TextCallback callback) {
        if (!loaded) { callback.done("", "Model not loaded", 0); return; }
        final long id = generationKey.incrementAndGet();
        serial.execute(() -> {
            if (generationKey.get() != id) {
                callback.done("", "Cancelled before inference started", 0);
                return;
            }
            long begin = SystemClock.elapsedRealtime();
            try {
                String prompt = PromptFormatter.format(owner, facts, rules, history, caller);
                String response = nativeGenerate(prompt, 32);
                if (generationKey.get() != id) {
                    callback.done("", "Cancelled", SystemClock.elapsedRealtime() - begin);
                    return;
                }
                String cleaned = PromptFormatter.clean(response);
                if (cleaned.isEmpty())
                    callback.done("", "No usable reply. " + progress(),
                            SystemClock.elapsedRealtime() - begin);
                else callback.done(cleaned, "", SystemClock.elapsedRealtime() - begin);
            } catch (Throwable e) {
                callback.done("", "Offline model error: " + e.getClass().getSimpleName(),
                        SystemClock.elapsedRealtime() - begin);
            }
        });
    }
    /** Summarizes caller phrases on-device, never uploads them. */
    public void summarize(List<String> callerPhrases, TextCallback callback) {
        if (!loaded) { callback.done("", "Model not loaded", 0); return; }
        final long id = generationKey.incrementAndGet();
        serial.execute(() -> {
            if (generationKey.get() != id) {
                callback.done("", "Cancelled before summary", 0);
                return;
            }
            long begin = SystemClock.elapsedRealtime();
            String answer = nativeGenerate(PromptFormatter.brief(callerPhrases), 44);
            String cleaned = generationKey.get() == id ? PromptFormatter.clean(answer) : "";
            if (cleaned.isEmpty()) callback.done("", "Summary unavailable or cancelled. " + progress(), SystemClock.elapsedRealtime() - begin);
            else callback.done(cleaned, "", SystemClock.elapsedRealtime() - begin);
        });
    }
    private static native String nativeLoad(String absolutePath);
    private static native String nativeGenerate(String prompt, int maxNewTokens);
    private static native void nativeUnload();
    private static native void nativeCancel();
    private static native String nativeProgress();
}
