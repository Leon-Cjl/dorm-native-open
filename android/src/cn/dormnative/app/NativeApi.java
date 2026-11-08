package cn.dormnative.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

/** Queries only the imported user's existing binding and access. Never enrolls or edits users. */
final class NativeApi {
    static final String APP_ID="wx5ab2a45f1eba1dcf";
    private static final String[] HOSTS={"https://gy.bostex.net:5500","https://applets.bostex.net"};
    private final JSONObject seed;
    private volatile int hostIndex;
    static final class Grant {
        final String company,staff,roomId,deviceId,roomLabel,mac,sn;
        final long personId,checkedAt;
        final String effective,expiry;
        Grant(String c,String s,long person,JSONObject room,long now) throws Exception {
            company=c; staff=s; personId=person; checkedAt=now;effective=room.optString("effectiveTime","");expiry=room.optString("expiryTime","");
            roomId=required(room,"roomID"); deviceId=required(room,"deviceID");
            roomLabel=required(room,"roomNo"); mac=room.optString("MAC","").toUpperCase(Locale.ROOT); sn=room.optString("sn","");
        }
        boolean usable(long now){return now>=checkedAt&&now-checkedAt<=15000&&validPeriod(effective,expiry,now);}
    }
    NativeApi(JSONObject envelope) throws Exception { this.seed=MigrationTrust.verify(envelope); }
    private static String required(JSONObject o,String k) throws Exception {
        String s=o.optString(k,"").trim(); if(s.isEmpty() || s.equals("null") || s.length()>180) throw new Exception("官方房间资料不完整："+k); return s;
    }
    private JSONObject post(String route,JSONObject body) throws Exception {
        if(Thread.currentThread().isInterrupted()) throw new Exception("查询已取消");
        Exception failure=null;
        for(int i=0;i<HOSTS.length;i++) {
            int index=(hostIndex+i)%HOSTS.length;
            try { JSONObject r=request(HOSTS[index]+route,body); if(Thread.currentThread().isInterrupted()) throw new Exception("查询已取消"); hostIndex=index; return r; }
            catch(Exception e) { failure=e; }
        }
        throw new Exception("无法完成官方授权查询；未发送开锁命令",failure);
    }
    private JSONObject request(String address,JSONObject body) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec("1234567890abcdef".getBytes(StandardCharsets.UTF_8),"AES"),new IvParameterSpec("abcdef1234567890".getBytes(StandardCharsets.UTF_8)));
        byte[] bytes=new JSONObject().put("data",Base64.encodeToString(cipher.doFinal(body.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP)).toString().getBytes(StandardCharsets.UTF_8);
        HttpsURLConnection conn=(HttpsURLConnection)new URL(address).openConnection();
        conn.setConnectTimeout(4500); conn.setReadTimeout(4500); conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST"); conn.setDoOutput(true); conn.setRequestProperty("Content-Type","application/json"); conn.setRequestProperty("language","zh-CN"); conn.setFixedLengthStreamingMode(bytes.length);
        // System certificate and hostname validation remain enabled.
        try {
            try(OutputStream out=conn.getOutputStream()) { out.write(bytes); }
            if(conn.getResponseCode()!=200) throw new Exception("官方接口未接受请求");
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()) {
                byte[] buffer=new byte[4096]; int count;
                while((count=in.read(buffer))!=-1) { if(out.size()+count>524288) throw new Exception("官方响应过大"); out.write(buffer,0,count); }
            }
            JSONObject reply=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            if(reply.has("code")) {
                if(!"200".equals(reply.optString("code"))) throw new Exception("官方接口未确认授权");
                Object value=reply.has("body")?reply.get("body"):reply.opt("data");
                if(value instanceof JSONObject) return (JSONObject)value;
                if(value instanceof JSONArray) return new JSONObject().put("data",value);
                throw new Exception("官方接口没有返回所需授权资料");
            }
            return reply;
        } finally { conn.disconnect(); }
    }
    private JSONObject company(String route,JSONObject body) throws Exception {
        return post("/wxProgram/system/companyEncryption",new JSONObject().put("companyID",seed.getString("companyID")).put("url",route).put("body",body).put("method","POST"));
    }
    List<Grant> grants() throws Exception {
        String company=seed.getString("companyID"), staff=seed.getString("staffID");
        if("10051".equals(company))throw new Exception("此组织存在额外开锁策略，尚未接入原生版");
        List<java.util.concurrent.Callable<JSONArray>> queries=new ArrayList<java.util.concurrent.Callable<JSONArray>>();
        queries.add(()->company("/staff/getStaff",new JSONObject().put("dataType","simple").put("staffID",staff)).optJSONArray("staff"));
        queries.add(()->company("/1075/getStaffRole",new JSONObject().put("staffNo",staff)).optJSONArray("data"));
        queries.add(()->post("/wxProgram/getCompanyListEncryption",new JSONObject().put("enable",1).put("companyID",company)).optJSONArray("data"));
        queries.add(()->company("/1081/getAccess",new JSONObject().put("staff",new JSONArray().put(new JSONObject().put("staffNo",staff))).put("dataType","simple-device-control")).optJSONArray("data"));
        List<JSONArray> replies=ParallelQueries.run(queries,12000);
        JSONArray people=replies.get(0);
        JSONObject person=null;
        if(people!=null) for(int i=0;i<people.length();i++) {
            JSONObject p=people.optJSONObject(i); if(p!=null && staff.equals(p.optString("staffID"))) { if(person!=null) throw new Exception("人员记录不唯一"); person=p; }
        }
        if(person==null) throw new Exception("官方账号没有返回本人资料");
        long personId; try { personId=Long.parseLong(person.optString("uniqueID")); Protocol.unlock(personId); } catch(Exception e) { throw new Exception("人员授权标识格式未适配"); }
        if(!String.valueOf(personId).equals(seed.getString("personId"))) throw new Exception("人员标识已变更，请重新从本人账号导入");
        JSONArray roles=replies.get(1);
        if(roles==null || roles.length()==0 || roles.getJSONObject(0).optInt("roleType",-1)!=0) throw new Exception("当前首版只接入学生本人权限，未发送开锁命令");
        JSONArray orgs=replies.get(2);
        JSONObject org=null;
        if(orgs!=null) for(int i=0;i<orgs.length();i++) { JSONObject o=orgs.optJSONObject(i); if(o!=null && company.equals(o.optString("companyID"))) org=o; }
        if(org==null) throw new Exception("当前组织权限未返回");
        // v53 contains a separate timed student branch for this organization. Do not silently bypass it.
        if("10051".equals(company)) throw new Exception("此组织存在额外开锁策略，尚未接入原生版");
        JSONArray rooms=replies.get(3);
        return selectGrants(seed,rooms,personId,System.currentTimeMillis());
    }
    /** Pure response selection, shared by production and offline regression of the observed owner row. */
    static List<Grant> selectGrants(JSONObject seed,JSONArray rooms,long personId,long now) throws Exception {
        if(rooms==null)throw new Exception("[AUTH_LIST] 后台未返回本人房间列表");
        String company=seed.getString("companyID"),staff=seed.getString("staffID");
        List<Grant> result=new ArrayList<Grant>();int matches=0;
        String reason="[AUTH_BINDING] 后台未返回与当前绑定一致的本人房间和设备";
        for(int i=0;i<rooms.length();i++) {
            JSONObject room=rooms.optJSONObject(i);
            if(room==null||!seed.getString("roomID").equals(room.optString("roomID","").trim())||!seed.getString("deviceID").equals(room.optString("deviceID","").trim()))continue;
            matches++;
            if(room.optInt("eStatus",1)==1){reason="[AUTH_DISABLED] 本人房间授权已停用或授权状态缺失";continue;}
            // The observed /getAccess response omits dStatus. v53 excludes only explicit 3.
            // Preserve rejection for an explicit disabled or malformed value; absence is optional.
            if(room.has("dStatus")&&room.optInt("dStatus",3)==3){reason="[DEVICE_DISABLED] 门锁设备已停用或设备状态无法识别";continue;}
            JSONObject control=room.optJSONObject("control"); boolean enabled=false;
            if(control!=null) for(String mode:new String[]{"card","face","fingerprint","pw","qr"}) enabled|=control.optInt(mode,0)==1;
            if(!enabled){reason="[AUTH_CONTROL] 本人房间未返回可用蓝牙开锁权限";continue;}
            if(!validPeriod(room.optString("effectiveTime"),room.optString("expiryTime"),now)){reason="[AUTH_PERIOD] 授权不在有效期内，或有效期格式未适配";continue;}
            if(!Protocol.validMac(room.optString("MAC",""))){reason="[DEVICE_ADDRESS] 门锁未返回可直连地址，可能需要序列号扫描适配";continue;}
            if(!seed.getString("MAC").equalsIgnoreCase(room.optString("MAC",""))){reason="[DEVICE_CHANGED] 后台门锁地址与原绑定不同，需要重新核对本人设备";continue;}
            Grant g=new Grant(company,staff,personId,room,now);result.add(g);
        }
        if(matches>1)throw new Exception("[AUTH_DUPLICATE] 本人房间设备记录不唯一，请先核对后台资料");
        if(result.isEmpty())throw new Exception(reason);
        return result;
    }
    String deviceSummary() throws Exception {
        Grant g=grants().get(0);
        JSONArray data=company("/1077/getDevice",new JSONObject().put("dataType","simple-basic").put("deviceID",g.deviceId)).optJSONArray("data");
        JSONObject device=null;
        if(data!=null)for(int i=0;i<data.length();i++){
            JSONObject d=data.optJSONObject(i);if(d==null)continue;
            String id=d.optString("id",d.optString("deviceID",""));
            if(g.deviceId.equals(id)&&g.mac.equalsIgnoreCase(d.optString("MAC",""))){if(device!=null)throw new Exception("设备记录不唯一");device=d;}
        }
        if(device==null)throw new Exception("后台未返回可核对的本人门锁资料");
        int battery=device.optInt("batteryLevel",-1);if(battery>100)battery-=100;
        String power=battery>=0&&battery<=100?battery+"%":"未返回";
        return "电量："+power+"\n后台通信状态："+(device.has("commStatus")?(device.optInt("commStatus",-1)==1?"在线":"离线"):"未返回")+"\n\n这是后台资料，可能有延迟；不代表门已打开或锁舌状态。未更改门锁设置。";
    }
    static boolean validPeriod(String start,String end,long now) {
        if(start.isEmpty() && end.isEmpty()) return true;
        Long a=parseDate(start), b=parseDate(end); return a!=null && b!=null && a<=now && now<=b;
    }
    private static Long parseDate(String value) {
        for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd'T'HH:mm:ss","yyyy/MM/dd HH:mm:ss"}) {
            SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.ROOT); f.setLenient(false); f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            ParsePosition p=new ParsePosition(0); Date date=f.parse(value,p); if(date!=null && p.getIndex()==value.length()) return date.getTime();
        }
        return null;
    }
}
