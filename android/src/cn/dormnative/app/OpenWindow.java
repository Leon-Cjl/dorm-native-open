package cn.dormnative.app;

/** User-reported nominal interval. Count conservatively from send, never from a delayed response. */
final class OpenWindow {
    static long remaining(long now,long sentAt){return sentAt<0||now<sentAt?0:Math.max(0,5000-(now-sentAt));}
}
