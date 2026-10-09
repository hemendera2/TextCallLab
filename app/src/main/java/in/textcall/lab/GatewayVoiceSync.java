package in.textcall.lab;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;

/**
 * Opt-in owner pairing to hosted live-call gateway.
 * Never sends caller audio from Android. Saves bearer token encrypted under Android Keystore.
 */
final class GatewayVoiceSync {
    interface Result { void done(boolean ok, String status); }
    private static final String PREF="kallvo_gateway_voice_v1";
    private static final String KEY="kallvo_gateway_token_v1";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    static boolean paired(Context c){
        return !prefs(c).getString("url","").isEmpty()
                && !prefs(c).getString("auth","").isEmpty();
    }
    private static SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key old=store.getKey(KEY,null);
        if(old!=null)return (SecretKey)old;
        KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static void pair(Context c,String address,String secret)throws Exception{
        String endpoint=address==null?"":address.trim();
        URL url=new URL(endpoint);
        if(!"https".equalsIgnoreCase(url.getProtocol())||url.getUserInfo()!=null
                ||url.getHost().isEmpty() || !"/control/voice".equals(url.getPath())
                || url.getQuery()!=null || url.getRef()!=null)
            throw new IllegalArgumentException("Use your HTTPS gateway /control/voice URL");
        if(secret==null||secret.trim().length()<24)
            throw new IllegalArgumentException("Owner token must contain at least 24 characters");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        String packed=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"
                +Base64.encodeToString(cipher.doFinal(secret.trim().getBytes(StandardCharsets.UTF_8)),
                        Base64.NO_WRAP);
        if(!prefs(c).edit().putString("url",endpoint).putString("auth",packed).commit())
            throw new IllegalStateException("Could not securely save gateway pairing");
    }
    static void disconnect(Context c){
        prefs(c).edit().clear().commit();
    }
    private static String token(Context c)throws Exception{
        String packed=prefs(c).getString("auth","");
        String[] parts=packed.split(":",-1);
        if(parts.length!=2)throw new IllegalStateException("Gateway pairing missing");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,
                Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),
                StandardCharsets.UTF_8);
    }
    static void sync(Context context,int speaker,Result cb){
        Context app=context.getApplicationContext();
        if(!paired(app)){MAIN.post(()->cb.done(false,"No voice gateway paired"));return;}
        if(speaker<0||speaker>9){MAIN.post(()->cb.done(false,"Invalid speaker"));return;}
        IO.execute(()->{
            HttpsURLConnection http=null;
            try{
                http=(HttpsURLConnection)new URL(prefs(app).getString("url","")).openConnection();
                http.setInstanceFollowRedirects(false);
                http.setRequestMethod("POST");
                http.setConnectTimeout(8000);
                http.setReadTimeout(8000);
                http.setRequestProperty("Authorization","Bearer "+token(app));
                http.setRequestProperty("Content-Type","application/json; charset=utf-8");
                http.setDoOutput(true);
                byte[] data=("{\"speaker_id\":"+speaker+"}").getBytes(StandardCharsets.UTF_8);
                http.setFixedLengthStreamingMode(data.length);
                try(OutputStream output=http.getOutputStream()){output.write(data);}
                int code=http.getResponseCode();
                boolean ok=code==200;
                String label=ok?"Selected call voice synchronized":"Gateway rejected voice sync (HTTP "+code+")";
                MAIN.post(()->cb.done(ok,label));
            }catch(Exception e){
                String label="Gateway voice sync failed: "+e.getClass().getSimpleName();
                MAIN.post(()->cb.done(false,label));
            }finally{
                if(http!=null)http.disconnect();
            }
        });
    }
}
