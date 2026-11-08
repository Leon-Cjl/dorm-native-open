package cn.dormnative.app;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded on-device event history. Only fixed event labels and numeric measurements are accepted. */
final class EventLog {
    enum Event {
        OBSERVE("已启动观察模式，不执行开锁"),AUTO("已启动自动开锁"),ARMED("已连续检测到离开，等待靠近"),
        PREVIEW("满足触发条件；观察模式未执行开锁"),AUTO_OK("门锁返回成功状态，实体动作仍需确认"),
        AUTO_UNKNOWN("自动请求已发出，结果未确认"),AUTO_ABORT("本次自动操作停止，未发开锁命令"),
        MANUAL_OK("手动请求返回成功状态"),MANUAL_UNKNOWN("手动请求已发出，结果未确认"),
        STOP("检测已暂停"),ERROR("检测异常停止，请查看状态"),INTERRUPTED("上次检测未正常结束，需要重新开启"),
        CALIBRATED("两处信号校准已更新"),CALIBRATION_FAILED("校准未完成，自动开锁保持停用");
        final String label;Event(String label){this.label=label;}
    }
    static synchronized void add(Context c,Event event,int rssi,long duration){
        try{
            JSONArray old=new JSONArray(SecureStore.prefs(c).getString("events","[]")),next=new JSONArray();
            for(int i=Math.max(0,old.length()-39);i<old.length();i++)next.put(old.get(i));
            JSONObject row=new JSONObject().put("at",System.currentTimeMillis()).put("event",event.name());
            if(rssi>=-120&&rssi<0)row.put("rssi",rssi);
            if(duration>=0&&duration<=120000)row.put("duration",duration);
            next.put(row);SecureStore.prefs(c).edit().putString("events",next.toString()).apply();
        }catch(Exception ignored){}
    }
    static String describe(Context c){
        StringBuilder out=new StringBuilder();
        try{
            JSONArray rows=new JSONArray(SecureStore.prefs(c).getString("events","[]"));
            SimpleDateFormat fmt=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault());
            for(int i=rows.length()-1;i>=0;i--){
                JSONObject row=rows.getJSONObject(i);Event event=Event.valueOf(row.getString("event"));
                out.append(fmt.format(new Date(row.getLong("at")))).append("  ").append(event.label);
                if(row.has("rssi"))out.append(" · ").append(row.getInt("rssi")).append(" dBm");
                if(row.has("duration"))out.append(" · 本次处理 ").append(row.getLong("duration")/1000.0).append(" 秒");
                out.append('\n');
            }
        }catch(Exception ignored){}
        return out.length()==0?"尚无记录":out.toString();
    }
}
