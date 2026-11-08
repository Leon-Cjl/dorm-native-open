package cn.dormnative.app;
import java.util.ArrayDeque;
/** Leave headroom below Android's repeated scan-start throttling; fall back to known-address connect. */
final class ScanBudget {
    private final ArrayDeque<Long> starts=new ArrayDeque<Long>();
    synchronized boolean allow(long now){
        if(!starts.isEmpty()&&now<starts.peekLast())starts.clear();
        while(!starts.isEmpty()&&now-starts.peekFirst()>=30000)starts.removeFirst();
        if(starts.size()>=4)return false;
        starts.addLast(now);return true;
    }
}
