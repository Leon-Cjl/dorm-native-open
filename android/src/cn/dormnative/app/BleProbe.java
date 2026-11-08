package cn.dormnative.app;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;

/** Read-only service discovery. No characteristic or descriptor writes, no unlock wake byte. */
final class BleProbe {
    interface Listener {void done(String t);}
    private final Context c;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());
    private Object lease;private BluetoothGatt gatt;private boolean ended;
    BleProbe(Context c,Listener l){this.c=c;listener=l;}
    void start(NativeApi.Grant grant){
        lease=OperationGuard.acquire();if(lease==null){finish("另一个门锁操作正在进行");return;}
        try{
            BluetoothManager bm=c.getSystemService(BluetoothManager.class);BluetoothAdapter a=bm==null?null:bm.getAdapter();
            if(a==null||!a.isEnabled()){finish("请先打开蓝牙");return;}
            gatt=a.getRemoteDevice(grant.mac).connectGatt(c,false,callback,BluetoothDevice.TRANSPORT_LE);
            if(gatt==null){finish("蓝牙连接未启动");return;}
            main.postDelayed(()->finish("连接检查超时；请唤醒门锁后再试"),12000);
        }catch(RuntimeException e){finish("无法启动连接检查，请确认附近设备权限");}
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        public void onConnectionStateChange(BluetoothGatt g,int status,int state){main.post(()->{
            if(ended||g!=gatt)return;
            if(status!=0||state==BluetoothProfile.STATE_DISCONNECTED){finish("门锁连接中断（"+status+"）");return;}
            if(state==BluetoothProfile.STATE_CONNECTED)try{if(!g.discoverServices())finish("服务发现未启动");}catch(RuntimeException e){finish("蓝牙权限已收回");}
        });}
        public void onServicesDiscovered(BluetoothGatt g,int status){main.post(()->{
            if(ended||g!=gatt)return;
            if(status!=0){finish("服务发现失败（"+status+"）");return;}
            BluetoothGattService service=g.getService(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"));
            BluetoothGattCharacteristic rx=service==null?null:service.getCharacteristic(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"));
            BluetoothGattCharacteristic tx=service==null?null:service.getCharacteristic(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"));
            boolean match=rx!=null&&tx!=null&&(rx.getProperties()&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))!=0&&(tx.getProperties()&(BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE))!=0;
            finish("已连接本人门锁\n发现服务数："+g.getServices().size()+"\n原生开锁服务："+(match?"结构匹配":"未匹配已分析版本")+"\n本次仅发现服务，未发送开锁或设置命令。\n结构匹配仍不代表实际开门成功。");
        });}
    };
    void cancel(){finish("连接检查已停止");}
    private void finish(String t){if(ended)return;ended=true;main.removeCallbacksAndMessages(null);if(gatt!=null){try{gatt.disconnect();}catch(RuntimeException ignored){}try{gatt.close();}catch(RuntimeException ignored){}gatt=null;}OperationGuard.release(lease);listener.done(t);}
}
