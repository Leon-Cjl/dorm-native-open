package cn.dormnative.app;

/** Rejects callbacks from an earlier registration and radio samples from before the current one. */
final class ScanSession {
    private long generation,started,last=-1;
    private boolean active;
    long begin(long now){generation++;active=true;started=now;last=-1;return generation;}
    void end(){active=false;generation++;}
    boolean current(long token){return active&&token==generation;}
    boolean accept(long token,long now,long observed,int rssi){
        if(!current(token)||observed<started||observed<=last||observed>now||now-observed>6000||rssi>=0||rssi< -120)return false;
        last=observed;return true;
    }
}
