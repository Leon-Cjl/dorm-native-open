// Executes the actual BleOpen.java against deterministic fake Android callbacks.
// No phone, network, owner file, or real Bluetooth adapter is used.
const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build','ble-fixture-'+Date.now());
const sources={
'android/os/SystemClock.java':`package android.os;public class SystemClock {public static long base;public static long elapsedRealtime(){return base+Handler.now;}}`,
 'android/Manifest.java':`package android;public class Manifest{public static class permission{public static final String BLUETOOTH_SCAN="scan";}}`,
'android/content/pm/PackageManager.java':`package android.content.pm;public class PackageManager{public static final int PERMISSION_GRANTED=0;}`,
'android/bluetooth/le/ScanCallback.java':`package android.bluetooth.le;public class ScanCallback{public void onScanResult(int t,ScanResult r){}public void onScanFailed(int c){}}`,
'android/bluetooth/le/ScanResult.java':`package android.bluetooth.le;public class ScanResult{private final String mac;public ScanResult(String m){mac=m;}public android.bluetooth.BluetoothDevice getDevice(){return new android.bluetooth.BluetoothDevice(mac);}}`,
'android/bluetooth/le/ScanFilter.java':`package android.bluetooth.le;public class ScanFilter{public String mac;public static class Builder{String mac;public Builder setDeviceAddress(String m){mac=m;return this;}public ScanFilter build(){ScanFilter f=new ScanFilter();f.mac=mac;return f;}}}`,
'android/bluetooth/le/ScanSettings.java':`package android.bluetooth.le;public class ScanSettings{public static final int SCAN_MODE_LOW_LATENCY=2;public static class Builder{public Builder setScanMode(int m){return this;}public Builder setReportDelay(long t){return this;}public ScanSettings build(){return new ScanSettings();}}}`,
'android/bluetooth/le/BluetoothLeScanner.java':`package android.bluetooth.le;public class BluetoothLeScanner{public static boolean emit=true;public static int starts,stops;public static ScanCallback latest;public void startScan(java.util.List<ScanFilter> filters,ScanSettings settings,ScanCallback callback){starts++;latest=callback;if(emit)callback.onScanResult(0,new ScanResult(filters.get(0).mac));}public void stopScan(ScanCallback c){stops++;}}`,

'android/os/Looper.java':`package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}`,
'android/os/Build.java':`package android.os; public class Build {public static class VERSION {public static int SDK_INT=33;}}`,
'android/os/Handler.java':`package android.os;
import java.util.*;
public class Handler {
 static class Task {Handler owner;Runnable r;long at;Task(Handler o,Runnable r,long a){owner=o;this.r=r;at=a;}}
 static List<Task> tasks=new ArrayList<>();public static long now;
 public Handler(Looper l){} public void post(Runnable r){postDelayed(r,0);}public void postDelayed(Runnable r,long delay){tasks.add(new Task(this,r,now+delay));}
 public void removeCallbacks(Runnable r){tasks.removeIf(t->t.owner==this&&t.r==r);}
 public void removeCallbacksAndMessages(Object o){tasks.removeIf(t->t.owner==this);}
 public static void reset(){tasks.clear();now=0;SystemClock.base+=30001;}
 public static void advance(long delta){long end=now+delta;while(true){Task next=null;for(Task t:tasks)if(t.at<=end&&(next==null||t.at<next.at))next=t;if(next==null)break;tasks.remove(next);now=next.at;next.r.run();}now=end;}
}`,
'android/content/Context.java':`package android.content;public class Context {public Context getApplicationContext(){return this;}public static int permission=0;public int checkSelfPermission(String p){return permission;}public <T>T getSystemService(Class<T> type){return type.cast(new android.bluetooth.BluetoothManager());}}`,
'android/bluetooth/BluetoothProfile.java':`package android.bluetooth;public interface BluetoothProfile {int STATE_DISCONNECTED=0,STATE_CONNECTED=2;}`,
'android/bluetooth/BluetoothManager.java':`package android.bluetooth;public class BluetoothManager {public BluetoothAdapter getAdapter(){return new BluetoothAdapter();}}`,
'android/bluetooth/BluetoothAdapter.java':`package android.bluetooth;public class BluetoothAdapter {public boolean isEnabled(){return true;}public BluetoothDevice getRemoteDevice(String mac){return new BluetoothDevice(mac);}public android.bluetooth.le.BluetoothLeScanner getBluetoothLeScanner(){return new android.bluetooth.le.BluetoothLeScanner();}}`,
'android/bluetooth/BluetoothDevice.java':`package android.bluetooth;public class BluetoothDevice {public static final int TRANSPORT_LE=2;String mac;public BluetoothDevice(){this("02:00:00:00:00:01");}public BluetoothDevice(String m){mac=m;}public String getAddress(){return mac;}public BluetoothGatt connectGatt(android.content.Context c,boolean auto,BluetoothGattCallback cb,int transport){BluetoothGatt.last=new BluetoothGatt(cb);return BluetoothGatt.last;}}`,
'android/bluetooth/BluetoothGattCharacteristic.java':`package android.bluetooth;import java.util.UUID;
public class BluetoothGattCharacteristic {public static final int PROPERTY_WRITE=8,PROPERTY_WRITE_NO_RESPONSE=4,PROPERTY_NOTIFY=16,PROPERTY_INDICATE=32,WRITE_TYPE_DEFAULT=2,WRITE_TYPE_NO_RESPONSE=1;
UUID uuid;int props,type;byte[] value;public BluetoothGattCharacteristic(String id,int p){uuid=UUID.fromString(id);props=p;}
public UUID getUuid(){return uuid;}public int getProperties(){return props;}public BluetoothGattDescriptor getDescriptor(UUID id){return new BluetoothGattDescriptor(id);}public byte[] getValue(){return value;}public void setValue(byte[] v){value=v;}public void setWriteType(int t){type=t;}}
`,
'android/bluetooth/BluetoothGattDescriptor.java':`package android.bluetooth;import java.util.UUID;public class BluetoothGattDescriptor {public static final byte[] ENABLE_NOTIFICATION_VALUE={1,0},ENABLE_INDICATION_VALUE={2,0};UUID uuid;byte[] value;public BluetoothGattDescriptor(UUID id){uuid=id;}public UUID getUuid(){return uuid;}public void setValue(byte[] v){value=v;}}`,
'android/bluetooth/BluetoothGattService.java':`package android.bluetooth;import java.util.UUID;public class BluetoothGattService {public static int rxProps=16,txProps=8;public final BluetoothGattCharacteristic rx=new BluetoothGattCharacteristic("0000fff1-0000-1000-8000-00805f9b34fb",rxProps),tx=new BluetoothGattCharacteristic("0000fff2-0000-1000-8000-00805f9b34fb",txProps);public BluetoothGattCharacteristic getCharacteristic(UUID id){return id.equals(rx.getUuid())?rx:id.equals(tx.getUuid())?tx:null;}}`,
'android/bluetooth/BluetoothGatt.java':`package android.bluetooth;import java.util.*;
public class BluetoothGatt {public static final int GATT_SUCCESS=0;public static BluetoothGatt last;
public BluetoothGattCallback cb;public BluetoothGattService service=new BluetoothGattService();public BluetoothGattDescriptor descriptor;public byte[] subscription;public boolean discovered,closed;public int rssiReads;public boolean readRemoteRssi(){rssiReads++;return true;}public int writes,writeType,writeResult;public List<byte[]> values=new ArrayList<>();public List<Long> times=new ArrayList<>();
public BluetoothGatt(BluetoothGattCallback c){cb=c;}public boolean discoverServices(){discovered=true;return true;}public BluetoothGattService getService(UUID id){return service;}
public boolean setCharacteristicNotification(BluetoothGattCharacteristic c,boolean enable){return true;}
public int writeDescriptor(BluetoothGattDescriptor d,byte[] value){descriptor=d;subscription=value;return 0;}public boolean writeDescriptor(BluetoothGattDescriptor d){return writeDescriptor(d,d.value)==0;}
public int writeCharacteristic(BluetoothGattCharacteristic c,byte[] v,int type){writes++;values.add(v.clone());times.add(android.os.Handler.now);writeType=type;return writeResult;}
public boolean writeCharacteristic(BluetoothGattCharacteristic c){return writeCharacteristic(c,c.value,c.type)==0;}public void disconnect(){}public void close(){closed=true;}}
`,
'android/bluetooth/BluetoothGattCallback.java':`package android.bluetooth;public class BluetoothGattCallback {public void onReadRemoteRssi(BluetoothGatt g,int rssi,int status){}public void onConnectionStateChange(BluetoothGatt g,int status,int state){}public void onServicesDiscovered(BluetoothGatt g,int status){}public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){}public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){}public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){}public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){}}`,
'cn/dormnative/app/SecureStore.java':`package cn.dormnative.app;final class SecureStore{static String token="test-target";static boolean matchesCapability(android.content.Context c,String t){return token.equals(t);}}`,
'cn/dormnative/app/AccessPreparation.java':`package cn.dormnative.app;final class AccessPreparation{static void warm(android.content.Context c){}}`,
'cn/dormnative/app/AutoService.java':`package cn.dormnative.app;final class AutoService{static boolean running;}`,
'cn/dormnative/app/UnlockRun.java':`package cn.dormnative.app;final class UnlockRun {interface Condition {boolean allowed();}}`,
'cn/dormnative/app/NativeApi.java':`package cn.dormnative.app;final class NativeApi {static class Grant {long checkedAt=System.currentTimeMillis(),personId=123;String mac="02:00:00:00:00:01";boolean usable(long now){return now>=checkedAt&&now-checkedAt<=15000;}}}`,
'cn/dormnative/app/BleHarness.java':`package cn.dormnative.app;
import android.bluetooth.*;import android.os.*;import android.content.Context;
public class BleHarness {
static int assertions;static void check(boolean value){assertions++;if(!value)throw new AssertionError("BLE assertion "+assertions);}
static class Session implements BleOpen.Listener {BleOpen open;BluetoothGatt g;boolean allowed=true,ok;int done;String result="";Session(int sdk,int rx,int tx){this(sdk,rx,tx,false);}Session(int sdk,int rx,int tx,boolean arrival){Handler.reset();Build.VERSION.SDK_INT=sdk;BluetoothGattService.rxProps=rx;BluetoothGattService.txProps=tx;open=new BleOpen(new Context(),this,()->allowed);if(arrival)open.requireProximity(-65);open.start(new NativeApi.Grant());Handler.advance(0);g=BluetoothGatt.last;}
public void status(String t){}public void done(boolean success,String t){done++;ok=success;result=t;}
void connected(){g.cb.onConnectionStateChange(g,0,2);Handler.advance(0);check(!g.discovered);Handler.advance(100);check(g.discovered);g.cb.onServicesDiscovered(g,0);Handler.advance(0);check(g.descriptor==null);Handler.advance(200);}
void subscribed(){g.cb.onDescriptorWrite(g,g.descriptor,0);Handler.advance(0);check(g.writes==0);Handler.advance(100);check(g.writes==1);}
void wakeAck(){g.cb.onCharacteristicWrite(g,g.service.tx,0);Handler.advance(0);Handler.advance(100);}
}
public static void main(String[] args){
 for(int sdk:new int[]{32,33})for(int tx:new int[]{8,4,12})for(int rx:new int[]{16,32}){
  Session s=new Session(sdk,rx,tx);s.connected();check(s.g.subscription[0]==(rx==32?2:1));s.subscribed();check(s.g.values.get(0).length==1&&s.g.values.get(0)[0]==0x7e);s.wakeAck();check(s.g.writes==2);check(s.g.writeType==((tx&8)!=0?2:1));check(s.open.sent());
  s.g.cb.onCharacteristicWrite(s.g,s.g.service.tx,0);Handler.advance(0);s.g.cb.onCharacteristicWrite(s.g,s.g.service.tx,0);Handler.advance(100);check(s.g.writes==2);
  s.open.cancel();check(s.done==1&&s.g.closed);Handler.advance(20000);check(s.done==1&&s.g.writes==2);
 }
 Session unsupported=new Session(33,16,0);unsupported.connected();check(unsupported.done==1&&unsupported.g.writes==0);
 Session denied=new Session(33,16,8);denied.connected();denied.allowed=false;denied.g.cb.onDescriptorWrite(denied.g,denied.g.descriptor,0);Handler.advance(100);check(denied.done==1&&denied.g.writes==0);
 Session paused=new Session(33,16,8);paused.connected();paused.subscribed();paused.allowed=false;paused.wakeAck();check(paused.g.writes==1&&!paused.open.sent());
 Session cancelled=new Session(33,16,8);cancelled.g.cb.onConnectionStateChange(cancelled.g,0,2);Handler.advance(0);cancelled.open.cancel();Handler.advance(1000);check(!cancelled.g.discovered&&cancelled.g.writes==0);
 Session failed=new Session(33,16,8);failed.g.cb.onConnectionStateChange(failed.g,133,0);Handler.advance(0);check(failed.done==0&&failed.g.closed&&failed.g.writes==0);
 Handler.advance(250);BluetoothGatt retry=BluetoothGatt.last;check(retry!=failed.g);
 failed.g.cb.onConnectionStateChange(failed.g,0,2);Handler.advance(400);check(!failed.g.discovered);
 retry.cb.onConnectionStateChange(retry,133,0);Handler.advance(0);check(failed.done==1&&failed.result.contains("133"));
 Session rejected=new Session(33,16,8);rejected.connected();rejected.subscribed();rejected.g.writeResult=201;rejected.wakeAck();check(rejected.done==1&&rejected.g.writes==2);Handler.advance(20000);check(rejected.g.writes==2);
 Handler.reset();Build.VERSION.SDK_INT=33;BluetoothGattService.txProps=8;
 final int[] result={0};final String[] error={""};
 BleOpen parallel=new BleOpen(new Context(),new BleOpen.Listener(){public void status(String t){}public void done(boolean ok,String t){result[0]++;error[0]=t;}},()->true);
 parallel.prepare("02:00:00:00:00:01");Handler.advance(0);BluetoothGatt pg=BluetoothGatt.last;
 pg.cb.onConnectionStateChange(pg,0,2);Handler.advance(100);pg.cb.onServicesDiscovered(pg,0);Handler.advance(200);
 pg.cb.onDescriptorWrite(pg,pg.descriptor,0);Handler.advance(100);check(pg.writes==0);
 parallel.authorize(new NativeApi.Grant());check(pg.writes==1);
 pg.cb.onCharacteristicWrite(pg,pg.service.tx,0);Handler.advance(100);check(pg.writes==2);
 pg.cb.onConnectionStateChange(pg,133,0);Handler.advance(1000);check(result[0]==1&&BluetoothGatt.last==pg&&pg.writes==2);
 Handler.reset();BleOpen mismatch=new BleOpen(new Context(),new BleOpen.Listener(){public void status(String t){}public void done(boolean ok,String t){result[0]++;}},()->true);
 mismatch.prepare("02:00:00:00:00:02");Handler.advance(0);BluetoothGatt mg=BluetoothGatt.last;mismatch.authorize(new NativeApi.Grant());check(mg.closed&&mg.writes==0);
 Handler.reset();BleOpen stale=new BleOpen(new Context(),new BleOpen.Listener(){public void status(String t){}public void done(boolean ok,String t){result[0]++;}},()->true);
 stale.prepare("02:00:00:00:00:01");Handler.advance(0);BluetoothGatt sg=BluetoothGatt.last;NativeApi.Grant old=new NativeApi.Grant();old.checkedAt-=16000;stale.authorize(old);check(sg.closed&&sg.writes==0);
 Session timeout=new Session(33,16,8);BluetoothGatt first=timeout.g;Handler.advance(5000);check(first.closed);Handler.advance(250);check(BluetoothGatt.last!=first);timeout.open.cancel();Handler.advance(20000);check(timeout.done==1);
 Session cancelRetry=new Session(33,16,8);cancelRetry.g.cb.onConnectionStateChange(cancelRetry.g,133,0);Handler.advance(0);cancelRetry.open.cancel();Handler.advance(1000);check(BluetoothGatt.last==cancelRetry.g&&cancelRetry.done==1);
 Session wakeFailure=new Session(33,16,8);wakeFailure.connected();wakeFailure.subscribed();wakeFailure.g.cb.onConnectionStateChange(wakeFailure.g,133,0);Handler.advance(1000);check(wakeFailure.done==1&&BluetoothGatt.last==wakeFailure.g&&wakeFailure.g.writes==1);
 Session serviceTimeout=new Session(33,16,8);BluetoothGatt serviceOld=serviceTimeout.g;serviceOld.cb.onConnectionStateChange(serviceOld,0,2);Handler.advance(4000);check(serviceOld.closed&&serviceOld.writes==0);Handler.advance(250);check(BluetoothGatt.last!=serviceOld);serviceOld.cb.onServicesDiscovered(serviceOld,0);Handler.advance(300);check(serviceOld.descriptor==null);serviceTimeout.open.cancel();
 Session subscribeTimeout=new Session(33,16,8);subscribeTimeout.connected();BluetoothGatt subOld=subscribeTimeout.g;Handler.advance(3000);check(subOld.closed&&subOld.writes==0);Handler.advance(250);check(BluetoothGatt.last!=subOld);subscribeTimeout.open.cancel();
 Handler.reset();android.bluetooth.le.BluetoothLeScanner.emit=false;
 final int[] found={0};DoorDiscovery locate=new DoorDiscovery();locate.start(new Context(),"02:00:00:00:00:01",300,d->found[0]++);
 android.bluetooth.le.ScanCallback scan=android.bluetooth.le.BluetoothLeScanner.latest;
 scan.onScanResult(0,new android.bluetooth.le.ScanResult("02:00:00:00:00:02"));Handler.advance(0);check(found[0]==0);
 Handler.advance(299);check(found[0]==0);Handler.advance(1);check(found[0]==1);
 scan.onScanResult(0,new android.bluetooth.le.ScanResult("02:00:00:00:00:01"));Handler.advance(0);check(found[0]==1);
 DoorDiscovery cancelScan=new DoorDiscovery();cancelScan.start(new Context(),"02:00:00:00:00:01",300,d->found[0]++);cancelScan.cancel();Handler.advance(400);check(found[0]==1);
 DoorDiscovery scanFail=new DoorDiscovery();scanFail.start(new Context(),"02:00:00:00:00:01",300,d->found[0]++);android.bluetooth.le.BluetoothLeScanner.latest.onScanFailed(2);Handler.advance(0);check(found[0]==2);Handler.advance(400);check(found[0]==2);
 Context.permission=-1;int starts=android.bluetooth.le.BluetoothLeScanner.starts;
 new DoorDiscovery().start(new Context(),"02:00:00:00:00:01",300,d->found[0]++);check(found[0]==3&&starts==android.bluetooth.le.BluetoothLeScanner.starts);Context.permission=0;
 android.bluetooth.le.BluetoothLeScanner.emit=true;
 Handler.reset();Context context=new Context();Object owner=new Object();
 WarmDoor.start(context,owner,SecureStore.token,"02:00:00:00:00:01");Handler.advance(0);BluetoothGatt wg=BluetoothGatt.last;
 check(OperationGuard.busy());check(!WarmDoor.ready());wg.cb.onConnectionStateChange(wg,0,2);Handler.advance(100);wg.cb.onServicesDiscovered(wg,0);Handler.advance(200);wg.cb.onDescriptorWrite(wg,wg.descriptor,0);Handler.advance(100);
 check(WarmDoor.ready());Handler.advance(100);check(WarmDoor.readySeconds()==15);Handler.advance(3000);check(WarmDoor.ready()&&WarmDoor.readySeconds()==12);check(wg.writes==0);WarmDoor.reserve(owner);WarmDoor.leave(owner);check(!wg.closed);
 final int[] warmDone={0};WarmDoor.Claim claim=WarmDoor.take(context,SecureStore.token,new BleOpen.Listener(){public void status(String t){}public void done(boolean ok,String t){warmDone[0]++;}},()->true);
 check(claim!=null&&claim.ble!=null&&claim.lease!=null);check(WarmDoor.take(context,SecureStore.token,null,()->true)==null);
 claim.ble.authorize(new NativeApi.Grant());check(wg.writes==1);wg.cb.onCharacteristicWrite(wg,wg.service.tx,0);Handler.advance(100);check(wg.writes==2);
 claim.ble.cancel();OperationGuard.release(claim.lease);check(warmDone[0]==1&&!OperationGuard.busy());
 Handler.reset();WarmDoor.start(context,owner,SecureStore.token,"02:00:00:00:00:01");Handler.advance(0);BluetoothGatt leave=BluetoothGatt.last;WarmDoor.leave(owner);check(leave.closed&&!OperationGuard.busy()&&leave.writes==0);
 Handler.reset();WarmDoor.start(context,owner,SecureStore.token,"02:00:00:00:00:01");Handler.advance(0);WarmDoor.reserve(owner);WarmDoor.leave(owner);Handler.advance(1500);check(!OperationGuard.busy());
 Handler.reset();WarmDoor.start(context,owner,SecureStore.token,"02:00:00:00:00:01");Handler.advance(0);check(WarmDoor.take(context,"wrong-target",null,()->true)==null);WarmDoor.close();check(!OperationGuard.busy());
 Handler.reset();WarmDoor.start(context,owner,SecureStore.token,"02:00:00:00:00:01");Handler.advance(25000);check(!OperationGuard.busy());

 Handler.reset();WarmDoor.start(context,owner,SecureStore.token,"02:00:00:00:00:01");Handler.advance(0);BluetoothGatt slow=BluetoothGatt.last;
 Handler.advance(4500);slow.cb.onConnectionStateChange(slow,0,2);Handler.advance(100);slow.cb.onServicesDiscovered(slow,0);Handler.advance(200);slow.cb.onDescriptorWrite(slow,slow.descriptor,0);Handler.advance(200);check(WarmDoor.ready());Handler.advance(13500);check(WarmDoor.ready()&&slow.writes==0&&!slow.closed);Handler.advance(1600);check(slow.closed&&!OperationGuard.busy());
 Handler.reset();Object serviceOwner=new Object();WarmDoor.startPersistent(context,serviceOwner,SecureStore.token,"02:00:00:00:00:01");Handler.advance(0);BluetoothGatt held=BluetoothGatt.last;
 held.cb.onConnectionStateChange(held,0,2);Handler.advance(100);held.cb.onServicesDiscovered(held,0);Handler.advance(200);held.cb.onDescriptorWrite(held,held.descriptor,0);Handler.advance(200);Handler.advance(60000);check(WarmDoor.ready()&&WarmDoor.owned(serviceOwner)&&held.writes==0&&!held.closed);
 WarmDoor.leave(owner);check(WarmDoor.ready()&&!held.closed);
 WarmDoor.Claim heldClaim=WarmDoor.take(context,SecureStore.token,new BleOpen.Listener(){public void status(String t){}public void done(boolean ok,String t){}},()->true);
 check(heldClaim!=null&&heldClaim.ble!=null);WarmDoor.closeOwner(serviceOwner);check(!held.closed);heldClaim.ble.authorize(new NativeApi.Grant());check(held.writes==1);held.cb.onCharacteristicWrite(held,held.service.tx,0);Handler.advance(100);check(held.writes==2);heldClaim.ble.cancel();OperationGuard.release(heldClaim.lease);check(held.closed&&!OperationGuard.busy());
 Handler.reset();WarmDoor.startPersistent(context,serviceOwner,SecureStore.token,"02:00:00:00:00:01");Handler.advance(0);BluetoothGatt stoppedPersistent=BluetoothGatt.last;WarmDoor.closeOwner(serviceOwner);check(stoppedPersistent.closed&&!OperationGuard.busy());
 Session doorstep=new Session(33,16,8,true);doorstep.connected();doorstep.g.cb.onDescriptorWrite(doorstep.g,doorstep.g.descriptor,0);Handler.advance(100);
 check(doorstep.g.writes==0&&doorstep.g.rssiReads==1);
 for(int i=0;i<3;i++){doorstep.g.cb.onReadRemoteRssi(doorstep.g,-70,0);Handler.advance(350);check(doorstep.g.writes==0);}
 for(int i=0;i<2;i++){doorstep.g.cb.onReadRemoteRssi(doorstep.g,-60,0);Handler.advance(350);check(doorstep.g.writes==0);}
 doorstep.g.cb.onReadRemoteRssi(doorstep.g,-60,0);Handler.advance(0);check(doorstep.g.writes==1);doorstep.wakeAck();check(doorstep.g.writes==2&&doorstep.open.sent());
 check(doorstep.open.openWindowRemaining()==5000);Handler.advance(2000);check(doorstep.open.openWindowRemaining()==3000);doorstep.open.cancel();Handler.advance(4000);check(doorstep.open.openWindowRemaining()==0&&doorstep.g.writes==2);
 Session noRssi=new Session(33,16,8,true);noRssi.connected();noRssi.g.cb.onDescriptorWrite(noRssi.g,noRssi.g.descriptor,0);Handler.advance(1700);check(noRssi.done==1&&noRssi.g.writes==0);noRssi.g.cb.onReadRemoteRssi(noRssi.g,-40,0);Handler.advance(1000);check(noRssi.g.writes==0);
 Session weakRssi=new Session(33,16,8,true);weakRssi.connected();weakRssi.g.cb.onDescriptorWrite(weakRssi.g,weakRssi.g.descriptor,0);Handler.advance(100);
 for(int i=0;i<24;i++){weakRssi.g.cb.onReadRemoteRssi(weakRssi.g,-75,0);Handler.advance(350);}check(weakRssi.done==1&&weakRssi.g.writes==0);
 Session cancelRssi=new Session(33,16,8,true);cancelRssi.connected();cancelRssi.g.cb.onDescriptorWrite(cancelRssi.g,cancelRssi.g.descriptor,0);Handler.advance(100);cancelRssi.open.cancel();cancelRssi.g.cb.onReadRemoteRssi(cancelRssi.g,-40,0);Handler.advance(1000);check(cancelRssi.done==1&&cancelRssi.g.writes==0);
 DoorstepGate gate=new DoorstepGate(-65);gate.sample(100,-60);gate.sample(450,-60);gate.sample(800,-60);check(gate.allowed(800));check(!gate.allowed(1801));gate.sample(900,-80);check(!gate.allowed(900));DoorstepGate boundary=new DoorstepGate(-65);boundary.sample(100,-65);boundary.sample(450,-65);boundary.sample(800,-65);check(boundary.allowed(800));boundary.sample(900,-66);check(!boundary.allowed(900));
 check(OpenWindow.remaining(10,-1)==0&&OpenWindow.remaining(10,11)==0&&OpenWindow.remaining(6000,0)==0);
 System.out.println("{\\"ok\\":true,\\"assertions\\":"+assertions+",\\"scope\\":\\"actual BleOpen, DoorDiscovery and WarmDoor with fake Android callbacks; no hardware or network\\"}");
}}
`
};
fs.mkdirSync(out,{recursive:true});const files=[];
for(const [name,source] of Object.entries(sources)){const p=path.join(out,name);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,source);files.push(p);}
files.push(path.join(root,'android/src/cn/dormnative/app/DoorstepGate.java'),path.join(root,'android/src/cn/dormnative/app/OpenWindow.java'),path.join(root,'android/src/cn/dormnative/app/WarmDoor.java'),path.join(root,'android/src/cn/dormnative/app/OperationGuard.java'),path.join(root,'android/src/cn/dormnative/app/ScanBudget.java'),path.join(root,'android/src/cn/dormnative/app/DoorDiscovery.java'),path.join(root,'android/src/cn/dormnative/app/BleOpen.java'),path.join(root,'android/src/cn/dormnative/app/Protocol.java'));
const javac=process.env.JAVAC||'javac',java=process.env.JAVA||'java';
const compile=cp.spawnSync(javac,['-encoding','UTF-8','--release','8','-d',out,...files],{encoding:'utf8'});
if(compile.status!==0)throw Error(compile.stderr);
const run=cp.spawnSync(java,['-cp',out,'cn.dormnative.app.BleHarness'],{encoding:'utf8'});
if(run.status!==0)throw Error(run.stderr);
const result=JSON.parse(run.stdout);result.androidRuntime='NOT RUN';result.realDoorUnlock='NOT RUN';
fs.writeFileSync(path.join(root,'dist/ble-transport-tests.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result));
