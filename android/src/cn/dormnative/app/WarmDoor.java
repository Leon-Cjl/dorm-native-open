package cn.dormnative.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Main-thread-only foreground preconnection. No grant is supplied until an explicit open action. */
final class WarmDoor {
    private static Session current;
    private static String state="idle",detail="";
    static String state(){return current!=null&&!current.valid()?"idle":state;}
    static String detail(){return detail;}
    static final class Claim{final BleOpen ble;final Object lease;Claim(BleOpen b,Object l){ble=b;lease=l;}}
    private static final class Session implements BleOpen.Listener{
        final Context context;final Object owner,lease;final String target,mac;
        final Handler main=new Handler(Looper.getMainLooper());
        BleOpen ble;BleOpen.Listener receiver;UnlockRun.Condition allowed;
        boolean persistent,intentionalClose,claimed,ended,foreground=true,reserved;long reserveUntil,readyAt=-1;
        Session(Context c,Object owner,Object lease,String target,String mac){context=c;this.owner=owner;this.lease=lease;this.target=target;this.mac=mac;}
        boolean valid(){return !ended&&SecureStore.matchesCapability(context,target)&&(claimed?allowed!=null&&allowed.allowed():foreground||reserved&&SystemClock.elapsedRealtime()<reserveUntil);}
        public void status(String text){if(claimed&&receiver!=null)receiver.status(text);else detail=text;}
        public void stage(int current,int total,String label){if(claimed&&receiver!=null)receiver.stage(current,total,label);}
        public void done(boolean ok,String text){
            if(ended)return;ended=true;main.removeCallbacksAndMessages(null);
            if(current==this){current=null;state=intentionalClose?"idle":"failed";detail=intentionalClose?"连接已释放":text;}
            if(claimed&&receiver!=null)receiver.done(ok,text);else OperationGuard.release(lease);
        }
        void close(){intentionalClose=true;if(!ended)ble.cancel();}
        void awaitReady(){
            if(ended||claimed)return;
            if(ble.readyForGrant()){
                readyAt=SystemClock.elapsedRealtime();
                state="ready";detail="门锁连接已准备";AccessPreparation.warm(context);
                if(!persistent)main.postDelayed(()->{if(!claimed){close();state="expired";detail="预连接已到期";}},15000);
            }else main.postDelayed(this::awaitReady,100);
        }
    }
    static boolean owned(Object owner){return current!=null&&!current.ended&&current.owner==owner;}
    static void closeOwner(Object owner){if(owned(owner))current.close();}
    static void startPersistent(Context c,Object owner,String target,String mac){
        if(current!=null||AutoService.running||!SecureStore.matchesCapability(c,target))return;
        Object lease=OperationGuard.acquire();if(lease==null)return;
        Session s=new Session(c.getApplicationContext(),owner,lease,target,mac);s.persistent=true;current=s;state="connecting";detail="正在提前连接门锁";
        s.ble=new BleOpen(s.context,s,s::valid);s.ble.prepareForTap(mac);
        s.main.postDelayed(()->{if(!s.claimed&&s.readyAt<0)s.close();},25000);s.awaitReady();
    }
    static void start(Context c,Object owner,String target,String mac){
        if(current!=null||AutoService.running||!SecureStore.matchesCapability(c,target))return;
        Object lease=OperationGuard.acquire();if(lease==null)return;
        Session s=new Session(c.getApplicationContext(),owner,lease,target,mac);current=s;state="connecting";detail="正在提前连接门锁";
        s.ble=new BleOpen(s.context,s,s::valid);s.ble.prepareForTap(mac);
        // Absolute foreground budget: never keep a lock occupied waiting indefinitely for a tap.
        s.main.postDelayed(()->{if(!s.claimed){s.close();state="expired";detail="预连接已到期";}},25000);
        s.awaitReady();
    }
    static void reserve(Object owner){
        Session s=current;if(s==null||s.owner!=owner||s.ended)return;
        s.reserved=true;s.reserveUntil=SystemClock.elapsedRealtime()+1500;
        s.main.postDelayed(()->{if(!s.claimed)s.close();},1500);
    }
    static Claim take(Context c,String target,BleOpen.Listener receiver,UnlockRun.Condition allowed){
        Session s=current;
        if(s==null||s.ended||s.claimed||!s.target.equals(target)||!s.valid())return null;
        s.claimed=true;s.receiver=receiver;s.allowed=allowed;s.main.removeCallbacksAndMessages(null);current=null;state="idle";
        s.ble.beginOperation();
        return new Claim(s.ble,s.lease);
    }
    static boolean ready(){return current!=null&&!current.ended&&current.ble.readyForGrant();}
    static long readySeconds(){return current==null||current.readyAt<0?0:Math.max(0,(15000-(SystemClock.elapsedRealtime()-current.readyAt)+999)/1000);}
    static void leave(Object owner){Session s=current;if(s==null||s.owner!=owner)return;s.foreground=false;if(!s.reserved)s.close();}
    static void close(){if(current!=null)current.close();}
}
