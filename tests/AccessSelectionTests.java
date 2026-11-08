package cn.dormnative.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local-only regression of production selection with the captured authorized owner response. */
public final class AccessSelectionTests {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
    static JSONArray one(JSONObject value){return new JSONArray().put(value);}
    static JSONObject seed,row;static long person,now;
    static void rejected(JSONObject value,String code)throws Exception{
        try{NativeApi.selectGrants(seed,one(value),person,now);throw new AssertionError("Accepted "+code);}
        catch(AssertionError e){throw e;}catch(Exception e){check(e.getMessage()!=null&&e.getMessage().startsWith(code),code);}
    }
    public static void main(String[] args)throws Exception{
        // Refuse all network activity: these tests exercise parsing and filtering only.
        java.net.URL.setURLStreamHandlerFactory(protocol->new java.net.URLStreamHandler(){
            protected java.net.URLConnection openConnection(java.net.URL u){throw new AssertionError("Network forbidden in regression");}
        });
        JSONObject envelope=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
        seed=MigrationTrust.verify(envelope);person=Long.parseLong(seed.getString("personId"));
        JSONObject capture=new JSONObject(new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8));
        row=capture.getJSONObject("room");now=java.time.Instant.parse(capture.getString("observedAt")).toEpochMilli();
        check(!row.has("dStatus"),"Captured row omits optional dStatus");
        check(row.optInt("dStatus",3)==3,"Old filter reproduces false device-disabled rejection");
        check(NativeApi.selectGrants(seed,one(row),person,now).size()==1,"Actual captured row accepted after fix");
        JSONObject r=copy(row).put("dStatus",0);check(NativeApi.selectGrants(seed,one(r),person,now).size()==1,"Explicit active status accepted");
        r=copy(row).put("dStatus",3);rejected(r,"[DEVICE_DISABLED]");
        r=copy(row).put("dStatus","3");rejected(r,"[DEVICE_DISABLED]");
        r=copy(row).put("dStatus","unknown");rejected(r,"[DEVICE_DISABLED]");
        r=copy(row).put("dStatus",JSONObject.NULL);rejected(r,"[DEVICE_DISABLED]");
        r=copy(row).put("eStatus",1);rejected(r,"[AUTH_DISABLED]");
        r=copy(row);r.remove("eStatus");rejected(r,"[AUTH_DISABLED]");
        r=copy(row).put("control",new JSONObject());rejected(r,"[AUTH_CONTROL]");
        r=copy(row);r.remove("control");rejected(r,"[AUTH_CONTROL]");
        r=copy(row).put("effectiveTime","2000-01-01 00:00:00").put("expiryTime","2000-01-02 00:00:00");rejected(r,"[AUTH_PERIOD]");
        r=copy(row).put("effectiveTime","2099-01-01 00:00:00").put("expiryTime","2099-02-01 00:00:00");rejected(r,"[AUTH_PERIOD]");
        r=copy(row).put("effectiveTime","bad");rejected(r,"[AUTH_PERIOD]");
        r=copy(row).put("MAC","");rejected(r,"[DEVICE_ADDRESS]");
        r=copy(row).put("MAC","00:00:00:00:00:00");rejected(r,"[DEVICE_ADDRESS]");
        r=copy(row).put("MAC","02:00:00:00:00:01");rejected(r,"[DEVICE_CHANGED]");
        r=copy(row).put("roomID","fixture-other-room");rejected(r,"[AUTH_BINDING]");
        r=copy(row).put("deviceID","fixture-other-device");rejected(r,"[AUTH_BINDING]");
        try{NativeApi.selectGrants(seed,new JSONArray().put(row).put(copy(row)),person,now);throw new AssertionError("Duplicate accepted");}catch(AssertionError e){throw e;}catch(Exception e){check(e.getMessage().startsWith("[AUTH_DUPLICATE]"),"Duplicate rejected");}
        try{NativeApi.selectGrants(seed,null,person,now);throw new AssertionError("Missing list accepted");}catch(AssertionError e){throw e;}catch(Exception e){check(e.getMessage().startsWith("[AUTH_LIST]"),"Missing list rejected");}
        System.out.println("{\"ok\":true,\"assertions\":"+checks+",\"oldBugReproduced\":true,\"capturedOwnerRowAccepted\":true,\"explicitDenialsPreserved\":true,\"networkUsed\":false,\"lockCommandSent\":false}");
    }
}
