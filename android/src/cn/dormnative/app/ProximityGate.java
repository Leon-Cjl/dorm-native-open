package cn.dormnative.app;

/** Pure, monotonic-time state machine. Silence NEVER constitutes departure. No persisted armed state. */
final class ProximityGate {
    static final long FAR_HOLD=12000, NEAR_HOLD=1200, MAX_GAP=8000, COOLDOWN=120000;
    private final int near,far;
    private boolean armed;
    private long last=-1, streak=-1, until;
    private int count;
    ProximityGate(int near,int far) {
        if(near> -30 || near< -85 || far< -110 || far>near-8)throw new IllegalArgumentException("靠近与离开信号超出可用范围，请重新选择采集位置");
        this.near=near;this.far=far;
    }
    boolean sample(long now,int rssi) {
        if(now<0 || rssi< -120 || rssi>=0)return false;
        if(last>=0 && now<=last)return false;
        if(last>=0 && now-last>MAX_GAP)clearStreak();
        last=now;
        if(now<until){clearStreak();return false;}
        if(!armed) {
            if(rssi<=far) { advance(now); if(count>=4 && now-streak>=FAR_HOLD){armed=true;clearStreak();} }
            else clearStreak();
            return false;
        }
        if(rssi>=near) {
            advance(now);
            if(count>=3 && now-streak>=NEAR_HOLD){consume(now);return true;}
        } else clearStreak();
        return false;
    }
    private void advance(long now){if(streak<0)streak=now;count++;}
    private void clearStreak(){streak=-1;count=0;}
    void consume(long now){armed=false;until=now+COOLDOWN;clearStreak();last=now;}
    boolean armed(){return armed;}
    long cooldownSeconds(long now){return Math.max(0,(until-now+999)/1000);}
    String progress(long now){
        if(now<until)return "冷却剩余 "+cooldownSeconds(now)+" 秒";
        if(last<0||now-last>MAX_GAP)return "等待新的连续广播";
        long seconds=streak<0?0:Math.max(0,(last-streak)/1000);
        return armed?"靠近确认："+seconds+" / 2 秒，"+count+" / 3 个样本":"离开确认："+seconds+" / 12 秒，"+count+" / 4 个样本";
    }
    String state(long now){return now<until?"冷却中；稍后需重新检测到离开":armed?"已布防；等待连续靠近信号":"等待连续弱信号确认离开";}
}
