package cn.dormnative.app;

import android.app.Application;

public final class NativeApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        if(SecureStore.prefs(this).getBoolean("monitor_active",false)){
            SecureStore.prefs(this).edit().putBoolean("monitor_active",false).apply();
            AutoService.liveStatus="上次检测未正常结束，请重新开启";
            EventLog.add(this,EventLog.Event.INTERRUPTED,0,-1);
        }
    }
}
