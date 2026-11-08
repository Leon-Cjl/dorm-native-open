package cn.dormnative.app;

/** Explicitly armed, one-shot return-home window; no persisted arming and no silence inference. */
final class ArrivalGate {
    static final long WINDOW=600000, GRACE=5000, HOLD=1200, GAP=3000;
    private final long start;private final int near;
    private long last=-1,streak=-1;private int samples;private boolean used;
    ArrivalGate(long now,int threshold){if(now<0||threshold<-85||threshold>-30)throw new IllegalArgumentException();start=now;near=threshold;}
    boolean ready(long now){return active(now)&&now-start>=GRACE;}
    boolean active(long now){return !used&&withinWindow(now);}
    boolean withinWindow(long now){return now>=start&&now-start<WINDOW;}
    long remaining(long now){return Math.max(0,WINDOW-Math.max(0,now-start));}
    void cancel(){used=true;}
    boolean sample(long now,int rssi){
        if(!active(now)||now-start<GRACE||rssi<-120||rssi>=0||now<=last)return false;
        if(last>=0&&now-last>GAP){streak=-1;samples=0;}
        last=now;
        if(rssi<near){streak=-1;samples=0;return false;}
        if(streak<0)streak=now;
        samples++;
        if(samples>=3&&now-streak>=HOLD){used=true;return true;}
        return false;
    }
}
