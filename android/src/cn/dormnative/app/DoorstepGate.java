package cn.dormnative.app;

/** Connected-link RSSI evidence, not a distance measurement or a door-side detector. */
final class DoorstepGate {
    static final long HOLD=700, FRESH=1000, WAIT=8000;
    private final int near;
    private long last=-1,streak=-1;
    private int samples;
    DoorstepGate(int threshold){if(threshold<-85||threshold>-30)throw new IllegalArgumentException();near=threshold;}
    void sample(long now,int rssi){
        if(now<0||now<=last)return;
        if(last>=0&&now-last>FRESH){streak=-1;samples=0;}
        last=now;
        if(rssi<-120||rssi>=0||rssi<near){streak=-1;samples=0;return;}
        if(streak<0)streak=now;
        samples++;
    }
    boolean allowed(long now){return last>=0&&now>=last&&now-last<=FRESH&&samples>=3&&last-streak>=HOLD;}
}
