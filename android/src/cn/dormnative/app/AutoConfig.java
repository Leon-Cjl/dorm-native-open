package cn.dormnative.app;

import android.content.Context;

/** Presets are starting values, not measured calibration. Explicit start is always required. */
final class AutoConfig {
    static final int DEFAULT_NEAR=-65,DEFAULT_FAR=-82;
    // Apply the requested relaxation once to older saved calibration as well as defaults.
    static void applyDoorSignalFix(Context c){
        if(SecureStore.prefs(c).getInt("near_preset_revision",0)>=2)return;
        android.content.SharedPreferences.Editor edit=SecureStore.prefs(c).edit();
        if(calibrated(c)){
            int near=SecureStore.prefs(c).getInt("near_rssi",DEFAULT_NEAR);
            if(near>DEFAULT_NEAR){edit.putInt("near_rssi",DEFAULT_NEAR).putInt("far_rssi",Math.min(far(c),DEFAULT_NEAR-8));}
        }
        edit.putInt("near_preset_revision",2).apply();
    }
    static boolean calibrated(Context c){return SecureStore.prefs(c).getBoolean("calibrated",false)&&SecureStore.prefs(c).getInt("calibration_version",0)==SignalCalibration.VERSION;}
    static int near(Context c){return calibrated(c)?SecureStore.prefs(c).getInt("near_rssi",DEFAULT_NEAR):DEFAULT_NEAR;}
    static int far(Context c){return calibrated(c)?SecureStore.prefs(c).getInt("far_rssi",DEFAULT_FAR):DEFAULT_FAR;}
}
