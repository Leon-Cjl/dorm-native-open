package cn.dormnative.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.*;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONObject;

public final class AutoActivity extends Activity {
    private final Handler main=new Handler();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ScanSession scanSession=new ScanSession();
    private LinearLayout body;
    private TextView live,preparation;
    private Button observeButton,autoButton,stopButton,farButton;
    private BluetoothLeScanner scanner;
    private ScanCallback calibrationScan;
    private Object scanLease;
    private boolean visible,collecting,nearPhase,busyQuery;
    private String targetMac="",calibrationVault="";
    private long scanStarted,firstSample,lastSample,nearCapturedAt;
    private int generation;
    private AlertDialog progress;
    private Future<?> query;
    private Runnable queryTimeout;
    private SignalCalibration.Profile nearProfile;
    private final ArrayList<Integer> samples=new ArrayList<Integer>();
    private BleProbe probe;
    private final Runnable ticker=new Runnable(){public void run(){if(!visible)return;refresh();updateSampling();main.postDelayed(this,500);}};

    @Override public void onCreate(Bundle b){super.onCreate(b);ConnectionService.stop(this,false);WarmDoor.close();render();}
    @Override protected void onResume(){super.onResume();visible=true;main.removeCallbacks(ticker);main.post(ticker);}
    @Override protected void onStop(){
        visible=false;main.removeCallbacks(ticker);cancelCalibration();generation++;
        if(query!=null)query.cancel(true);busyQuery=false;
        if(probe!=null){probe.cancel();probe=null;}
        super.onStop();
    }
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(MainActivity.INK);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(10),0,dp(8));
        if(size>=20)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(t);return t;
    }
    private Button button(String s,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?Color.WHITE:MainActivity.GREEN);b.setMinHeight(dp(58));
        GradientDrawable bg=new GradientDrawable();bg.setColor(primary?MainActivity.GREEN:Color.WHITE);bg.setCornerRadius(dp(18));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);body.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private boolean calibrated(){return SecureStore.prefs(this).getBoolean("calibrated",false)&&SecureStore.prefs(this).getInt("calibration_version",0)==SignalCalibration.VERSION;}
    private void render(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(245,245,247));
        root.setOnApplyWindowInsetsListener((v,i)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(x.left,x.top,x.right,x.bottom);}else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(22),dp(18),dp(22),dp(36));scroll.addView(body);root.addView(scroll);setContentView(root);root.requestApplyInsets();
        text("靠近开锁",27);text("锁屏 / 口袋检测 · "+AppInfo.version(this)+" 试验版",13);
        live=text("",17);live.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(233,243,255));bg.setCornerRadius(dp(18));live.setBackground(bg);
        observeButton=button("先观察接近，不执行开锁",false,()->enable(true));
        autoButton=button("开启锁屏自动开锁",true,()->enable(false));
        stopButton=button("暂停并停止",false,()->{AutoService.stop(this);refresh();});
        button("查看最近运行记录",false,()->history());
        text("当前配置",20);preparation=text("",14);
        button("返回首页手动测试",false,()->{startActivity(new Intent(this,MainActivity.class));finish();});
        text("可选：精调信号",20);
        text("先在门外靠近门锁采样，再走到信号明显变弱但仍有广播的位置采样。两处都保持日常持机或放口袋的姿势。",14);
        button("① 开始新一轮：采集门边 20 秒",false,()->calibrate(true));
        farButton=button("② 采集远处 20 秒",false,()->calibrate(false));
        text("默认参数用于起始测试，尚未证明适合你的门锁。只有触发不合适时才需精调；两组采样完成后替换默认参数。",12);
        text("检测与后台",20);
        Switch speed=new Switch(this);speed.setText("较快检测（增加扫描耗电）");speed.setChecked(SecureStore.prefs(this).getBoolean("scan_balanced",false));
        speed.setOnCheckedChangeListener((b,checked)->{AutoService.stop(this);SecureStore.prefs(this).edit().putBoolean("scan_balanced",checked).apply();toast("已保存，需重新开启检测");refresh();});body.addView(speed);
        button("通知 / 后台权限检查",false,()->permissionsInfo());
        text("门锁连接",20);
        button("只检查连接，不发开锁命令",false,()->probe());
        button("查询门锁电量与联网状态",false,()->deviceInfo());
        text("音量、上锁延时、常开和 NFC 发卡尚未接入；本页的校准与检测速度只调整手机行为。",13);
        button("查看使用说明与限制",false,()->show("使用说明","连续弱信号至少 4 个样本、跨 12 秒才布防；再靠近至少 3 个样本、跨 2 秒才尝试一次，之后冷却 2 分钟并重新检测离开。\n\n蓝牙不能判断门内外。人在屋内走远再靠近也可能触发；无广播不算离开，门锁若必须触摸才广播，仍可能需要先碰门锁。\n\n观察模式不建立开锁连接，只记录何时满足条件。观察模式不会自动升级为实际开锁。\n\n后台被结束、手机重启或蓝牙中断后需重新开启；没有永久保活保证。真实锁屏兼容性、速度和耗电仍待小米 15 测试。"));
        refresh();
    }
    private void refresh(){
        if(live==null)return;
        long age=SystemClock.elapsedRealtime()-AutoService.liveSampleAt;
        String signal=AutoService.running&&AutoService.liveSampleAt>0?(age<15000?AutoService.liveRssi+" dBm · "+Math.max(0,age/1000)+" 秒前":"广播已中断；不会据此判定离开"):"等待本人门锁广播";
        String mode=AutoService.running?(AutoService.observation?"观察模式 · 不执行开锁":"自动开锁运行中"):"检测已停止";
        live.setText(mode+"\n"+AutoService.liveStatus+(AutoService.running?"\n"+signal+"\n"+AutoService.progress():""));
        observeButton.setEnabled(!AutoService.running&&!collecting);autoButton.setEnabled(!AutoService.running&&!collecting);stopButton.setEnabled(AutoService.running);
        boolean imported=!SecureStore.prefs(this).getString("room_label","").isEmpty();boolean manual=SecureStore.prefs(this).getBoolean("manual_confirmed",false);
        String calibration=calibrated()?"自定义信号：靠近 ≥ "+AutoConfig.near(this)+"，离开 ≤ "+AutoConfig.far(this)+" dBm":nearProfile!=null?"正在精调；两组完成后使用新参数":"已使用默认参数：靠近 ≥ -65，离开 ≤ -82 dBm";
        preparation.setText((imported?"✓ 本人房间已准备":"○ 本人房间未准备")+"\n"+calibration+"\n参数是触发起点，不代表已测得门内外距离。");
        farButton.setEnabled(nearProfile!=null&&!collecting&&!AutoService.running);
    }
    private boolean permissions(boolean notify){
        if(Build.VERSION.SDK_INT<31){show("当前试验范围","靠近检测仅接入 Android 12 及以上，旧系统仍可手动开锁。");return false;}
        ArrayList<String> missing=new ArrayList<String>();for(String p:new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        if(notify&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),90);return false;}
        try{BluetoothManager bm=getSystemService(BluetoothManager.class);BluetoothAdapter a=bm==null?null:bm.getAdapter();if(a==null||!a.isEnabled()){toast("请先在控制中心打开蓝牙");return false;}}catch(RuntimeException e){toast("附近设备权限尚未生效");return false;}return true;
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==90)toast("授权后请再次点击所需功能；不会自动开启开锁");}
    private void enable(boolean observe){
        if(AutoService.running||collecting||busyQuery||OperationGuard.busy()){toast("请先结束当前检测或门锁操作");return;}
        if(!permissions(true))return;
        NotificationManager nm=getSystemService(NotificationManager.class);if(nm==null||!nm.areNotificationsEnabled()){permissionsInfo();return;}
        final String target=SecureStore.capability(this);
        if(observe){startMonitor(true,target);return;}
        new AlertDialog.Builder(this).setTitle("允许锁屏下自动开锁？").setMessage("离开后再靠近且满足信号条件，会核对本人当前授权并尝试一次开锁，无需亮屏确认。\n\n蓝牙不能判断门内外，人在屋内移动也可能触发。启用后有运行通知和额外耗电。")
            .setNegativeButton("取消",null).setPositiveButton("开启",(d,w)->startMonitor(false,target)).show();
    }
    private void startMonitor(boolean observe,String target){if(!SecureStore.matchesCapability(this,target)){toast("宿舍已切换，请重新点击");return;}try{startForegroundService(new Intent(this,AutoService.class).setAction(observe?"observe":"start").putExtra("capability",target));}catch(RuntimeException e){show("未能开启","系统未允许启动后台检测，请保持此页在前台并检查权限。");}}
    private void calibrate(boolean near){
        if(AutoService.running||collecting||busyQuery||OperationGuard.busy()){toast("请先暂停检测并结束当前门锁操作");return;}
        if(!permissions(false))return;
        if(!near&&(nearProfile==null||SystemClock.elapsedRealtime()-nearCapturedAt>600000||!calibrationVault.equals(SecureStore.prefs(this).getString("vault","")))){nearProfile=null;refresh();show("请重新开始","两组采集须在同一授权下、10 分钟内完成。请先采集门边。");return;}
        scanLease=OperationGuard.acquire();if(scanLease==null)return;
        if(near){nearProfile=null;calibrationVault=SecureStore.prefs(this).getString("vault","");SecureStore.prefs(this).edit().putBoolean("calibrated",false).remove("calibration_version").apply();}
        collecting=true;nearPhase=near;scanStarted=0;firstSample=-1;lastSample=-1;samples.clear();final int epoch=++generation;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progress=new AlertDialog.Builder(this).setTitle(near?"门边信号采集":"远处信号采集").setMessage("正在核对本人门锁授权…").setNegativeButton("取消",(d,w)->cancelCalibration()).setOnCancelListener(d->cancelCalibration()).create();progress.show();
        queryTimeout=()->{if(collecting&&epoch==generation){cancelCalibration();show("授权查询超时","请检查手机网络后重试，尚未开始采样。");}};main.postDelayed(queryTimeout,35000);
        query=io.submit(()->{try{
            JSONObject envelope=SecureStore.load(this);if(envelope==null)throw new Exception("请先导入本人授权");NativeApi.Grant grant=new NativeApi(envelope).grants().get(0);
            main.post(()->{if(!visible||!collecting||epoch!=generation)return;
                if(queryTimeout!=null)main.removeCallbacks(queryTimeout);queryTimeout=null;
                if(!calibrationVault.equals(SecureStore.prefs(this).getString("vault",""))){cancelCalibration();show("授权已变化","请从门边采集重新开始。");return;}
                targetMac=grant.mac;
                try{
                    BluetoothManager bm=getSystemService(BluetoothManager.class);scanner=bm.getAdapter().getBluetoothLeScanner();if(scanner==null)throw new Exception();
                    scanStarted=SystemClock.elapsedRealtime();final long token=scanSession.begin(scanStarted);
                    calibrationScan=new ScanCallback(){
                        public void onScanResult(int type,ScanResult r){main.post(()->collect(epoch,token,r));}
                        public void onBatchScanResults(List<ScanResult> rows){for(ScanResult r:rows)main.post(()->collect(epoch,token,r));}
                        public void onScanFailed(int code){main.post(()->{if(epoch!=generation||!scanSession.current(token))return;cancelCalibration();show("采集失败","系统蓝牙扫描错误："+code);});}
                    };
                    scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setDeviceAddress(targetMac).build()),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(),calibrationScan);
                    updateSampling();
                }catch(Exception e){cancelCalibration();show("无法采集","蓝牙检测未启动，请检查权限。");}
            });
        }catch(Exception e){main.post(()->{if(!visible||epoch!=generation)return;cancelCalibration();show("无法核对",e.getMessage()==null?"授权查询失败":e.getMessage());});}});
        refresh();
    }
    private void collect(int epoch,long token,ScanResult r){
        if(!collecting||epoch!=generation||r==null)return;
        try{if(!targetMac.equalsIgnoreCase(r.getDevice().getAddress()))return;}catch(RuntimeException e){cancelCalibration();show("采集停止","附近设备权限已被收回。");return;}
        long observed=r.getTimestampNanos()/1000000;
        if(!scanSession.accept(token,SystemClock.elapsedRealtime(),observed,r.getRssi())||observed-lastSample<200)return;
        lastSample=observed;if(firstSample<0)firstSample=observed;if(samples.size()<200)samples.add(r.getRssi());
    }
    private void updateSampling(){
        if(!collecting||scanStarted==0||progress==null)return;
        long elapsed=SystemClock.elapsedRealtime()-scanStarted;
        if(elapsed>=20000){finishCalibration();return;}
        progress.setMessage("剩余 "+((20000-elapsed+999)/1000)+" 秒 · 有效样本 "+samples.size()+" 个\n"+(samples.isEmpty()?"尚未收到本人门锁广播，请确认门锁已唤醒。":"最近信号："+samples.get(samples.size()-1)+" dBm")+"\n保持位置与持机姿势不变。采集不执行开锁。");
    }
    private void finishCalibration(){
        int[] data=new int[samples.size()];for(int i=0;i<data.length;i++)data[i]=samples.get(i);
        long span=firstSample>=0?lastSample-firstSample:0;boolean near=nearPhase;cancelCalibration();
        try{
            SignalCalibration.Profile profile=SignalCalibration.profile(data,span);
            if(!calibrationVault.equals(SecureStore.prefs(this).getString("vault","")))throw new IllegalArgumentException("授权已变化，请重新开始");
            if(near){nearProfile=profile;nearCapturedAt=SystemClock.elapsedRealtime();show("门边采集完成","中位数 "+profile.median+" dBm，主要波动区间 "+profile.low+" 至 "+profile.high+" dBm。\n\n请在 10 分钟内走到远处继续第②步。");}
            else{
                if(nearProfile==null||SystemClock.elapsedRealtime()-nearCapturedAt>600000)throw new IllegalArgumentException("门边采集已过期，请重新开始");
                int[] thresholds=SignalCalibration.thresholds(nearProfile,profile);
                SecureStore.prefs(this).edit().putInt("near_rssi",thresholds[0]).putInt("far_rssi",thresholds[1]).putInt("calibration_version",SignalCalibration.VERSION).putBoolean("calibrated",true).apply();
                nearProfile=null;EventLog.add(this,EventLog.Event.CALIBRATED,0,-1);show("两组校准完成","建议先开启观察模式，锁屏放口袋走一遍。它只记录触发条件，不会开锁。");
            }
        }catch(IllegalArgumentException e){EventLog.add(this,EventLog.Event.CALIBRATION_FAILED,0,-1);show("校准未完成",e.getMessage());}
        refresh();
    }
    private void cancelCalibration(){
        if(!collecting&&scanLease==null)return;
        collecting=false;generation++;scanSession.end();if(query!=null)query.cancel(true);
        if(queryTimeout!=null)main.removeCallbacks(queryTimeout);queryTimeout=null;
        if(scanner!=null&&calibrationScan!=null)try{scanner.stopScan(calibrationScan);}catch(RuntimeException ignored){}scanner=null;calibrationScan=null;
        OperationGuard.release(scanLease);scanLease=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(progress!=null){progress.dismiss();progress=null;}refresh();
    }
    private boolean startQuery(){if(collecting||busyQuery||OperationGuard.busy()){toast("请等待当前操作结束");return false;}busyQuery=true;return true;}
    private void probe(){
        if(AutoService.running){toast("请先暂停靠近检测");return;}if(!permissions(false)||!startQuery())return;
        final int epoch=++generation;toast("正在核对授权并准备连接检查");
        query=io.submit(()->{try{JSONObject e=SecureStore.load(this);if(e==null)throw new Exception("请先导入本人授权");NativeApi.Grant grant=new NativeApi(e).grants().get(0);
            main.post(()->{if(!visible||epoch!=generation)return;busyQuery=false;probe=new BleProbe(this,t->{probe=null;show("连接检查",t);});probe.start(grant);});
        }catch(Exception e){main.post(()->{if(!visible||epoch!=generation)return;busyQuery=false;show("连接检查未开始",e.getMessage()==null?"本人授权核对失败":e.getMessage());});}});
    }
    private void deviceInfo(){
        if(!startQuery())return;final int epoch=++generation;toast("正在查询本人门锁资料");
        query=io.submit(()->{try{JSONObject e=SecureStore.load(this);if(e==null)throw new Exception("请先导入本人授权");String info=new NativeApi(e).deviceSummary();main.post(()->{if(!visible||epoch!=generation)return;busyQuery=false;show("后台门锁资料",info);});}
        catch(Exception e){main.post(()->{if(!visible||epoch!=generation)return;busyQuery=false;show("资料查询未完成",e.getMessage()==null?"查询失败":e.getMessage());});}});
    }
    private void history(){new AlertDialog.Builder(this).setTitle("最近 40 条本机记录").setMessage(AutoService.diagnostics(this)+"\n最近自动结果："+SecureStore.prefs(this).getString("auto_last","无")+"\n\n"+EventLog.describe(this)).setNegativeButton("关闭",null).setPositiveButton("复制记录",(d,w)->{ClipboardManager c=getSystemService(ClipboardManager.class);if(c!=null)c.setPrimaryClip(ClipData.newPlainText("靠近检测记录",AutoService.diagnostics(this)+"\n最近自动结果："+SecureStore.prefs(this).getString("auto_last","无")+"\n\n"+EventLog.describe(this)));toast("已复制，不包含房间、身份编号或蓝牙地址");}).show();}
    private void permissionsInfo(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        String p="附近设备："+(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED?"已允许":"待允许")+"\n应用通知："+(nm!=null&&nm.areNotificationsEnabled()?"已允许":"待允许");
        new AlertDialog.Builder(this).setTitle("通知与后台运行").setMessage(p+"\n\n如 HyperOS 限制后台，请在本应用的系统设置中允许后台运行，并允许“靠近开锁运行状态”通知。系统结束服务后需重新开启，应用不会自动改系统设置。")
            .setNegativeButton("关闭",null).setPositiveButton("打开系统设置",(d,w)->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){toast("请在系统设置中找到本应用");}}).show();
    }
    private void show(String title,String message){if(visible&&!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show();}
    private void toast(String t){Toast.makeText(this,t,Toast.LENGTH_LONG).show();}
}
