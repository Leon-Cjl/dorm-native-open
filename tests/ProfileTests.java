package cn.dormnative.app;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.security.*;
import java.security.cert.Certificate;
import javax.crypto.spec.SecretKeySpec;
import org.json.*;
import android.content.*;

public final class ProfileTests {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Profile assertion "+checks);}
    interface Work{void run()throws Exception;}
    static void rejected(Work work)throws Exception{try{work.run();throw new AssertionError("Expected rejection");}catch(AssertionError e){throw e;}catch(Exception expected){checks++;}}
    static JSONObject seed(String company,String staff,String room)throws Exception{
        return new JSONObject().put("companyID",company).put("staffID",staff).put("personId","1234").put("roomID",room).put("deviceID","device-"+room).put("MAC","02:00:00:00:00:01");
    }
    public static final class TestKeyStore extends KeyStoreSpi{
        public TestKeyStore(){}
        public Key engineGetKey(String a,char[] p){return new SecretKeySpec(new byte[16],"AES");}
        public Certificate[] engineGetCertificateChain(String a){return null;}
        public Certificate engineGetCertificate(String a){return null;}
        public Date engineGetCreationDate(String a){return new Date(0);}
        public void engineSetKeyEntry(String a,Key k,char[] p,Certificate[] c){}
        public void engineSetKeyEntry(String a,byte[] k,Certificate[] c){}
        public void engineSetCertificateEntry(String a,Certificate c){}
        public void engineDeleteEntry(String a){}
        public Enumeration<String> engineAliases(){return Collections.enumeration(Collections.singleton("fixture"));}
        public boolean engineContainsAlias(String a){return true;}
        public int engineSize(){return 1;}
        public boolean engineIsKeyEntry(String a){return true;}
        public boolean engineIsCertificateEntry(String a){return false;}
        public String engineGetCertificateAlias(Certificate c){return null;}
        public void engineStore(java.io.OutputStream o,char[] p){}
        public void engineLoad(java.io.InputStream i,char[] p){}
    }
    static final class MemoryPrefs implements SharedPreferences{
        Map<String,Object> data=new HashMap<String,Object>();int commits;boolean fail;
        public String getString(String k,String d){return data.containsKey(k)?(String)data.get(k):d;}
        public boolean getBoolean(String k,boolean d){return data.containsKey(k)?(Boolean)data.get(k):d;}
        public int getInt(String k,int d){return data.containsKey(k)?(Integer)data.get(k):d;}
        public Editor edit(){return new Editor(){
            Map<String,Object> additions=new HashMap<String,Object>();Set<String> removals=new HashSet<String>();
            public Editor putString(String k,String v){additions.put(k,v);removals.remove(k);return this;}
            public Editor putBoolean(String k,boolean v){additions.put(k,v);removals.remove(k);return this;}
            public Editor putInt(String k,int v){additions.put(k,v);removals.remove(k);return this;}
            public Editor remove(String k){removals.add(k);additions.remove(k);return this;}
            public boolean commit(){commits++;for(String k:removals)data.remove(k);data.putAll(additions);return !fail;}
            public void apply(){commit();}
        };}
    }
    static final class TestContext extends Context{
        final MemoryPrefs prefs=new MemoryPrefs();
        public SharedPreferences getSharedPreferences(String n,int mode){return prefs;}
    }
    public static void main(String[] args)throws Exception{
        java.net.URL.setURLStreamHandlerFactory(p->new java.net.URLStreamHandler(){protected java.net.URLConnection openConnection(java.net.URL u){throw new AssertionError("Network forbidden");}});
        TestContext signalContext=new TestContext();
        check(AutoConfig.near(signalContext)==-65);AutoConfig.applyDoorSignalFix(signalContext);check(AutoConfig.near(signalContext)==-65);
        signalContext.prefs.data.clear();signalContext.prefs.edit().putBoolean("calibrated",true).putInt("calibration_version",SignalCalibration.VERSION).putInt("near_rssi",-55).putInt("far_rssi",-60).apply();
        AutoConfig.applyDoorSignalFix(signalContext);check(AutoConfig.near(signalContext)==-65);check(AutoConfig.far(signalContext)==-73);
        signalContext.prefs.edit().putInt("near_rssi",-60).apply();AutoConfig.applyDoorSignalFix(signalContext);check(AutoConfig.near(signalContext)==-60);
        signalContext.prefs.data.clear();signalContext.prefs.edit().putBoolean("calibrated",true).putInt("calibration_version",SignalCalibration.VERSION).putInt("near_rssi",-70).putInt("far_rssi",-90).apply();
        AutoConfig.applyDoorSignalFix(signalContext);check(AutoConfig.near(signalContext)==-70&&AutoConfig.far(signalContext)==-90);
        ProfileCatalog c=new ProfileCatalog(null);
        JSONObject envelope=new JSONObject().put("fixture",true);
        JSONObject a=seed("1","account-A","room-1"),a2=seed("1","account-A","room-2"),b=seed("1","account-B","room-1"),otherOrg=seed("2","account-A","room-1");
        String idA=c.upsert(envelope,a,"A room 1");
        c.upsert(envelope,a2,"A room 2");c.upsert(envelope,b,"B room 1");c.upsert(envelope,otherOrg,"Other org");
        check(c.accounts().size()==3);check(c.rooms(ProfileCatalog.account(a)).size()==2);
        check(!ProfileCatalog.id(a).equals(ProfileCatalog.id(b)));check(!ProfileCatalog.id(a).equals(ProfileCatalog.id(otherOrg)));
        check(c.upsert(new JSONObject().put("renewed",true),a,"Updated").equals(idA));
        check(c.rooms(ProfileCatalog.account(a)).size()==2);check(c.find(idA).getJSONObject("envelope").getBoolean("renewed"));
        JSONObject copy=c.find(idA);copy.put("room","mutated");check(!c.find(idA).getString("room").equals("mutated"));
        ProfileCatalog restored=new ProfileCatalog(c.json());check(restored.find(idA).getString("room").equals("Updated"));
        restored.remove(idA);check(restored.rooms(ProfileCatalog.account(a)).size()==1);rejected(()->restored.find(idA));
        check(c.find(idA)!=null);
        JSONObject duplicate=c.json();duplicate.getJSONArray("profiles").put(duplicate.getJSONArray("profiles").getJSONObject(0));rejected(()->new ProfileCatalog(duplicate));
        rejected(()->new ProfileCatalog(new JSONObject().put("version",2).put("profiles",new JSONArray())));
        ProfileCatalog limit=new ProfileCatalog(null);
        for(int i=0;i<ProfileCatalog.LIMIT;i++)limit.upsert(envelope,seed("1","account-A","room-"+i),"Room");
        rejected(()->limit.upsert(envelope,seed("1","account-A","too-many"),"Room"));
        check(limit.upsert(envelope,a,"Renew at capacity").equals(ProfileCatalog.id(a)));
        JSONObject macCase=new JSONObject(a.toString()).put("MAC","02:ab:cd:00:00:01");
        check(ProfileCatalog.id(macCase).equals(ProfileCatalog.id(new JSONObject(macCase.toString()).put("MAC","02:AB:CD:00:00:01"))));
        check(TargetToken.matches("new","new"));check(!TargetToken.matches("new","old"));
        check(!TargetToken.matches("", ""));check(!TargetToken.matches(null,"x"));check(!TargetToken.matches("x",null));

        Security.addProvider(new Provider("ProfileFixture",1.0,"Software key, JVM only"){{
            put("KeyStore.AndroidKeyStore",TestKeyStore.class.getName());
        }});
        // Actual signed owner envelope is read only locally, never printed, modified or sent.
        JSONObject owner=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
        JSONObject signed=MigrationTrust.verify(owner);
        TestContext context=new TestContext();MemoryPrefs p=context.prefs;
        SecureStore.save(context,owner);
        check(SecureStore.load(context).getString("signature").equals(owner.getString("signature")));
        check(!p.getString("profiles_v1","").contains(owner.getString("payload")));
        check(SecureStore.catalog(context).accounts().size()==1);
        String selected=p.getString("profile_id",""),vault=p.getString("vault",""),token=SecureStore.capability(context);
        p.data.remove("profiles_v1");p.data.remove("profile_id");p.data.put("calibrated",true);
        SecureStore.ensureProfiles(context);
        check(p.getString("vault","").equals(vault));check(SecureStore.capability(context).equals(token));
        check(p.getBoolean("calibrated",false));check(p.getString("profile_id","").equals(selected));
        int commits=p.commits;SecureStore.ensureProfiles(context);check(p.commits==commits);
        p.data.put("last_result","old-result");p.data.put("events","old-events");p.data.put("scan_balanced",true);
        commits=p.commits;SecureStore.activate(context,owner,signed.getString("roomNo"));
        check(p.commits==commits+1);check(!p.data.containsKey("calibrated")&&!p.data.containsKey("last_result"));
        check(!p.data.containsKey("events")&&!p.data.containsKey("scan_balanced"));
        check(!SecureStore.matchesCapability(context,token));check(!p.getString("vault","").equals(vault));
        check(SecureStore.catalog(context).accounts().size()==1);
        Map<String,Object> before=new HashMap<String,Object>(p.data);
        JSONObject bad=new JSONObject(owner.toString()).put("signature","AAAA");
        rejected(()->SecureStore.activate(context,bad,"Do not select"));
        check(p.data.equals(before));
        String active=SecureStore.capability(context);
        SecureStore.removeProfile(context,selected);
        check(SecureStore.load(context)==null);check(SecureStore.catalog(context).accounts().isEmpty());
        check(p.getBoolean("personal_disabled",false));check(!SecureStore.matchesCapability(context,active));
        SecureStore.save(context,owner);check(!p.getBoolean("personal_disabled",true));
        active=SecureStore.capability(context);SecureStore.clear(context);
        check(SecureStore.load(context)==null);check(SecureStore.catalog(context).accounts().isEmpty());
        check(p.getBoolean("personal_disabled",false));check(!SecureStore.matchesCapability(context,active));
        SecureStore.save(context,owner);
        p.data.put("profiles_v1","broken encrypted catalogue");
        Map<String,Object> corrupt=new HashMap<String,Object>(p.data);
        rejected(()->SecureStore.ensureProfiles(context));check(p.data.equals(corrupt));
        TestContext failed=new TestContext();failed.prefs.fail=true;
        rejected(()->SecureStore.save(failed,owner)); // Android-style in-memory transaction can exist even when disk commit fails.
        check(SecureStore.load(failed)!=null);
        check(failed.prefs.getString("profile_id","").equals(ProfileCatalog.id(signed)));
        check(SecureStore.catalog(failed).find(ProfileCatalog.id(signed))!=null);
        System.out.println("{\"ok\":true,\"assertions\":"+checks+",\"networkUsed\":false,\"androidKeystoreRuntime\":\"NOT RUN\",\"scope\":\"production catalogue, SecureStore AES-GCM and target tokens; in-memory preferences and software KeyStore fixture\"}");
    }
}

