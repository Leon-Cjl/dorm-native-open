package cn.dormnative.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** Explicit user-started foreground monitor. Filtered scan; no boot start, no scan restart loop. */
public final class AutoService extends Service {
    static volatile boolean running;
    static volatile String liveStatus="未开启";
    static volatile int liveRssi;
    static volatile long liveSampleAt;
    static volatile boolean observation,arrivalMode;
    static volatile int previewCount;
    private static AutoService active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private ScanCallback scan;
    private final ScanSession scanSession=new ScanSession();
    private ProximityGate gate;
    private MonitorPolicy policy;private ArrivalGate arrival;private boolean arrivalWarmed;
    private UnlockRun run;
    private boolean stopped,registered,failed;
    private String mac="",vault="";
    private long lastManual, latestSample=-1;
    private int latestRssi=-127, near;
    private long scanBegan;private int sampleCount;
    static String diagnostics(Context c){return SecureStore.prefs(c).getString("auto_diagnostic","尚无本版检测记录");}
    private void snapshot(){
        long now=SystemClock.elapsedRealtime();
        String value="模式："+(arrivalMode?"本次回宿舍":observation?"只观察":"长期靠近")+"；有效广播 "+sampleCount+" 个；最近样本 "+(latestSample<0?"无":(now-latestSample)/1000+" 秒前 / "+latestRssi+" dBm")+"；门前阈值 "+near+" dBm；"+liveStatus;
        SecureStore.prefs(this).edit().putString("auto_diagnostic",value).apply();
    }
    private final Runnable heartbeat=new Runnable(){public void run(){
        if(stopped||!running)return;
        long now=SystemClock.elapsedRealtime();
        if(run==null&&now-scanBegan>=20000&&(latestSample<0||now-latestSample>15000))update("尚未收到近期门锁广播；请在门旁唤醒门锁，或手动开门");
        snapshot();main.postDelayed(this,5000);
    }};
    static boolean opening(){return active!=null&&active.run!=null;}
    static void stop(Context c){if(active!=null){EventLog.add(c,EventLog.Event.STOP,0,-1);active.shutdown();}liveStatus="已暂停；需要时可重新开启";c.stopService(new Intent(c,AutoService.class));}
    static String progress(){return active!=null&&active.gate!=null&&running?(active.arrival!=null?"本次有效期剩余 "+((active.arrival.remaining(SystemClock.elapsedRealtime())+999)/1000)+" 秒 · 只尝试一次":active.gate.progress(SystemClock.elapsedRealtime())):"";}
    @Override public void onCreate(){super.onCreate();active=this;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null || "stop".equals(intent.getAction())){if(running)EventLog.add(this,EventLog.Event.STOP,0,-1);liveStatus="已暂停；需要时可重新开启";shutdown();stopSelf();return START_NOT_STICKY;}
        if(!"start".equals(intent.getAction())&&!"observe".equals(intent.getAction())&&!"arrival".equals(intent.getAction())){stopSelfResult(startId);return START_NOT_STICKY;}
        if(!SecureStore.matchesCapability(this,intent.getStringExtra("capability"))){
            if(!running){liveStatus="宿舍已切换，请重新开启检测";stopSelfResult(startId);}return START_NOT_STICKY;
        }
        if(running)return START_NOT_STICKY;
        ConnectionService.stop(this,false);WarmDoor.close();stopped=false;failed=false;observation="observe".equals(intent.getAction());arrivalMode="arrival".equals(intent.getAction());arrivalWarmed=false;arrival=null;
        try{
            if(Build.VERSION.SDK_INT<31)throw new Exception("锁屏无感试验当前仅支持 Android 12 及以上；手动开锁仍可使用");
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new Exception("请授予附近设备权限后重新开启");
            NotificationManager nm=getSystemService(NotificationManager.class);
            if(nm==null || !nm.areNotificationsEnabled())throw new Exception("需要允许通知，才能随时停止无感开锁");
            nm.createNotificationChannel(new NotificationChannel("auto","靠近开锁运行状态",NotificationManager.IMPORTANCE_LOW));
            if(nm.getNotificationChannel("auto").getImportance()==NotificationManager.IMPORTANCE_NONE)throw new Exception("请允许“靠近开锁运行状态”通知后重新开启");
            if(Build.VERSION.SDK_INT>=34)startForeground(30,notification("正在准备靠近检测"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(30,notification("正在准备靠近检测"));
            JSONObject envelope=SecureStore.load(this);if(envelope==null)throw new Exception("请先导入本人授权");
            vault=SecureStore.prefs(this).getString("vault","");
            mac=MigrationTrust.verify(envelope).getString("MAC");
            AutoConfig.applyDoorSignalFix(this);near=AutoConfig.near(this);int far=AutoConfig.far(this);
            if(arrivalMode)arrival=new ArrivalGate(SystemClock.elapsedRealtime(),near);
            gate=new ProximityGate(near,far);policy=new MonitorPolicy(observation,gate);lastManual=SecureStore.prefs(this).getLong("last_manual_elapsed",0);
            BluetoothManager bm=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=bm==null?null:bm.getAdapter();
            if(adapter==null || !adapter.isEnabled())throw new Exception("请先打开蓝牙");
            scanner=adapter.getBluetoothLeScanner();if(scanner==null)throw new Exception("手机无法启动蓝牙检测");
            IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            if(Build.VERSION.SDK_INT>=33)registerReceiver(bluetoothState,filter,Context.RECEIVER_EXPORTED);else registerReceiver(bluetoothState,filter);
            registered=true;running=true;liveRssi=0;liveSampleAt=0;latestSample=-1;previewCount=0;liveStatus="";
            scanBegan=SystemClock.elapsedRealtime();sampleCount=0;main.post(heartbeat);
            SecureStore.prefs(this).edit().putBoolean("monitor_active",true).apply();
            // A single registered scan, scoped to the signed lock MAC. No periodic wake-up polling.
            int mode=arrivalMode?ScanSettings.SCAN_MODE_LOW_LATENCY:SecureStore.prefs(this).getBoolean("scan_balanced",false)?ScanSettings.SCAN_MODE_BALANCED:ScanSettings.SCAN_MODE_LOW_POWER;
            final long token=scanSession.begin(SystemClock.elapsedRealtime());
            scan=new ScanCallback(){
                @Override public void onScanResult(int type,ScanResult result){main.post(()->sample(token,result));}
                @Override public void onBatchScanResults(List<ScanResult> results){for(ScanResult r:results)main.post(()->sample(token,r));}
                @Override public void onScanFailed(int code){main.post(()->{if(scanSession.current(token))fail("系统停止蓝牙检测（"+code+"）；请重新开启");});}
            };
            scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(mac).build()),new ScanSettings.Builder().setScanMode(mode).setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).setReportDelay(0).build(),scan);
            EventLog.add(this,observation?EventLog.Event.OBSERVE:EventLog.Event.AUTO,0,-1);
            if(arrivalMode){
                update("回宿舍模式 · 15 秒后开始判断接近");
                main.postDelayed(()->{if(!stopped&&arrival!=null)endArrival("本次回宿舍模式已到期");},ArrivalGate.WINDOW);
            }else update("等待连续弱信号确认离开");
        }catch(Exception e){fail(e.getMessage()==null?"无感开锁未能启动":e.getMessage());}
        return START_NOT_STICKY;
    }
    private final BroadcastReceiver bluetoothState=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1)!=BluetoothAdapter.STATE_ON)fail("蓝牙发生中断；已停止，请在应用内重新开启");}};
    private void sample(long token,ScanResult result){
        if(stopped || !running || gate==null || result==null || !scanSession.current(token))return;
        long now=SystemClock.elapsedRealtime();
        if(arrival!=null&&!arrival.withinWindow(now)){endArrival("本次回宿舍模式已到期");return;}
        long observed=result.getTimestampNanos()/1000000;
        try{if(!mac.equalsIgnoreCase(result.getDevice().getAddress()))return;}catch(RuntimeException e){fail("蓝牙权限已撤销；已停止检测");return;}
        int rssi=result.getRssi();if(!scanSession.accept(token,now,observed,rssi))return;
        if(!vault.equals(SecureStore.prefs(this).getString("vault",""))){fail("本机授权已变化；已停止检测");return;}
        latestSample=observed;latestRssi=rssi;liveRssi=rssi;liveSampleAt=observed;
        sampleCount++;
        long manual=SecureStore.prefs(this).getLong("last_manual_elapsed",0);
        if(manual!=lastManual){lastManual=manual;if(arrival!=null){endArrival("已手动尝试开门，本次回宿舍模式结束");return;}gate.consume(now);}
        if(arrival!=null){arrivalSample(observed,rssi,now);return;}
        if(OperationGuard.busy()){if(run==null)gate.consume(now);return;}
        boolean wasArmed=gate.armed();
        MonitorPolicy.Decision decision=policy.sample(observed,rssi);
        if(decision!=MonitorPolicy.Decision.NONE){
            if(decision==MonitorPolicy.Decision.OBSERVE){
                previewCount++;EventLog.add(this,EventLog.Event.PREVIEW,rssi,-1);
                SecureStore.prefs(this).edit().putString("observe_last","满足触发条件，共 "+previewCount+" 次；观察模式未执行开锁").apply();
                update("观察到触发条件；未执行开锁，进入冷却");return;
            }
            final long began=now;
            run=new UnlockRun(this,()->!stopped&&running&&SecureStore.prefs(this).getLong("last_manual_elapsed",0)==lastManual,new UnlockRun.Listener(){
                public void status(String t){update(t);}
                public void stage(int current,int total,String label){update("阶段 "+current+"/"+total+" · "+label);}
                public void done(boolean ok,boolean sent,String t){long remaining=run==null?0:run.openWindowRemaining();run=null;EventLog.add(AutoService.this,ok?EventLog.Event.AUTO_OK:sent?EventLog.Event.AUTO_UNKNOWN:EventLog.Event.AUTO_ABORT,latestRssi,SystemClock.elapsedRealtime()-began);if(!stopped){resultNotice(ok,t,remaining);gate.consume(SystemClock.elapsedRealtime());update((ok?"收到开锁成功状态；":"本次自动操作结束；")+"冷却并等待再次离开");SecureStore.prefs(AutoService.this).edit().putString("auto_last",t).apply();snapshot();}}
            }).requireProximity(near);
            run.start();
        }else if(run==null){if(!wasArmed&&gate.armed())EventLog.add(this,EventLog.Event.ARMED,rssi,-1);update(gate.state(now));}
    }
    private void arrivalSample(long observed,int rssi,long now){
        if(run!=null||OperationGuard.busy())return;
        if(!arrivalWarmed&&rssi>=near){arrivalWarmed=true;AccessPreparation.warm(this);}
        if(!arrival.sample(observed,rssi)){
            update("回宿舍模式 · 等待本人门锁接近信号");return;
        }
        final long began=now;
        run=new UnlockRun(this,()->!stopped&&running&&arrival!=null&&arrival.withinWindow(SystemClock.elapsedRealtime())&&SecureStore.prefs(this).getLong("last_manual_elapsed",0)==lastManual,new UnlockRun.Listener(){
            public void status(String t){update("本次回宿舍 · "+t);}
            public void stage(int current,int total,String label){update("本次回宿舍 · 阶段 "+current+"/"+total+" · "+label);}
            public void done(boolean ok,boolean sent,String t){
                long remaining=run==null?0:run.openWindowRemaining();run=null;
                EventLog.add(AutoService.this,ok?EventLog.Event.AUTO_OK:sent?EventLog.Event.AUTO_UNKNOWN:EventLog.Event.AUTO_ABORT,latestRssi,SystemClock.elapsedRealtime()-began);
                if(!stopped){resultNotice(ok,t,remaining);SecureStore.prefs(AutoService.this).edit().putString("auto_last",t).apply();endArrival(ok?"门锁已响应，请立即推门；本次模式已关闭":"本次未完成："+t);}
            }
        }).requireProximity(near);run.start();
    }
    private void endArrival(String message){
        if(stopped)return;liveStatus=message;snapshot();shutdown();stopSelf();
    }
    private void resultNotice(boolean ok,String detail,long remaining){
        try{
            NotificationManager nm=getSystemService(NotificationManager.class);if(nm==null)return;
            NotificationChannel channel=new NotificationChannel("door_result","开门结果提醒",NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null,null);channel.enableVibration(true);channel.setVibrationPattern(new long[]{0,180,100,180});nm.createNotificationChannel(channel);
            PendingIntent open=PendingIntent.getActivity(this,32,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b=new Notification.Builder(this,ok?"door_result":"auto").setSmallIcon(getResources().getIdentifier("ic_tile","drawable",getPackageName())).setContentTitle(ok?remaining>0?"门锁已响应，请立即推门":"门锁已响应，请检查门锁状态":"本次自动开门未完成").setContentText(ok?"开门窗口约 5 秒；倒计时仅供参考，以门锁为准":detail).setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(ok&&remaining>0?remaining:30000);
            if(ok&&remaining>0)b.setWhen(System.currentTimeMillis()+remaining).setUsesChronometer(true).setChronometerCountDown(true);
            nm.notify(32,b.build());
        }catch(RuntimeException ignored){}
    }
    private Notification notification(String status){
        PendingIntent open=PendingIntent.getActivity(this,30,new Intent(this,AutoActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,31,new Intent(this,AutoService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,"auto").setSmallIcon(getResources().getIdentifier("ic_tile","drawable",getPackageName())).setContentTitle(arrivalMode?"本次回宿舍 · 10 分钟内只尝试一次":observation?"仅观察接近 · 不执行开锁":"靠近自动开锁已开启").setContentText(status).setStyle(new Notification.BigTextStyle().bigText(status)).setContentIntent(open).addAction(new Notification.Action.Builder(null,"暂停并停止",stop).build()).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE);
        return b.build();
    }
    private void update(String t){if(stopped||t.equals(liveStatus))return;liveStatus=t;NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(30,notification(t));}
    private void fail(String t){if(stopped)return;failed=true;liveStatus=t;EventLog.add(this,EventLog.Event.ERROR,0,-1);SecureStore.prefs(this).edit().putString("auto_last",t).apply();shutdown();stopSelf();}
    private void shutdown(){
        if(stopped)return;stopped=true;running=false;if(arrival!=null)arrival.cancel();
        scanSession.end();SecureStore.prefs(this).edit().putBoolean("monitor_active",false).apply();
        if(scanner!=null&&scan!=null)try{scanner.stopScan(scan);}catch(RuntimeException ignored){}scanner=null;scan=null;
        if(run!=null){run.cancel();run=null;}
        if(registered)try{unregisterReceiver(bluetoothState);}catch(RuntimeException ignored){}registered=false;
        main.removeCallbacksAndMessages(null);stopForeground(STOP_FOREGROUND_REMOVE);
    }
    @Override public void onDestroy(){shutdown();if(active==this)active=null;if(!failed&&!liveStatus.contains("暂停")&&!arrivalMode)liveStatus="已停止；需在应用内重新开启";super.onDestroy();}
}
