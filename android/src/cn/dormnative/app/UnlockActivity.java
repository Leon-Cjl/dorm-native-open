package cn.dormnative.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** One manual intent. Results remain readable; a failed pre-send attempt may be explicitly retried. */
public final class UnlockActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private UnlockRun run;private boolean ended;private long began,referenceUntil;
    private TextView status,elapsed,title,stage;private ProgressBar progress;private Button close,retry,copy;
    private boolean dark;
    private final Runnable ticker=new Runnable(){public void run(){
        if(isFinishing())return;
        long now=SystemClock.elapsedRealtime();
        if(!ended)elapsed.setText(String.format(java.util.Locale.CHINA,"本次等待 %.1f 秒",(now-began)/1000.0));
        else if(referenceUntil>0){long left=Math.max(0,referenceUntil-now);stage.setText(left>0?"参考开门窗口 · 约 "+((left+999)/1000)+" 秒":"参考窗口已结束，请以门锁状态为准");if(left==0)return;}
        else return;
        main.postDelayed(this,100);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);
        KeyguardManager k=getSystemService(KeyguardManager.class);
        if(!SecureStore.matchesCapability(this,getIntent().getStringExtra("capability"))||(k!=null&&k.isDeviceLocked())||(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)){
            startActivity(new Intent(this,MainActivity.class));finish();return;
        }
        String theme=getSharedPreferences("appearance",MODE_PRIVATE).getString("theme","");
        dark=theme.equals("dark")||theme.isEmpty()&&(getResources().getConfiguration().uiMode&48)==32;
        int ink=dark?Color.rgb(242,243,247):MainActivity.INK;
        Window w=getWindow();w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);w.setBackgroundDrawableResource(android.R.color.transparent);
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(24),dp(26),dp(24),dp(24));
        GradientDrawable bg=new GradientDrawable();bg.setColor(dark?Color.rgb(28,30,36):Color.WHITE);bg.setCornerRadius(dp(26));box.setBackground(bg);
        title=text(box,"正在开门",24,ink);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        stage=text(box,"正在准备连接",15,ink);elapsed=text(box,"",13,dark?0xffaeb4c0:MainActivity.MUTED);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(6);progress.setIndeterminate(true);box.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
        status=text(box,"连接与开门准备同时进行",15,ink);status.setPadding(0,dp(18),0,dp(12));
        close=button(box,"取消",()->{if(run!=null&&!ended)run.cancel();finish();});
        retry=button(box,"重新尝试",()->recreate());retry.setVisibility(View.GONE);
        copy=button(box,"复制结果与耗时",()->{ClipboardManager cb=getSystemService(ClipboardManager.class);if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("开门诊断","版本："+AppInfo.version(this)+"\n"+status.getText()+"\n耗时："+SecureStore.prefs(this).getString("last_timing","无")));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();});copy.setVisibility(View.GONE);
        setContentView(box);w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);began=SystemClock.elapsedRealtime();main.post(ticker);
        run=new UnlockRun(this,()->!ended&&!isFinishing(),new UnlockRun.Listener(){
            public void status(String t){if(!ended)status.setText(t);}
            public void stage(int current,int total,String label){if(ended)return;stage.setText(label);progress.setIndeterminate(false);progress.setMax(total);progress.setProgress(current);}
            public void done(boolean ok,boolean sent,String t){
                ended=true;main.removeCallbacksAndMessages(null);progress.setVisibility(View.GONE);
                elapsed.setText(String.format(java.util.Locale.CHINA,"本次等待 %.1f 秒",(SystemClock.elapsedRealtime()-began)/1000.0));
                title.setText(ok?"门锁已响应，请立即推门":sent?"请查看门锁状态":"本次未能开门");
                stage.setText(sent&&!ok?"请求已发出，未确认结果":"操作已结束");
                status.setText(t);close.setText("关闭");copy.setVisibility(View.VISIBLE);retry.setVisibility(!ok&&!sent?View.VISIBLE:View.GONE);
                if(sent)EventLog.add(UnlockActivity.this,ok?EventLog.Event.MANUAL_OK:EventLog.Event.MANUAL_UNKNOWN,0,-1);
                SecureStore.prefs(UnlockActivity.this).edit().putLong("last_manual_elapsed",SystemClock.elapsedRealtime()).putBoolean("manual_can_confirm",sent).apply();
                if(ok){setResult(RESULT_OK);ConnectionService.stop(UnlockActivity.this,false);buzz();long left=run.openWindowRemaining();if(left>0){referenceUntil=SystemClock.elapsedRealtime()+left;main.post(ticker);}}
            }
        });run.start();
    }
    private TextView text(LinearLayout box,String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,0,0,dp(10));box.addView(t);return t;}
    private Button button(LinearLayout box,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(dark?0xff88b5ff:MainActivity.GREEN);box.addView(b);b.setOnClickListener(v->action.run());return b;}
    private void buzz(){try{Vibrator v=Build.VERSION.SDK_INT>=31?getSystemService(VibratorManager.class).getDefaultVibrator():(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null)v.vibrate(VibrationEffect.createOneShot(80,VibrationEffect.DEFAULT_AMPLITUDE));}catch(RuntimeException ignored){}}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onStop(){if(run!=null&&!ended)run.cancel();super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);if(run!=null&&!ended)run.cancel();super.onDestroy();}
}
