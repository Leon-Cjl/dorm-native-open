package cn.dormnative.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;

/** Explicit app-use session: retry preparation, hold the link, stop after manual success. No lock writes. */
public final class ConnectionService extends Service {
    static volatile boolean running;
    private static ConnectionService active;
    private final Handler main=new Handler(Looper.getMainLooper());
    private String target="",mac="",last="";private boolean ended;
    private final ConnectionRetry retry=new ConnectionRetry();
    static void ensure(Context c){
        if(running||AutoService.running||!SecureStore.prefs(c).getBoolean("auto_connect_enabled",true))return;
        if(Build.VERSION.SDK_INT>=31&&(c.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED||c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED))return;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null||!nm.areNotificationsEnabled()||Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        try{c.startForegroundService(new Intent(c,ConnectionService.class).setAction("start").putExtra("capability",SecureStore.capability(c)));}catch(RuntimeException ignored){}
    }
    static void stop(Context c,boolean user){
        if(user)SecureStore.prefs(c).edit().putBoolean("auto_connect_enabled",false).apply();
        if(active!=null)active.shutdown();c.stopService(new Intent(c,ConnectionService.class));
    }
    @Override public void onCreate(){super.onCreate();active=this;}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i==null||"stop".equals(i.getAction())){stop(this,true);return START_NOT_STICKY;}
        if(!SecureStore.matchesCapability(this,i.getStringExtra("capability"))){if(!running)stopSelf();return START_NOT_STICKY;}
        if(running)return START_NOT_STICKY;
        try{
            NotificationManager nm=getSystemService(NotificationManager.class);
            if(nm==null)throw new Exception();
            nm.createNotificationChannel(new NotificationChannel("connection","门锁预连接",NotificationManager.IMPORTANCE_LOW));
            if(nm.getNotificationChannel("connection").getImportance()==NotificationManager.IMPORTANCE_NONE)throw new Exception();
            if(Build.VERSION.SDK_INT>=34)startForeground(40,notification("正在准备连接门锁"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(40,notification("正在准备连接门锁"));
            target=SecureStore.capability(this);mac=MigrationTrust.verify(SecureStore.load(this)).getString("MAC");
            WarmDoor.close();ended=false;running=true;main.post(loop);
        }catch(Exception e){shutdown();stopSelf();}
        return START_NOT_STICKY;
    }
    private final Runnable loop=new Runnable(){public void run(){
        if(ended)return;
        if(!SecureStore.matchesCapability(ConnectionService.this,target)||AutoService.running){shutdown();stopSelf();return;}
        long now=SystemClock.elapsedRealtime();
        if(WarmDoor.ready()){retry.connected();show("门锁已连接，点开门可直接接续；开门后结束");}
        else if(WarmDoor.owned(ConnectionService.this)){show("正在提前连接门锁；失败会继续尝试");}
        else if(OperationGuard.busy()){show("正在开门，等待本次结果");}
        else if(retry.shouldAttempt(now)){
            WarmDoor.startPersistent(ConnectionService.this,ConnectionService.this,target,mac);show("正在提前连接门锁");
        }else show("等待重连；可直接点开门");
        main.postDelayed(this,500);
    }};
    private Notification notification(String t){
        PendingIntent open=PendingIntent.getActivity(this,40,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,41,new Intent(this,ConnectionService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"connection").setSmallIcon(getResources().getIdentifier("ic_tile","drawable",getPackageName())).setContentTitle("宿舍门锁预连接").setContentText(t).setContentIntent(open).addAction(new Notification.Action.Builder(null,"停止预连接",stop).build()).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
    }
    private void show(String t){if(t.equals(last))return;last=t;NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(40,notification(t));}
    private void shutdown(){if(ended)return;ended=true;running=false;main.removeCallbacksAndMessages(null);WarmDoor.closeOwner(this);stopForeground(STOP_FOREGROUND_REMOVE);}
    @Override public void onDestroy(){shutdown();if(active==this)active=null;super.onDestroy();}
}
