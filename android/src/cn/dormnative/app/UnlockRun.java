package cn.dormnative.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONObject;

/** A single authorized command, with common serialization and bounded awake time. Main thread only. */
final class UnlockRun {
    interface Listener { void status(String t); void done(boolean ok,boolean sent,String t); void stage(int current,int total,String label); }
    interface Condition { boolean allowed(); }
    private final Context context;
    private final Listener listener;
    private final Condition condition;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private Object lease;
    private Future<?> task;
    private PowerManager.WakeLock wake;
    private BleOpen ble;
    private String vault;
    private volatile boolean ended;
    private long began,accessMs=-1;
    private Integer proximity;
    UnlockRun(Context c,Condition condition,Listener l){context=c.getApplicationContext();this.condition=condition;listener=l;}
    UnlockRun requireProximity(int near){proximity=near;return this;}
    long openWindowRemaining(){return ble==null?0:ble.openWindowRemaining();}
    void start(){
        began=android.os.SystemClock.elapsedRealtime();
        vault=SecureStore.prefs(context).getString("vault","");
        BleOpen.Listener receiver=new BleOpen.Listener(){
            public void status(String t){if(!ended)listener.status(t);}
            public void done(boolean ok,String t){finish(ok,t);}
            public void stage(int current,int total,String label){if(!ended)listener.stage(current,total,label);}
        };
        WarmDoor.Claim prepared=proximity==null?WarmDoor.take(context,SecureStore.capability(context),receiver,this::allowed):null;
        if(prepared!=null){ble=prepared.ble;lease=prepared.lease;}else lease=OperationGuard.acquire();
        if(lease==null){finish(false,"另一个门锁操作正在进行，本次停止");return;}
        try{
            PowerManager pm=context.getSystemService(PowerManager.class);
            if(pm!=null){wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"dormnative:unlock");wake.acquire(40000);}
        }catch(RuntimeException e){finish(false,"无法维持本次蓝牙操作，请检查权限");return;}
        main.postDelayed(()->finish(false,"本次操作超时；如已发送，请查看门锁，不会自动重发"),35000);
        listener.status(prepared!=null?"正在接续已准备的连接":"正在连接门锁");
        task=io.submit(()->{try{
            JSONObject envelope=SecureStore.load(context);if(envelope==null)throw new Exception("请先选择宿舍");
            String mac=MigrationTrust.verify(envelope).getString("MAC");
            main.post(()->{
                if(ended)return;
                if(!allowed()){finish(false,"宿舍已切换或本次操作已停止");return;}
                if(ble==null){ble=new BleOpen(context,receiver,this::allowed);if(proximity!=null)ble.requireProximity(proximity);ble.prepare(mac);}
            });
            if(ended)return;
            NativeApi.Grant grant=AccessPreparation.take(context);
            final long elapsed=android.os.SystemClock.elapsedRealtime()-began;
            main.post(()->{
                if(ended)return;accessMs=elapsed;
                if(!allowed()){finish(false,"靠近信号已过期、授权已变化或操作已暂停，本次停止");return;}
                if(ble!=null)ble.authorize(grant);
            });
        }catch(Exception e){main.post(()->finish(false,e.getMessage()==null?"开门准备失败":e.getMessage()));}});
    }
    boolean sent(){return ble!=null&&ble.sent();}
    private boolean allowed(){return !ended && vault!=null && !vault.isEmpty() && vault.equals(SecureStore.prefs(context).getString("vault","")) && condition.allowed();}
    void cancel(){finish(false,sent()?"已停止等待；已发送的开锁不能撤回，请查看门锁":"本次操作已停止");}
    private void finish(boolean ok,String t){
        if(ended)return;ended=true;boolean sent=sent();main.removeCallbacksAndMessages(null);
        if(task!=null)task.cancel(true);if(ble!=null)ble.cancel();io.shutdownNow();
        if(wake!=null)try{if(wake.isHeld())wake.release();}catch(RuntimeException ignored){}
        OperationGuard.release(lease);lease=null;
        String timing="准备="+accessMs+"ms; 总计="+(android.os.SystemClock.elapsedRealtime()-began)+"ms; "+(ble==null?"":ble.timing());
        android.util.Log.i("DormPerf",timing+" sent="+sent+" ok="+ok);
        if(vault!=null&&vault.equals(SecureStore.prefs(context).getString("vault","")))SecureStore.prefs(context).edit().putString("last_result",t).putString("last_timing",timing).putLong("last_duration_ms",android.os.SystemClock.elapsedRealtime()-began).apply();
        listener.done(ok,sent,t);
    }
}
