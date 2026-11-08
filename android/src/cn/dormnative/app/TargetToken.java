package cn.dormnative.app;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** Late intents must carry the capability of the target selected when they were created. */
final class TargetToken {
    static boolean matches(String current,String supplied){
        return current!=null&&!current.isEmpty()&&supplied!=null&&MessageDigest.isEqual(current.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));
    }
}

