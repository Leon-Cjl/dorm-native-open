package cn.dormnative.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Pure catalogue of already verified account/room envelopes. Persist only encrypted. */
final class ProfileCatalog {
    static final int LIMIT=24;
    private final JSONArray rows;
    ProfileCatalog(JSONObject data)throws Exception{
        if(data==null){rows=new JSONArray();return;}
        if(data.optInt("version",0)!=1||data.optJSONArray("profiles")==null)throw new Exception("本机账号资料格式无法识别");
        rows=new JSONArray(data.getJSONArray("profiles").toString());
        if(rows.length()>LIMIT)throw new Exception("本机保存数量超过上限");
        java.util.HashSet<String> ids=new java.util.HashSet<String>();
        for(int i=0;i<rows.length();i++){
            JSONObject p=rows.getJSONObject(i);
            if(!p.getString("id").matches("[0-9a-f]{64}")||!p.getString("account").matches("[0-9a-f]{64}")||!ids.add(p.getString("id"))||p.optJSONObject("envelope")==null)throw new Exception("本机账号资料不完整");
        }
    }
    static String digest(JSONArray values)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(values.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    static String account(JSONObject seed)throws Exception{
        return digest(new JSONArray().put(seed.getString("companyID")).put(seed.getString("staffID")).put(seed.getString("personId")));
    }
    static String id(JSONObject seed)throws Exception{
        return digest(new JSONArray().put(account(seed)).put(seed.getString("roomID")).put(seed.getString("deviceID")).put(seed.getString("MAC").toUpperCase(Locale.ROOT)));
    }
    String upsert(JSONObject envelope,JSONObject verifiedSeed,String room)throws Exception{
        String id=id(verifiedSeed),staff=verifiedSeed.getString("staffID");
        JSONObject p=new JSONObject().put("id",id).put("account",account(verifiedSeed)).put("hint","尾号 "+staff.substring(Math.max(0,staff.length()-4)))
            .put("room",room).put("envelope",new JSONObject(envelope.toString()));
        for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id)){rows.put(i,p);return id;}
        if(rows.length()>=LIMIT)throw new Exception("最多保存 24 份宿舍授权，请先移除不再使用的宿舍");
        rows.put(p);return id;
    }
    JSONObject find(String id)throws Exception{
        for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return new JSONObject(rows.getJSONObject(i).toString());
        throw new Exception("该宿舍已被移除，请重新选择");
    }
    void remove(String id)throws Exception{
        find(id);
        for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id)){rows.remove(i);return;}
    }
    List<JSONObject> rooms(String account)throws Exception{
        List<JSONObject> out=new ArrayList<JSONObject>();
        for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);if(account.equals(p.getString("account")))out.add(new JSONObject(p.toString()));}
        return out;
    }
    List<JSONObject> accounts()throws Exception{
        LinkedHashMap<String,JSONObject> out=new LinkedHashMap<String,JSONObject>();
        for(int i=0;i<rows.length();i++){
            JSONObject p=rows.getJSONObject(i);String key=p.getString("account");
            if(!out.containsKey(key))out.put(key,new JSONObject().put("id",key).put("label","账号 "+(out.size()+1)+" · "+p.getString("hint")).put("count",0));
            JSONObject a=out.get(key);a.put("count",a.getInt("count")+1);
        }
        return new ArrayList<JSONObject>(out.values());
    }
    JSONObject json()throws Exception{return new JSONObject().put("version",1).put("profiles",new JSONArray(rows.toString()));}
}

