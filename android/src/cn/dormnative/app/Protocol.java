package cn.dormnative.app;

/** Port of the observed v53 client branch, not a claim about unobserved firmware. */
public final class Protocol {
    private Protocol() {}
    public static byte[] unlock(long personId) {
        if(personId<=0 || personId>0xffffffffL) throw new IllegalArgumentException("Invalid authorized person identifier");
        byte[] out={(byte)0xaa,0,0x0b,0,0,0,0,0,1,1,0,0,0,0,0};
        for(int i=0;i<4;i++) out[10+i]=(byte)(personId >>> (24-i*8));
        int sum=0; for(int i=0;i<out.length-1;i++) sum+=out[i]&255;
        out[out.length-1]=(byte)sum; return out;
    }
    public static boolean successStatus(byte[] reply,boolean requestSent) {
        // This is precisely the status field checked by the original client.
        // Packet integrity/command correlation beyond this is still undocumented.
        return requestSent && reply!=null && reply.length>=11 && reply[9]==1 && reply[10]==0;
    }
    public static boolean validMac(String mac) {
        return mac!=null && mac.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}") && !mac.equals("00:00:00:00:00:00");
    }
}
