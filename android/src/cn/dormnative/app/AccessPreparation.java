package cn.dormnative.app;

import android.content.Context;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import org.json.JSONObject;

/** One-use, short-lived preparation. No disk cache, offline fallback or periodic polling. */
final class AccessPreparation {
    static final long REUSE=12000;
    private static String target="";
    private static FutureTask<NativeApi.Grant> flight;
    private static long began,finished;
    private static boolean consumed;
    static synchronized void warm(Context c){
        if(SecureStore.prefs(c).getString("vault","").isEmpty())return;
        String key=SecureStore.capability(c);
        long now=android.os.SystemClock.elapsedRealtime();
        if(key.equals(target)&&flight!=null&&!consumed&&(!flight.isDone()||now-finished<REUSE))return;
        begin(c,key);
    }
    private static void begin(Context c,String key){
        if(flight!=null&&!flight.isDone())flight.cancel(true);
        target=key;began=android.os.SystemClock.elapsedRealtime();finished=0;consumed=false;
        final Context app=c.getApplicationContext();
        FutureTask<NativeApi.Grant> task=new FutureTask<NativeApi.Grant>(()->{
            if(!SecureStore.matchesCapability(app,key))throw new Exception("宿舍已切换");
            JSONObject envelope=SecureStore.load(app);if(envelope==null)throw new Exception("请先选择宿舍");
            NativeApi.Grant g=new NativeApi(envelope).grants().get(0);
            if(!SecureStore.matchesCapability(app,key))throw new Exception("宿舍已切换");
            return g;
        }){protected void done(){synchronized(AccessPreparation.class){if(flight==this)finished=android.os.SystemClock.elapsedRealtime();}}};
        flight=task;Thread worker=new Thread(task,"door-prepare");worker.setDaemon(true);worker.start();
    }
    static NativeApi.Grant take(Context c)throws Exception{
        FutureTask<NativeApi.Grant> task;String key=SecureStore.capability(c);
        synchronized(AccessPreparation.class){
            long now=android.os.SystemClock.elapsedRealtime();
            if(flight==null||!key.equals(target)||consumed||(flight.isDone()?now-finished>REUSE:now-began>25000))begin(c,key);
            task=flight;consumed=true;
        }
        try{
            NativeApi.Grant g=task.get(25,TimeUnit.SECONDS);
            if(!SecureStore.matchesCapability(c,key)||!g.usable(System.currentTimeMillis()))throw new Exception("本次准备已失效，请重新尝试");
            return g;
        }catch(ExecutionException e){Throwable cause=e.getCause();throw new Exception(cause==null?"开门准备失败":cause.getMessage());}
    }
    static synchronized boolean ready(Context c){
        if(flight==null||!flight.isDone()||consumed||!SecureStore.matchesCapability(c,target))return false;
        long age=android.os.SystemClock.elapsedRealtime()-finished;
        if(age<0||age>REUSE)return false;
        try{return flight.get().usable(System.currentTimeMillis());}catch(Exception ignored){return false;}
    }
}
