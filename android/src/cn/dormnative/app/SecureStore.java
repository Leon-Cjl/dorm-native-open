package cn.dormnative.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

final class SecureStore {
    private static final String ALIAS="dormnative.authorization.v1";
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("native_open",Context.MODE_PRIVATE);}
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return g.generateKey();
    }
    private static String encrypt(JSONObject state)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] data=cipher.doFinal(state.toString().getBytes(StandardCharsets.UTF_8));
        return new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(data,Base64.NO_WRAP)).toString();
    }
    private static JSONObject decrypt(String text)throws Exception{
        JSONObject wrapper=new JSONObject(text);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(wrapper.getString("iv"),Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(wrapper.getString("data"),Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    static synchronized JSONObject load(Context c)throws Exception{
        String text=prefs(c).getString("vault","");return text.isEmpty()?null:decrypt(text);
    }
    static synchronized ProfileCatalog catalog(Context c)throws Exception{
        String text=prefs(c).getString("profiles_v1","");
        if(text.length()>2097152)throw new Exception("本机账号资料过大");
        return new ProfileCatalog(text.isEmpty()?null:decrypt(text));
    }
    /** Upgrade 0.6.0 without altering the active authorization or calibration. */
    static synchronized void ensureProfiles(Context c)throws Exception{
        JSONObject envelope=load(c);if(envelope==null)return;
        JSONObject seed=MigrationTrust.verify(envelope);String id=ProfileCatalog.id(seed);
        ProfileCatalog list=catalog(c);
        if(id.equals(prefs(c).getString("profile_id",""))){list.find(id);return;}
        list.upsert(envelope,seed,prefs(c).getString("room_label",seed.getString("roomNo")));
        if(!prefs(c).edit().putString("profiles_v1",encrypt(list.json())).putString("profile_id",id).commit())throw new Exception("账号资料升级保存失败");
    }
    static synchronized void save(Context c,JSONObject envelope)throws Exception{
        JSONObject seed=MigrationTrust.verify(envelope);activate(c,envelope,seed.getString("roomNo"));
    }
    /** Call while holding OperationGuard after stopping the monitor. One atomic preference transaction. */
    static synchronized void activate(Context c,JSONObject envelope,String label)throws Exception{
        JSONObject seed=MigrationTrust.verify(envelope);
        ensureProfiles(c);ProfileCatalog list=catalog(c);String id=list.upsert(envelope,seed,label);
        String vault=encrypt(envelope),profiles=encrypt(list.json());
        SharedPreferences.Editor edit=resetEditor(prefs(c).edit());
        edit.putString("vault",vault).putString("profiles_v1",profiles).putString("profile_id",id).putString("room_label",label)
            .putString("capability",newCapability()).putBoolean("personal_disabled",false);
        if(!edit.commit())throw new Exception("本机授权保存失败，请重新打开应用核对当前宿舍");
    }
    static synchronized void removeProfile(Context c,String id)throws Exception{
        ensureProfiles(c);ProfileCatalog list=catalog(c);list.remove(id);
        String profiles=encrypt(list.json());SharedPreferences.Editor edit=prefs(c).edit().putString("profiles_v1",profiles);
        if(id.equals(prefs(c).getString("profile_id",""))){
            resetEditor(edit).remove("vault").remove("profile_id").remove("room_label").putString("capability",newCapability()).putBoolean("personal_disabled",true);
        }
        if(!edit.commit())throw new Exception("本机资料未能保存，请重新打开应用核对");
    }
    private static String newCapability(){
        byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);return Base64.encodeToString(bytes,Base64.NO_WRAP|Base64.URL_SAFE);
    }
    static synchronized String capability(Context c){
        String existing=prefs(c).getString("capability","");if(!existing.isEmpty())return existing;
        String value=newCapability();prefs(c).edit().putString("capability",value).commit();return value;
    }
    static boolean matchesCapability(Context c,String value){return TargetToken.matches(capability(c),value);}
    private static SharedPreferences.Editor resetEditor(SharedPreferences.Editor e){
        for(String k:new String[]{"manual_can_confirm","manual_confirmed","calibrated","calibration_version","near_median","far_median","near_rssi","far_rssi","near_preset_revision","auto_last","auto_diagnostic","observe_last","monitor_active","events","last_manual_elapsed","last_result","last_duration_ms","last_timing","scan_balanced"})e.remove(k);
        return e;
    }
    static void resetAutomation(Context c){resetEditor(prefs(c).edit()).apply();}
    static synchronized void clear(Context c)throws Exception{
        if(!resetEditor(prefs(c).edit()).remove("vault").remove("profiles_v1").remove("profile_id").remove("room_label").putString("capability",newCapability()).putBoolean("personal_disabled",true).commit())throw new Exception("清除保存失败，请重新打开应用核对");
    }
}

