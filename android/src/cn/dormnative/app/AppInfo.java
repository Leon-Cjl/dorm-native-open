package cn.dormnative.app;

import android.content.Context;

final class AppInfo {
    static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "试验版";}}
}
