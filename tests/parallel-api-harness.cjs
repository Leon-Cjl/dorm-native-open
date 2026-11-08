// Compile actual Android SDK JSON sources + production NativeApi, without Android or network.
// Annotation stubs and Base64 adapter only; no mock JSON semantics or mock grant selector.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..'),sdk=path.join(process.env.LOCALAPPDATA,'Android/Sdk/sources/android-37.0/org/json');
const out=path.join(root,'build','parallel-api-fixture-'+Date.now());fs.mkdirSync(out,{recursive:true});
const stubs={
 'android/compat/annotation/UnsupportedAppUsage.java':'package android.compat.annotation;public @interface UnsupportedAppUsage {}',
 'android/annotation/SystemApi.java':'package android.annotation;public @interface SystemApi {Client client();public enum Client {MODULE_LIBRARIES}}',
 'libcore/util/NonNull.java':'package libcore.util;@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE,java.lang.annotation.ElementType.PARAMETER,java.lang.annotation.ElementType.METHOD,java.lang.annotation.ElementType.FIELD}) public @interface NonNull {}',
 'libcore/util/Nullable.java':'package libcore.util;@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE,java.lang.annotation.ElementType.PARAMETER,java.lang.annotation.ElementType.METHOD,java.lang.annotation.ElementType.FIELD}) public @interface Nullable {}',
 'android/util/Base64.java':'package android.util;public final class Base64 {public static final int NO_WRAP=2;public static byte[] decode(String s,int flags){return java.util.Base64.getDecoder().decode(s);}public static String encodeToString(byte[] b,int flags){return java.util.Base64.getEncoder().encodeToString(b);}}'
};
const sources=[];for(const [file,content]of Object.entries(stubs)){const p=path.join(out,file);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,content);sources.push(p);}
sources.push(...fs.readdirSync(sdk).filter(f=>f.endsWith('.java')).map(f=>path.join(sdk,f)));
for(const f of ['ParallelQueries.java','NativeApi.java','MigrationTrust.java','Protocol.java'])sources.push(path.join(root,'android/src/cn/dormnative/app',f));
sources.push(path.join(root,'tests/ParallelApiTests.java'));
sources.push(path.join(root,'tests/NativeApiReadOnlyCheck.java'));
const jdk='D:/APPs/Android Studio/jbr/bin/';
const compile=cp.spawnSync(jdk+'javac.exe',['-encoding','UTF-8','--release','8','-d',out,...sources],{encoding:'utf8'});
if(compile.status!==0)throw Error(compile.stderr);
const capture=path.join(root,'private/access-row-diagnostic.json');
const run=cp.spawnSync(jdk+'java.exe',['-cp',out,'cn.dormnative.app.ParallelApiTests',path.join(root,'private/本人宿舍授权.dormkey'),capture],{encoding:'utf8',timeout:60000});
if(run.status!==0)throw Error(run.stderr);
const result=JSON.parse(run.stdout);result.nativeApiSha256=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'android/src/cn/dormnative/app/NativeApi.java'))).digest('hex');
result.deviceRuntime='NOT RUN';result.realDoorUnlock='NOT RUN';
fs.writeFileSync(path.join(root,'dist/parallel-api-tests.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
