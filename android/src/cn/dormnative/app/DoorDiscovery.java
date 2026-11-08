package cn.dormnative.app;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.*;
import java.util.Collections;

/** Bounded, exact-address scan so Android learns the advertised address type before connecting. */
final class DoorDiscovery {
    private static final ScanBudget budget=new ScanBudget();
    interface Result { void found(BluetoothDevice device); }
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;private ScanCallback callback;private boolean ended;
    void start(Context c,String mac,long wait,Result result){
        BluetoothAdapter adapter=c.getSystemService(BluetoothManager.class).getAdapter();
        BluetoothDevice fallback=adapter.getRemoteDevice(mac);
        if(Build.VERSION.SDK_INT<31||c.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){ended=true;result.found(fallback);return;}
        if(!budget.allow(SystemClock.elapsedRealtime())){ended=true;result.found(fallback);return;}
        main.postDelayed(()->deliver(fallback,result),wait);
        try{
            scanner=adapter.getBluetoothLeScanner();if(scanner==null){deliver(fallback,result);return;}
            callback=new ScanCallback(){
                @Override public void onScanResult(int type,ScanResult r){main.post(()->{
                    if(ended||r==null)return;
                    try{if(mac.equalsIgnoreCase(r.getDevice().getAddress()))deliver(r.getDevice(),result);}catch(SecurityException ignored){deliver(fallback,result);}
                });}
                @Override public void onScanFailed(int code){main.post(()->deliver(fallback,result));}
            };
            scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(mac).build()),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(),callback);
        }catch(RuntimeException e){deliver(fallback,result);}
    }
    private void deliver(BluetoothDevice d,Result result){if(ended)return;cancel();result.found(d);}
    void cancel(){if(ended)return;ended=true;main.removeCallbacksAndMessages(null);if(scanner!=null&&callback!=null)try{scanner.stopScan(callback);}catch(RuntimeException ignored){}scanner=null;callback=null;}
}
