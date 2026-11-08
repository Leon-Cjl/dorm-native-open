package cn.dormnative.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;

/** One authorized BLE operation. No scan and no retry after an unlock write. */
final class BleOpen {
    interface Listener { void status(String text); void done(boolean reportedSuccess,String text); default void stage(int current,int total,String label){} }
    private static final UUID SERVICE=UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
        RX=UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),TX=UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
        CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Context context;
    private final Listener listener;
    private final UnlockRun.Condition condition;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private boolean ended,requestSent,wakeReady;
    private String mac;
    private NativeApi.Grant authorization;
    private DoorDiscovery discovery;
    private int attempts,generation;
    private long began;
    private long sentAt=-1,rssiStarted;
    private DoorstepGate doorstep;
    private boolean readingRssi;
    private boolean waitingForTap;
    void prepareForTap(String address){waitingForTap=true;prepare(address);}
    void beginOperation(){if(ended)return;waitingForTap=false;main.removeCallbacks(timeout);main.postDelayed(timeout,18000);}
    void requireProximity(int near){if(stage!=0)throw new IllegalStateException();doorstep=new DoorstepGate(near);}
    long openWindowRemaining(){return OpenWindow.remaining(android.os.SystemClock.elapsedRealtime(),sentAt);}
    private final StringBuilder trace=new StringBuilder();
    private void mark(String text){trace.append(text).append("=").append(android.os.SystemClock.elapsedRealtime()-began).append("ms; ");}
    String timing(){return trace.toString();}
    private int stage; // 1 connect, 2 service, 3 subscribe, 4 wake write, 5 command write, 6 wait
    private static final String[] STAGE_LABELS={"准备中","连接门锁","发现服务","订阅状态","唤醒门锁","发送开锁","等待响应"};
    static final int STAGE_TOTAL=6;
    private void reportStage(){listener.stage(stage,STAGE_TOTAL,STAGE_LABELS[Math.min(stage,STAGE_LABELS.length-1)]);}
    private int writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
    private byte[] command;
    private final Runnable timeout=()->finish(false,requestSent?"已发送一次，未收到可确认的成功状态；请查看门锁":"蓝牙连接超时，请先唤醒门锁后重试");
    BleOpen(Context c,Listener l,UnlockRun.Condition condition) { context=c; listener=l; this.condition=condition; }
    void start(NativeApi.Grant grant){
        if(ended||stage!=0)return;
        if(!grant.usable(System.currentTimeMillis())){finish(false,"本次开门准备已过期");return;}
        authorization=grant;command=Protocol.unlock(grant.personId);prepare(grant.mac);
    }
    void authorize(NativeApi.Grant grant){
        if(ended)return;
        if(mac==null||!mac.equalsIgnoreCase(grant.mac)||!grant.usable(System.currentTimeMillis())){finish(false,"开门准备已变化，请重新尝试");return;}
        authorization=grant;command=Protocol.unlock(grant.personId);mark("access_ready");sendWake();
    }
    void prepare(String address){
        if(ended||stage!=0)return;
        began=android.os.SystemClock.elapsedRealtime();mac=address;
        if(!Protocol.validMac(mac)){finish(false,"门锁地址无法识别");return;}
        if(!waitingForTap)main.postDelayed(timeout,18000);
        connect();
    }
    private void connect(){
        if(ended)return;
        if(!condition.allowed()){finish(false,"操作已停止");return;}
        attempts++;stage=1;wakeReady=false;reportStage();final int epoch=++generation;
        try{
            BluetoothManager manager=context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null||!adapter.isEnabled()){finish(false,"请先打开手机蓝牙");return;}
            listener.status(attempts==1?"正在连接门锁":"正在重新连接门锁");mark("attempt_"+attempts);
            discovery=new DoorDiscovery();
            discovery.start(context,mac,attempts==1?700:1500,device->{
                if(ended||epoch!=generation)return;
                try{
                    gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
                    mark("connect_start");
                    if(gatt==null){reconnect("蓝牙连接未启动");return;}
                    main.postDelayed(()->{if(!ended&&epoch==generation&&stage==1)reconnect("连接等待超时");},5000);
                }catch(SecurityException e){finish(false,"附近设备权限未授予");}
                catch(RuntimeException e){reconnect("蓝牙连接启动异常");}
            });
        }catch(SecurityException e){finish(false,"附近设备权限未授予");}
        catch(RuntimeException e){finish(false,"蓝牙未能启动");}
    }
    private void releaseConnection(){
        if(discovery!=null){discovery.cancel();discovery=null;}
        BluetoothGatt old=gatt;gatt=null;tx=null;
        if(old!=null){try{old.disconnect();}catch(RuntimeException ignored){}try{old.close();}catch(RuntimeException ignored){}}
    }
    private void reconnect(String reason){
        if(ended)return;mark("failure_"+stage);
        if(requestSent||stage>=4||attempts>=2||!condition.allowed()){finish(false,reason);return;}
        releaseConnection();generation++;stage=0;
        main.postDelayed(()->{if(!ended)connect();},250);
    }
    private void sendWake(){
        if(ended||stage!=4||!wakeReady||authorization==null)return;
        if(doorstep!=null&&(readingRssi||!doorstep.allowed(android.os.SystemClock.elapsedRealtime())))return;
        if(!authorization.usable(System.currentTimeMillis())){finish(false,"开门准备已过期，请重新点击");return;}
        wakeReady=false;mark("wake_write");write(new byte[]{0x7e},false);
    }
    private void readProximity(){
        if(ended||stage!=4||!wakeReady||doorstep==null||readingRssi)return;
        if(!condition.allowed()){finish(false,"自动操作已停止");return;}
        final BluetoothGatt g=gatt;
        readingRssi=true;rssiStarted=android.os.SystemClock.elapsedRealtime();final long started=rssiStarted;
        try{if(!g.readRemoteRssi()){readingRssi=false;finish(false,"门锁连接信号读取未启动；请手动开门");return;}}
        catch(RuntimeException e){readingRssi=false;finish(false,"无法读取门锁连接信号；请手动开门");return;}
        main.postDelayed(()->{if(current(g)&&readingRssi&&rssiStarted==started)finish(false,"门锁连接信号未返回；未自动开门");},1500);
    }
    private void watchStage(BluetoothGatt g,int expected,long delay){
        main.postDelayed(()->{if(current(g)&&stage==expected)reconnect("蓝牙阶段 "+expected+" 等待回调超时");},delay);
    }
    private boolean current(BluetoothGatt g) { return !ended && g!=null && g==gatt; }
    private final BluetoothGattCallback callback=new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state) { main.post(()->{
            if(!current(g)) return;
            if(status!=BluetoothGatt.GATT_SUCCESS || state==BluetoothProfile.STATE_DISCONNECTED) { reconnect((requestSent?"连接已断开；开锁请求已发送，请查看门锁":"未连接到门锁")+"（GATT "+status+"）"); return; }
            if(state==BluetoothProfile.STATE_CONNECTED && stage==1) { stage=2; mark("connected");reportStage();watchStage(g,2,4000);
                listener.status("正在发现开锁服务"); main.postDelayed(()->{if(!current(g)||stage!=2)return;try { if(!g.discoverServices()) finish(false,"门锁服务发现未启动"); } catch(RuntimeException e) { finish(false,"蓝牙服务发现异常，请检查附近设备权限"); }},100); }
        }); }
        private void onServicesDiscoveredSafe(BluetoothGatt g){ main.post(()->onServicesDiscoveredBody(g,BluetoothGatt.GATT_SUCCESS)); }
        @Override public void onServicesDiscovered(BluetoothGatt g,int status) { main.post(()->onServicesDiscoveredBody(g,status)); }
        private void onServicesDiscoveredBody(BluetoothGatt g,int status) {
            if(!current(g) || stage!=2) return;
            if(status!=BluetoothGatt.GATT_SUCCESS) { reconnect("门锁服务发现失败（GATT "+status+"）"); return; }
            stage=3;mark("services");reportStage();watchStage(g,3,3000);
            main.postDelayed(()->subscribe(g),200);
        }
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status) { main.post(()->{
            if(!current(g) || stage!=3 || !CCC.equals(d.getUuid())) return;
            if(status!=BluetoothGatt.GATT_SUCCESS) { finish(false,"门锁状态订阅失败（GATT "+status+"）"); return; }
            stage=4;mark("subscribed");reportStage();
            listener.status(authorization==null?"门锁已连接，正在准备开门":"正在开门");
            main.postDelayed(()->{if(current(g)&&stage==4){
                wakeReady=true;
                if(doorstep==null)sendWake();else{
                    listener.status("门锁已连接 · 等待门前稳定信号");
                    main.postDelayed(()->{if(current(g)&&stage==4&&wakeReady)finish(false,"8 秒内未达到门前信号条件；未自动开门，请手动开门");},DoorstepGate.WAIT);
                    readProximity();
                }
            }},100);
        }); }
        @Override public void onReadRemoteRssi(BluetoothGatt g,int rssi,int status){
            final long received=android.os.SystemClock.elapsedRealtime();
            main.post(()->{
                if(!current(g)||stage!=4||!wakeReady||doorstep==null||!readingRssi)return;
                readingRssi=false;
                long now=android.os.SystemClock.elapsedRealtime();
                if(status!=BluetoothGatt.GATT_SUCCESS||received<rssiStarted||received-rssiStarted>1000||now-received>1000){finish(false,"门锁连接信号无效或已过期；未自动开门");return;}
                doorstep.sample(received,rssi);
                listener.status("门锁已连接 · 信号 "+rssi+" dBm，等待门前稳定信号");
                sendWake();
                if(!ended&&wakeReady)main.postDelayed(()->{if(current(g))readProximity();},350);
            });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status) { main.post(()->{
            if(!current(g) || !TX.equals(c.getUuid())) return;
            if(status!=BluetoothGatt.GATT_SUCCESS) { finish(false,(requestSent?"开锁写入未确认，请查看门锁；不会自动重发":"门锁唤醒写入失败")+"（GATT "+status+"）"); return; }
            if(stage==4) {
                stage=5; reportStage(); main.postDelayed(()->{ if(current(g) && stage==5 && !requestSent) { listener.status("正在开门"); write(command,true); } },100);
            } else if(stage==5 && requestSent) { stage=6; reportStage(); listener.status("开锁已发送，等待门锁响应"); }
        }); }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c) {
            if(Build.VERSION.SDK_INT>=33) return;
            byte[] data=c.getValue(); receive(g,c,data==null?null:data.clone());
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value) { receive(g,c,value==null?null:value.clone()); }
    };
    private void subscribe(BluetoothGatt g){
            if(!current(g)||stage!=3)return;
            try {
                BluetoothGattService s=g.getService(SERVICE);
                BluetoothGattCharacteristic rx=s==null?null:s.getCharacteristic(RX); tx=s==null?null:s.getCharacteristic(TX);
                if(rx==null || tx==null) { finish(false,"未发现已适配的收发特征，请在设置中运行连接诊断"); return; }
                int rp=rx.getProperties(),tp=tx.getProperties();
                boolean notify=(rp&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0,indicate=(rp&BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0;
                if((tp&BluetoothGattCharacteristic.PROPERTY_WRITE)!=0)writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                else if((tp&BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0)writeType=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                else {finish(false,"门锁发送特征不可写（属性 "+tp+"）");return;}
                if(!notify&&!indicate){finish(false,"门锁接收特征不支持通知（属性 "+rp+"）");return;}
                listener.status("正在订阅状态（接收属性 "+rp+"，发送属性 "+tp+"）");
                BluetoothGattDescriptor descriptor=rx.getDescriptor(CCC);
                if(descriptor==null || !g.setCharacteristicNotification(rx,true)) { finish(false,"无法订阅门锁状态"); return; }
                byte[] subscription=notify?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                boolean ok;
                if(Build.VERSION.SDK_INT>=33) ok=g.writeDescriptor(descriptor,subscription)==0;
                else { descriptor.setValue(subscription); ok=g.writeDescriptor(descriptor); }
                if(!ok) finish(false,"门锁状态订阅未发送");
            } catch(SecurityException e) { finish(false,"蓝牙权限已被收回"); }
            catch(RuntimeException e){finish(false,"状态订阅出现系统异常");}
    }
    private void receive(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value) { main.post(()->{
        if(!current(g) || !RX.equals(c.getUuid())) return;
        if(Protocol.successStatus(value,requestSent)) finish(true,"收到门锁成功状态，请确认门已打开");
    }); }
    private void write(byte[] value,boolean unlock) {
        if(ended || gatt==null || tx==null || (unlock && requestSent)) return;
        if(!condition.allowed()||authorization==null||!authorization.usable(System.currentTimeMillis())){finish(false,"操作已暂停或开门准备已过期，未继续发送");return;}
        if(doorstep!=null&&!doorstep.allowed(android.os.SystemClock.elapsedRealtime())){finish(false,"门前信号已过期；未继续发送，请手动开门");return;}
        try {
            if(unlock){mark("unlock_write");sentAt=android.os.SystemClock.elapsedRealtime();requestSent=true;}
            boolean ok;
            if(Build.VERSION.SDK_INT>=33) ok=gatt.writeCharacteristic(tx,value,writeType)==0;
            else { tx.setWriteType(writeType); tx.setValue(value); ok=gatt.writeCharacteristic(tx); }
            if(!ok) finish(false,unlock?"系统未接受开锁写入，本次停止":"门锁唤醒写入未被接受");
        } catch(SecurityException e) { finish(false,"蓝牙权限已被收回，请检查门锁状态"); }
        catch(RuntimeException e) { finish(false,"蓝牙通信中断，请检查门锁状态"); }
    }
    boolean sent() { return requestSent; }
    boolean readyForGrant(){return !ended&&stage==4&&wakeReady&&authorization==null;}
    void cancel() { finish(false,requestSent?"已停止等待；已经发起的开锁不能撤回，请查看门锁":"本次操作已取消"); }
    private void finish(boolean success,String text) {
        if(ended) return;mark("finish"); ended=true; main.removeCallbacksAndMessages(null);
        if(discovery!=null)discovery.cancel();
        BluetoothGatt g=gatt;gatt=null;
        if(g!=null){try{g.disconnect();}catch(RuntimeException ignored){}try{g.close();}catch(RuntimeException ignored){}}
        listener.done(success,success?text:"[蓝牙阶段 "+stage+"] "+text);
    }
}
