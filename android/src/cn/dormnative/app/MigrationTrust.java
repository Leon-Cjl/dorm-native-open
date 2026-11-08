package cn.dormnative.app;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import android.util.Base64;
import org.json.JSONObject;
/** Local migration signature, not a vendor certificate or a new lock enrollment. */
final class MigrationTrust {
 private static final String PUBLIC_KEY="MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAwTMITEpLrtODhDK6S6Fobezy+q3pbszVadDGMxMWB411CqhgFIfxpW3ngq88CavDfAP6Pzasc4+VBpWh+rYscy9B5HnCYt89bcD09Sp5+eo5YvEVlEe8xIp74xSw5W9NoWgzV8ZqRFytAot9jg09P3J32mZHETlgsIXflfeVzW7WoJyYpDX9SpwlYImYzKhOzzF0g+FZQpUToxU3a+a1yDoYyp38TfDAGeqUTGOgnfJIe6xBhNlynSSJmeYJRMx1ufzsLPCq57oZI2FeDT7b+3+zXbWlfzsGa5HmRcrjd7ASzC0+59HMt+SQ+Xx9OzboCyUf/D387ttd5NWOWLU2Cgq97nmzkloKW4kPCexiDgym0WhnZRbcfTwOj6QiNk74z5ZlcMiHVTmGLQ0r8mNjjIWWKr3kPF3KBTlh2f8MmaFh0A96hm0+5spOtt4WOzMXnCRKEY/ddEhd9NFUbnBJaGkNyJKe3vA7spK0ItsDL9c3O/U+7N0MApdEsqLD3/AdAgMBAAE=";
 static JSONObject verify(JSONObject envelope) throws Exception {
  if(!"dorm-native-signed/1".equals(envelope.optString("schema"))) throw new Exception("不是已核对的本人授权文件");
  byte[] bytes=Base64.decode(envelope.getString("payload"),Base64.NO_WRAP);
  if(bytes.length>16384) throw new Exception("授权文件过大");
  Signature v=Signature.getInstance("SHA256withRSA");
  v.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY,Base64.NO_WRAP))));
  v.update(bytes);
  if(!v.verify(Base64.decode(envelope.getString("signature"),Base64.NO_WRAP))) throw new Exception("授权文件签名不匹配");
  JSONObject s=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
  if(!"dorm-native-migration/1".equals(s.optString("schema")) || !"wx5ab2a45f1eba1dcf".equals(s.optString("appId")) ||
     !"official-pc-visible-session".equals(s.optString("source"))) throw new Exception("授权来源或类型不匹配");
  for(String k:new String[]{"companyID","staffID","personId","roomID","deviceID","roomNo","MAC"})
   if(s.optString(k,"").isEmpty() || s.optString(k).length()>180) throw new Exception("授权文件缺少必要字段");
  if(!s.getString("companyID").matches("[0-9]{1,12}") || !Protocol.validMac(s.getString("MAC"))) throw new Exception("授权字段格式未适配");
  Protocol.unlock(Long.parseLong(s.getString("personId")));
  return s;
 }
}
