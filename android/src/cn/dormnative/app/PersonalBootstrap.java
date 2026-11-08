package cn.dormnative.app;

import android.content.Context;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Only personal builds contain the signed seed. It is recoverable from the APK, not a secret vault. */
final class PersonalBootstrap {
    static boolean available(Context c){try(InputStream in=c.getAssets().open("owner.dormkey")){return true;}catch(Exception e){return false;}}
    static boolean install(Context c) throws Exception {
        if(!SecureStore.prefs(c).getString("vault","").isEmpty()){SecureStore.ensureProfiles(c);return true;}
        if(SecureStore.prefs(c).getBoolean("personal_disabled",false))return false;
        if(!available(c))return false;
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=c.getAssets().open("owner.dormkey")){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new Exception("预置资料无法读取");out.write(b,0,n);}}
        JSONObject envelope=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        JSONObject seed=MigrationTrust.verify(envelope);
        SecureStore.save(c,envelope);
        return true;
    }
}
