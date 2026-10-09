package in.textcall.lab;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small, encrypted on-device action inbox. No network, notifications or call audio. */
final class SecretaryTaskStore {
    static final class Task {
        final String id;
        final String title;
        final boolean important;
        final long created;
        Task(String id,String title,boolean important,long created) {
            this.id=id;this.title=title;this.important=important;this.created=created;
        }
    }
    private static final String PREF="kallvo_private_tasks_v1";
    private static final String KEY_ALIAS="kallvo_task_aes_v1";
    private static final int MAX_TASKS=40;
    private final Context app;
    SecretaryTaskStore(Context context){app=context.getApplicationContext();}

    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing=store.getKey(KEY_ALIAS,null);
        if(existing!=null)return (SecretKey)existing;
        KeyGenerator maker=KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        maker.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return maker.generateKey();
    }
    private SharedPreferences prefs(){
        return app.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    private JSONArray read() throws Exception {
        String saved=prefs().getString("ciphertext","");
        if(saved.isEmpty())return new JSONArray();
        String[] fields=saved.split(":",-1);
        if(fields.length!=2)throw new IllegalStateException("Encrypted task record damaged");
        byte[] nonce=Base64.decode(fields[0],Base64.NO_WRAP);
        byte[] ciphertext=Base64.decode(fields[1],Base64.NO_WRAP);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));
        return new JSONArray(new String(cipher.doFinal(ciphertext),StandardCharsets.UTF_8));
    }
    private void save(JSONArray all) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] bytes=cipher.doFinal(all.toString().getBytes(StandardCharsets.UTF_8));
        String packed=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"
                +Base64.encodeToString(bytes,Base64.NO_WRAP);
        if(!prefs().edit().putString("ciphertext",packed).commit())
            throw new IllegalStateException("Task storage unavailable");
    }
    List<Task> list() throws Exception {
        JSONArray all=read();
        List<Task> result=new ArrayList<>();
        for(int i=0;i<all.length();i++){
            JSONObject task=all.getJSONObject(i);
            result.add(new Task(task.getString("id"),task.getString("title"),
                    task.optBoolean("important",false),task.optLong("created",0)));
        }
        result.sort(Comparator.comparing((Task x)->!x.important)
                .thenComparing((Task x)->-x.created));
        return result;
    }
    void add(String title,boolean important) throws Exception {
        String safe=title==null?"":title.trim().replaceAll("[\\r\\n]+"," ");
        if(safe.isEmpty())throw new IllegalArgumentException("Task cannot be empty");
        if(safe.length()>180)throw new IllegalArgumentException("Task is over 180 characters");
        JSONArray all=read();
        if(all.length()>=MAX_TASKS)throw new IllegalStateException("Finish an earlier task first");
        for(int i=0;i<all.length();i++)
            if(safe.equalsIgnoreCase(all.getJSONObject(i).getString("title")))
                throw new IllegalStateException("This follow-up already exists");
        JSONObject item=new JSONObject();
        item.put("id",UUID.randomUUID().toString());
        item.put("title",safe);
        item.put("important",important);
        item.put("created",System.currentTimeMillis());
        all.put(item);
        save(all);
    }
    /** Explicit privacy erase, keeps model downloads and other app settings intact. */
    boolean eraseAll(){ return prefs().edit().clear().commit(); }
    void finish(String id) throws Exception {
        JSONArray all=read(),remaining=new JSONArray();
        boolean found=false;
        for(int i=0;i<all.length();i++){
            JSONObject item=all.getJSONObject(i);
            if(id.equals(item.getString("id")))found=true;
            else remaining.put(item);
        }
        if(!found)throw new IllegalStateException("Task no longer exists");
        save(remaining);
    }
}
