package in.textcall.lab;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Completely in-app Hindi/English neural voice powered by sherpa-onnx/Supertonic.
 * One explicit HTTPS download to a pinned host, SHA-256 validated, then offline.
 * No system TTS app dependency. Never transfers speech text off-device.
 */
final class NeuralVoice {
    interface Callback { void onResult(boolean ok,String status); }
    interface Listener { void onStatus(String status); }
    private static final String SOURCE =
        "https://github.com/chukfinley/supertonic-tts/releases/download/model-v1/supertonic3-model.zip";
    private static final String SHA256 =
        "34dcc85eb9e743d7dd2875d151149ea4d9b4f13890f8068db10aae8bdcbc7843";
    private static final long MAX_ARCHIVE = 160_000_000L;
    private static final long MAX_UNPACKED = 620_000_000L;
    private static final String[] FILES = {
        "duration_predictor.int8.onnx","text_encoder.int8.onnx",
        "vector_estimator.int8.onnx","vocoder.int8.onnx",
        "tts.json","unicode_indexer.bin","voice.bin"
    };
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"Kallvo-NativeVoice");
        t.setPriority(Thread.NORM_PRIORITY-1); return t;
    });
    private final Context app;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile String status="Voice pack not installed";
    private volatile boolean downloading;
    private volatile boolean speaking;
    private volatile long completedSamples;
    private final AtomicLong speakEpoch=new AtomicLong();
    private volatile Listener listener;
    private OfflineTts tts;
    private AudioTrack track;
    NeuralVoice(Context context) {
        app=context.getApplicationContext();
        prefs=Prefs.get(app);
        status=isInstalled()?"Hindi neural voice installed":"Install Hindi neural voice (one time)";
    }
    void listen(Listener l) {listener=l; if(l!=null) l.onStatus(status);}
    private void report(String s) {
        status=s;
        Listener current=listener;
        if(current!=null)main.post(()->{
            Listener next=listener;
            if(next!=null)next.onStatus(s);
        });
    }
    String status(){return status;}
    boolean downloading(){return downloading;}
    boolean speaking(){return speaking;}
    int speaker(){return Math.max(0,Math.min(9,prefs.getInt("native_voice_id",1)));}
    void setSpeaker(int sid){prefs.edit().putInt("native_voice_id",Math.max(0,Math.min(9,sid))).apply();}
    static String speakerLabel(int i){return i<5?"Female "+(i+1):"Male "+(i-4);}
    private File root(){return new File(app.getFilesDir(),"neural-voice-v1");}
    private File archive(){return new File(root(),"model.zip");}
    private File modelFolder(){return new File(root(),"unpacked");}
    private File asset(String name){return locate(modelFolder(),name,0);}
    static File locate(File dir,String filename,int depth) {
        if(dir==null||depth>3||!dir.exists())return null;
        File candidate=new File(dir,filename);
        if(candidate.isFile())return candidate;
        File[] entries=dir.listFiles();
        if(entries!=null)for(File entry:entries){
            if(entry.isDirectory()){
                File found=locate(entry,filename,depth+1);
                if(found!=null)return found;
            }
        }
        return null;
    }
    boolean isInstalled(){
        if(!new File(root(),".verified").exists())return false;
        for(String filename:FILES)if(asset(filename)==null)return false;
        return true;
    }
    void install(Callback callback) {
        if(downloading){callback.onResult(false,"A voice download is already running");return;}
        if(isInstalled()){callback.onResult(true,"Hindi neural voice already installed");return;}
        downloading=true;
        WORKER.execute(()->{
            try {
                if(!root().exists()&&!root().mkdirs())throw new Exception("Voice storage unavailable");
                downloadVerified();
                unpackVerified();
                new File(root(),".verified").createNewFile();
                archive().delete();
                report("Hindi neural voice ready • offline • 10 speakers");
                done(callback,true,status);
            }catch(Exception e){
                report("Voice setup failed: "+e.getMessage());
                done(callback,false,status);
            }finally{downloading=false;}
        });
    }
    private void done(Callback c,boolean ok,String msg){main.post(()->{if(c!=null)c.onResult(ok,msg);});}
    private static String hex(byte[] data){
        StringBuilder b=new StringBuilder();
        for(byte c:data)b.append(String.format(Locale.ROOT,"%02x",c&255));
        return b.toString();
    }
    private static boolean validArchive(File file)throws Exception {
        if(!file.exists()||file.length()<5_000_000||file.length()>MAX_ARCHIVE)return false;
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(file)){
            byte[] buf=new byte[65536];int n;
            while((n=in.read(buf))!=-1)d.update(buf,0,n);
        }
        return SHA256.equalsIgnoreCase(hex(d.digest()));
    }
    private void downloadVerified()throws Exception {
        File zip=archive();
        if(validArchive(zip))return;
        long offset=zip.exists()?zip.length():0;
        if(offset>MAX_ARCHIVE) {zip.delete();offset=0;}
        HttpURLConnection c=null;
        try {
            URL url=new URL(SOURCE);
            c=(HttpURLConnection)url.openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent","KALLVO/1.0 voice pack");
            if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
            c.connect();
            int code=c.getResponseCode();
            if(code!=206&&code!=200)throw new Exception("Download HTTP "+code);
            if(code==200)offset=0;
            long total=c.getContentLengthLong()+offset;
            if(total>MAX_ARCHIVE)throw new Exception("Voice pack too large");
            try(RandomAccessFile out=new RandomAccessFile(zip,"rw");
                java.io.InputStream in=c.getInputStream()) {
                if(offset==0)out.setLength(0); else out.seek(offset);
                byte[] buf=new byte[65536];int n;long bytes=offset;int last=-1;
                while((n=in.read(buf))!=-1){
                    bytes+=n;
                    if(bytes>MAX_ARCHIVE)throw new Exception("Download exceeds limit");
                    out.write(buf,0,n);
                    int pct=total>0?(int)(100L*bytes/total):0;
                    if(pct!=last && (pct%2==0||pct>=99)){
                        report("Downloading Hindi voice… "+pct+"%");
                        last=pct;
                    }
                }
            }
        } finally {if(c!=null)c.disconnect();}
        report("Checking voice model integrity…");
        if(!validArchive(zip)){zip.delete();throw new Exception("Model checksum mismatch (download not trusted)");}
    }
    private void unpackVerified() throws Exception {
        report("Preparing offline neural voice…");
        File dir=modelFolder();
        if(!dir.exists()&&!dir.mkdirs())throw new Exception("Cannot create voice folder");
        long total=0;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new FileInputStream(archive())))){
            ZipEntry entry;
            byte[] buffer=new byte[65536];
            String canonical=dir.getCanonicalPath()+File.separator;
            while((entry=zip.getNextEntry())!=null){
                if(entry.getName().startsWith("/")||entry.getName().contains("\\"))
                    throw new SecurityException("Unsafe voice model entry");
                File target=new File(dir,entry.getName());
                String location=target.getCanonicalPath();
                if(!location.startsWith(canonical))throw new SecurityException("Unsafe ZIP path");
                if(entry.isDirectory()) {target.mkdirs();zip.closeEntry();continue;}
                if(target.getParentFile()!=null)target.getParentFile().mkdirs();
                try(FileOutputStream output=new FileOutputStream(target)){
                    int n;
                    while((n=zip.read(buffer))!=-1){
                        total+=n;
                        if(total>MAX_UNPACKED)throw new Exception("Unpacked model too large");
                        output.write(buffer,0,n);
                    }
                }
                zip.closeEntry();
            }
        }
        for(String f:FILES)if(asset(f)==null)throw new Exception("Model missing "+f);
    }
    void speak(String message,Callback callback) {
        if(speaking){done(callback,false,"Voice is busy. Wait or tap Stop.");return;}
        if(!isInstalled()){done(callback,false,"Install Hindi voice in KALLVO first");return;}
        if(message==null||message.trim().isEmpty()){done(callback,false,"Nothing to speak");return;}
        final long epoch=speakEpoch.incrementAndGet();
        speaking=true;
        WORKER.execute(()->{
            try {
                ensureEngine();
                if(epoch!=speakEpoch.get())return;
                int sid=speaker();
                String text=message.trim();
                String lang=HindiLanguage.likelyHindi(text)?"hi":"en";
                GenerationConfig cfg=new GenerationConfig(0.2f,1.0f,sid,
                    null,0,null,5,Collections.singletonMap("lang",lang));
                report("Generating "+("hi".equals(lang)?"Hindi":"English")+" voice…");
                GeneratedAudio wave=tts.generateWithConfig(text,cfg);
                if(epoch!=speakEpoch.get())return;
                float[] values=wave.getSamples();
                if(values==null||values.length<1000)throw new Exception("Speech engine returned no audio");
                report("Playing "+speakerLabel(sid)+" • local neural voice");
                play(values,wave.getSampleRate(),epoch);
                if(epoch==speakEpoch.get()){
                    report("Voice played successfully");
                    done(callback,true,status);
                }
            }catch(Throwable failure) {
                String why=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();
                report("Offline voice failed: "+why);
                done(callback,false,status);
            }finally {speaking=false;}
        });
    }
    private void ensureEngine()throws Exception {
        if(tts!=null)return;
        OfflineTtsSupertonicModelConfig cfg=new OfflineTtsSupertonicModelConfig(
            asset("duration_predictor.int8.onnx").getAbsolutePath(),
            asset("text_encoder.int8.onnx").getAbsolutePath(),
            asset("vector_estimator.int8.onnx").getAbsolutePath(),
            asset("vocoder.int8.onnx").getAbsolutePath(),
            asset("tts.json").getAbsolutePath(),
            asset("unicode_indexer.bin").getAbsolutePath(),
            asset("voice.bin").getAbsolutePath());
        OfflineTtsModelConfig model=new OfflineTtsModelConfig(
            new com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(),
            new com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig(),
            new com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig(),
            new com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig(),
            new com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig(),
            new com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig(),
            cfg,2,false,"cpu");
        tts=new OfflineTts(null,new OfflineTtsConfig(model,"","",1,0.2f));
    }
    private void play(float[] values,int sampleRate,long epoch)throws Exception {
        int rate=sampleRate>0?sampleRate:44100;
        AudioTrack player=new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setSampleRate(rate).build())
            .setBufferSizeInBytes(Math.max(AudioTrack.getMinBufferSize(rate,
                AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT),rate*2))
            .setTransferMode(AudioTrack.MODE_STREAM).build();
        track=player;
        try {
            player.play();
            byte[] buf=new byte[16384];
            int idx=0;
            while(idx<values.length && epoch==speakEpoch.get()){
                int count=Math.min(buf.length/2,values.length-idx);
                for(int i=0;i<count;i++){
                    float x=Math.max(-1f,Math.min(1f,values[idx+i]));
                    short pcm=(short)(x*32767);
                    buf[i*2]=(byte)(pcm&255);
                    buf[i*2+1]=(byte)((pcm>>8)&255);
                }
                int bytes=player.write(buf,0,count*2);
                if(bytes<0)throw new Exception("Android audio output failed "+bytes);
                idx+=bytes/2;
            }
            completedSamples=idx;
            if(epoch==speakEpoch.get()) {
                // Wait for actual playback head, not the full duration again after streaming.
                long deadline=android.os.SystemClock.elapsedRealtime()
                        + Math.min(30000L, (long)idx*1000L/rate+2000L);
                while(epoch==speakEpoch.get()
                        && (player.getPlaybackHeadPosition() & 0xffffffffL)<(long)idx
                        && android.os.SystemClock.elapsedRealtime()<deadline) {
                    Thread.sleep(35L);
                }
            }
        }finally{
            try{player.stop();}catch(Exception ignored){}
            player.release();
            if(track==player)track=null;
        }
    }
    void stop(){
        speakEpoch.incrementAndGet();
        AudioTrack current=track;
        if(current!=null)try{current.pause();current.flush();}catch(Exception ignored){}
    }
    void shutdown(){
        stop();
        WORKER.execute(()->{
            if(tts!=null){tts.release();tts=null;}
        });
    }
}
