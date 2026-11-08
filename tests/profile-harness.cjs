// Compile actual Android SDK JSON sources + production NativeApi, without Android or network.
// Annotation stubs and Base64 adapter only; no mock JSON semantics or mock grant selector.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'),sdk=path.join(process.env.LOCALAPPDATA,'Android/Sdk/sources/android-37.0/org/json');
const out=path.join(root,'build','profiles-fixture-'+Date.now());fs.mkdirSync(out,{recursive:true});
const stubs={
 'android/compat/annotation/UnsupportedAppUsage.java':'package android.compat.annotation;public @interface UnsupportedAppUsage {}',
 'android/annotation/SystemApi.java':'package android.annotation;public @interface SystemApi {Client client();public enum Client {MODULE_LIBRARIES}}',
 'libcore/util/NonNull.java':'package libcore.util;@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE,java.lang.annotation.ElementType.PARAMETER,java.lang.annotation.ElementType.METHOD,java.lang.annotation.ElementType.FIELD}) public @interface NonNull {}',
 'libcore/util/Nullable.java':'package libcore.util;@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE,java.lang.annotation.ElementType.PARAMETER,java.lang.annotation.ElementType.METHOD,java.lang.annotation.ElementType.FIELD}) public @interface Nullable {}',
 'android/util/Base64.java':'package android.util;public final class Base64 {public static final int NO_WRAP=2,URL_SAFE=8;public static byte[] decode(String s,int flags){return java.util.Base64.getDecoder().decode(s);}public static String encodeToString(byte[] b,int flags){return ((flags&8)!=0?java.util.Base64.getUrlEncoder():java.util.Base64.getEncoder()).encodeToString(b);}}'
};
Object.assign(stubs,{
'android/content/SharedPreferences.java':'package android.content; public interface SharedPreferences {String getString(String k,String d);boolean getBoolean(String k,boolean d);int getInt(String k,int d);Editor edit();interface Editor{Editor putString(String k,String v);Editor putBoolean(String k,boolean v);Editor putInt(String k,int v);Editor remove(String k);boolean commit();void apply();}}',
'android/content/Context.java':'package android.content;public abstract class Context {public static final int MODE_PRIVATE=0;public abstract SharedPreferences getSharedPreferences(String n,int m);}',
'android/security/keystore/KeyProperties.java':'package android.security.keystore;public class KeyProperties {public static final String KEY_ALGORITHM_AES="AES",BLOCK_MODE_GCM="GCM",ENCRYPTION_PADDING_NONE="NoPadding";public static final int PURPOSE_ENCRYPT=1,PURPOSE_DECRYPT=2;}',
'android/security/keystore/KeyGenParameterSpec.java':'package android.security.keystore;public class KeyGenParameterSpec implements java.security.spec.AlgorithmParameterSpec {public static class Builder {public Builder(String a,int p){} public Builder setBlockModes(String...s){return this;}public Builder setEncryptionPaddings(String...s){return this;}public KeyGenParameterSpec build(){return new KeyGenParameterSpec();}}}'
});
const sources=[];for(const [file,content]of Object.entries(stubs)){const p=path.join(out,file);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,content);sources.push(p);}
sources.push(...fs.readdirSync(sdk).filter(f=>f.endsWith('.java')).map(f=>path.join(sdk,f)));
for(const f of ['AutoConfig.java','ProximityGate.java','SignalCalibration.java','SecureStore.java','ProfileCatalog.java','TargetToken.java','MigrationTrust.java','Protocol.java'])sources.push(path.join(root,'android/src/cn/dormnative/app',f));
sources.push(path.join(root,'tests/ProfileTests.java'));

const jdk='D:/APPs/Android Studio/jbr/bin/';
const compile=cp.spawnSync(jdk+'javac.exe',['-encoding','UTF-8','--release','8','-d',out,...sources],{encoding:'utf8'});
if(compile.status!==0)throw Error(compile.stderr);

const run=cp.spawnSync(jdk+'java.exe',['-cp',out,'cn.dormnative.app.ProfileTests',path.join(root,'private/本人宿舍授权.dormkey')],{encoding:'utf8',timeout:30000});
if(run.status!==0)throw Error(run.stderr);
const result=JSON.parse(run.stdout);result.sources={};
for(const f of ['AutoConfig.java','ProximityGate.java','SignalCalibration.java','SecureStore.java','ProfileCatalog.java','TargetToken.java','MainActivity.java','AutoService.java','AutoActivity.java','QuickTileService.java','PersonalBootstrap.java']){
 result.sources[f]=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'android/src/cn/dormnative/app',f))).digest('hex');
}
result.deviceRuntime='NOT RUN';
fs.writeFileSync(path.join(root,'dist/profile-tests.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
