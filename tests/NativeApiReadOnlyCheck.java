package cn.dormnative.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.json.JSONObject;

/** Opt-in, owner-authorized read-only check. No Bluetooth or settings calls exist here. */
public final class NativeApiReadOnlyCheck {
    public static void main(String[] args)throws Exception{
        JSONObject envelope=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
        JSONObject seed=MigrationTrust.verify(envelope);
        List<NativeApi.Grant> grants=new NativeApi(envelope).grants();
        if(grants.size()!=1)throw new Exception("Expected one owner grant");
        NativeApi.Grant g=grants.get(0);
        if(!g.roomId.equals(seed.getString("roomID"))||!g.deviceId.equals(seed.getString("deviceID"))||!g.mac.equalsIgnoreCase(seed.getString("MAC")))throw new Exception("Owner binding mismatch");
        System.out.println("{\"ok\":true,\"ownerGrantCount\":1,\"ownerBindingMatched\":true,\"currentAccessVerified\":true,\"networkUsed\":true,\"lockCommandSent\":false,\"scope\":\"production NativeApi on desktop JDK with Android SDK JSON, read-only owner queries\"}");
    }
}
