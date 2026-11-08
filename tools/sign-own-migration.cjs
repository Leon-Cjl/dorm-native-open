const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'), priv=path.join(root,'private');
const payload=fs.readFileSync(path.join(priv,'verified-own-migration.json'),'utf8');
const item=JSON.parse(payload);
if(item.schema!=='dorm-native-migration/1' || item.source!=='official-pc-visible-session') throw Error('Not verified own migration');
const keyFile=path.join(priv,'local-migration-signing.pem');
if(!fs.existsSync(keyFile)){
 const pair=crypto.generateKeyPairSync('rsa',{modulusLength:3072,privateKeyEncoding:{type:'pkcs8',format:'pem'},publicKeyEncoding:{type:'spki',format:'pem'}});
 fs.writeFileSync(keyFile,pair.privateKey);
}
const key=fs.readFileSync(keyFile),pub=crypto.createPublicKey(key).export({format:'der',type:'spki'}).toString('base64');
const envelope={schema:'dorm-native-signed/1',payload:Buffer.from(payload).toString('base64'),signature:crypto.sign('sha256',Buffer.from(payload),key).toString('base64')};
fs.writeFileSync(path.join(priv,'本人宿舍授权.dormkey'),JSON.stringify(envelope));
const source=`package cn.dormnative.app;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import android.util.Base64;
import org.json.JSONObject;
/** Local migration signature, not a vendor certificate or a new lock enrollment. */
final class MigrationTrust {
 private static final String PUBLIC_KEY="${pub}";
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
`;
fs.writeFileSync(path.join(root,'android','src','cn','dormnative','app','MigrationTrust.java'),source);
console.log(JSON.stringify({ok:true,signatureVerified:crypto.verify('sha256',Buffer.from(payload),crypto.createPublicKey(key),Buffer.from(envelope.signature,'base64')),privateValuesInApkSource:false}));
