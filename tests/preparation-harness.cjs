// Production AccessPreparation with deterministic account/network stand-ins. No network/phone access.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build','prepare-fixture-'+Date.now());
const src={
'android/os/SystemClock.java':`package android.os;public class SystemClock{public static volatile long now;public static long elapsedRealtime(){return now;}}`,
'org/json/JSONObject.java':`package org.json;public class JSONObject{}`,
'android/content/Context.java':`package android.content;public class Context{public Context getApplicationContext(){return this;}}`,
'cn/dormnative/app/SecureStore.java':`package cn.dormnative.app;final class SecureStore{static volatile String token="A";static Prefs prefs(android.content.Context c){return new Prefs();}static class Prefs{String getString(String k,String d){return "encrypted";}}static String capability(android.content.Context c){return token;}static boolean matchesCapability(android.content.Context c,String v){return token.equals(v);}static org.json.JSONObject load(android.content.Context c){return new org.json.JSONObject();}}`,
'cn/dormnative/app/NativeApi.java':`package cn.dormnative.app;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
final class NativeApi{
 static AtomicInteger calls=new AtomicInteger();static volatile boolean fail,valid=true;
 static volatile CountDownLatch entered,release;
 static class Grant{boolean usable(long t){return valid;}}
 NativeApi(org.json.JSONObject o){}
 java.util.List<Grant> grants()throws Exception{
 calls.incrementAndGet();CountDownLatch e=entered,r=release;if(e!=null)e.countDown();if(r!=null)r.await();
 if(fail)throw new Exception("fixture denial");
 return java.util.Collections.singletonList(new Grant());
 }
}`,
'cn/dormnative/app/PreparationTests.java':`package cn.dormnative.app;
import java.util.concurrent.*;import java.lang.reflect.*;
public class PreparationTests{
 static int checks;static android.content.Context c=new android.content.Context();
 static void check(boolean b){checks++;if(!b)throw new AssertionError("prepare "+checks);}
 static FutureTask<?> task()throws Exception{Field f=AccessPreparation.class.getDeclaredField("flight");f.setAccessible(true);return (FutureTask<?>)f.get(null);}
 static void done()throws Exception{task().get(2,TimeUnit.SECONDS);synchronized(AccessPreparation.class){}}
 static void advance(long v){android.os.SystemClock.now+=v;}
 static void reset()throws Exception{
  if(task()!=null)task().cancel(true);
  for(String name:new String[]{"flight","target"}){Field f=AccessPreparation.class.getDeclaredField(name);f.setAccessible(true);f.set(null,name.equals("flight")?null:"");}
  NativeApi.calls.set(0);NativeApi.fail=false;NativeApi.valid=true;NativeApi.entered=null;NativeApi.release=null;SecureStore.token="A";advance(100000);
 }
 public static void main(String[] a)throws Exception{
  reset();check(!AccessPreparation.ready(c));check(AccessPreparation.take(c)!=null);check(NativeApi.calls.get()==1);
  check(AccessPreparation.take(c)!=null);check(NativeApi.calls.get()==2);
  reset();AccessPreparation.warm(c);done();check(NativeApi.calls.get()==1);check(AccessPreparation.take(c)!=null);check(NativeApi.calls.get()==1);
  reset();AccessPreparation.warm(c);done();advance(13000);check(AccessPreparation.take(c)!=null);check(NativeApi.calls.get()==2);
  reset();AccessPreparation.warm(c);done();check(AccessPreparation.ready(c));advance(11000);check(AccessPreparation.ready(c));advance(1001);check(!AccessPreparation.ready(c));
  reset();AccessPreparation.warm(c);done();SecureStore.token="B";AccessPreparation.take(c);check(NativeApi.calls.get()==2);
  reset();NativeApi.entered=new CountDownLatch(1);NativeApi.release=new CountDownLatch(1);
  AccessPreparation.warm(c);check(NativeApi.entered.await(2,TimeUnit.SECONDS));AccessPreparation.warm(c);check(NativeApi.calls.get()==1);
  NativeApi.release.countDown();done();check(AccessPreparation.take(c)!=null);check(NativeApi.calls.get()==1);
  reset();NativeApi.fail=true;boolean denied=false;try{AccessPreparation.take(c);}catch(Exception e){denied=true;}check(denied);
  NativeApi.fail=false;check(AccessPreparation.take(c)!=null);check(NativeApi.calls.get()==2);
  reset();NativeApi.valid=false;denied=false;try{AccessPreparation.take(c);}catch(Exception e){denied=true;}check(denied);
  reset();NativeApi.entered=new CountDownLatch(1);NativeApi.release=new CountDownLatch(1);
  AccessPreparation.warm(c);check(NativeApi.entered.await(2,TimeUnit.SECONDS));SecureStore.token="B";NativeApi.release.countDown();
  denied=false;try{done();}catch(Exception e){denied=true;}check(denied);AccessPreparation.take(c);check(NativeApi.calls.get()==2);
  System.out.println("{\\"ok\\":true,\\"assertions\\":"+checks+",\\"scope\\":\\"production one-use preparation with fake API and account clock; no network\\"}");
 }
}`
};
fs.mkdirSync(out,{recursive:true});const files=[];
for(const [name,content] of Object.entries(src)){const p=path.join(out,name);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,content);files.push(p);}
files.push(path.join(root,'android/src/cn/dormnative/app/AccessPreparation.java'));
const javac=process.env.JAVAC||'javac',java=process.env.JAVA||'java';
const build=cp.spawnSync(javac,['-encoding','UTF-8','--release','8','-d',out,...files],{encoding:'utf8'});
if(build.status!==0)throw Error(build.stderr);
const run=cp.spawnSync(java,['-cp',out,'cn.dormnative.app.PreparationTests'],{encoding:'utf8',timeout:10000});
if(run.status!==0)throw Error(run.stderr);
const result=JSON.parse(run.stdout);fs.writeFileSync(path.join(root,'dist/preparation-tests.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));

