package cn.dormnative.app;

/** At most one live attempt; a disconnected session resumes short, bounded retry intervals. */
final class ConnectionRetry {
    private long next;private int attempts;
    boolean shouldAttempt(long now){if(now<next)return false;next=now+Math.min(6000,2000L*(++attempts));return true;}
    void connected(){attempts=0;next=0;}
}
