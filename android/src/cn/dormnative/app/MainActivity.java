package cn.dormnative.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.JavascriptInterface;
import android.net.Uri;
import android.provider.Settings;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    static final int GREEN=Color.rgb(0,102,204), INK=Color.rgb(23,23,27), MUTED=Color.rgb(98,98,107);
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private boolean importing,preparing,visible,personal,homeVisible=true,opening,manualCompleted;
    private Object profileLease;private JSONObject pendingProfile;private String pendingTarget="";
    private String accountLabel="未选择账号";private int savedAccounts,savedRooms;
    static volatile int stageCurrent,stageTotal;static volatile String stageLabel="";
    private LinearLayout pageRoot;
    private WebView web;private String prepareError="",pending="";
    private final Handler main=new Handler();
    private static final String ORIGIN="https://appassets.androidplatform.net/ui/";
    private final Runnable ticker=new Runnable(){public void run(){if(visible){if(homeVisible&&WarmDoor.ready())AccessPreparation.warm(MainActivity.this);publish();main.postDelayed(this,1000);}}};
    @Override public void onCreate(Bundle state) { super.onCreate(state);AutoConfig.applyDoorSignalFix(this);personal=PersonalBootstrap.available(this);render();prepareOwner(); }
    @Override protected void onResume() { super.onResume();visible=true;opening=false;refreshProfileSummary();showPendingProfile();if(homeVisible){AccessPreparation.warm(this);primeDoor();}main.removeCallbacks(ticker);main.post(ticker); }
    @Override protected void onStop(){visible=false;if(!opening)manualCompleted=false;WarmDoor.leave(this);main.removeCallbacks(ticker);super.onStop();}
    @Override protected void onDestroy() { WarmDoor.leave(this);main.removeCallbacksAndMessages(null);io.shutdownNow();OperationGuard.release(profileLease);profileLease=null;if(web!=null){web.removeJavascriptInterface("Dorm");web.destroy();}super.onDestroy(); }
    private void render() {
        if(web!=null){publish();return;}
        LinearLayout root=new LinearLayout(this);pageRoot=root; root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(245,245,247));
        root.setOnApplyWindowInsetsListener((v,i)->{ if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(x.left,x.top,x.right,x.bottom);}else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        web=new WebView(this);web.setBackgroundColor(Color.rgb(245,245,247));
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);web.getSettings().setMixedContentMode(0);
        web.addJavascriptInterface(new Bridge(),"Dorm");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                String u=r.getUrl().toString();String name=u.startsWith(ORIGIN)?u.substring(ORIGIN.length()):"";
                if(Arrays.asList("index.html","gongda.css","door.css","door.js").contains(name))try{return new WebResourceResponse(name.endsWith(".css")?"text/css":name.endsWith(".js")?"application/javascript":"text/html","UTF-8",getAssets().open("ui/"+name));}catch(Exception ignored){}
                return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView v,String u){if((ORIGIN+"index.html").equals(u))publish();}
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,-1));setContentView(root);applyAppearance();root.requestApplyInsets();web.loadUrl(ORIGIN+"index.html");
    }
    private final class Bridge {
        @JavascriptInterface public void setTheme(String theme){if(!"light".equals(theme)&&!"dark".equals(theme))return;main.post(()->{if(!visible||isDestroyed())return;getSharedPreferences("appearance",MODE_PRIVATE).edit().putString("theme",theme).apply();applyAppearance();publish();});}
        @JavascriptInterface public void ready(){main.post(()->publish());}
        @JavascriptInterface public void action(String action){main.post(()->{if(visible&&!isFinishing())handle(action);});}
    }
    private String appearance(){
        String saved=getSharedPreferences("appearance",MODE_PRIVATE).getString("theme","");
        if(saved.equals("dark")||saved.equals("light"))return saved;
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES?"dark":"light";
    }
    private void applyAppearance(){
        boolean dark=appearance().equals("dark");
        int color=dark?Color.rgb(17,17,20):Color.rgb(245,245,247);
        if(pageRoot!=null)pageRoot.setBackgroundColor(color);
        if(web!=null)web.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);
        int flags=getWindow().getDecorView().getSystemUiVisibility();
        int light=android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(dark?flags&~light:flags|light);
        if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null){
            int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(dark?0:mask,mask);
        }
    }
    private void primeDoor(){
        if(!visible||!homeVisible||preparing||importing||opening||manualCompleted||AutoService.running||SecureStore.prefs(this).getString("vault","").isEmpty())return;
        ConnectionService.ensure(this);
    }
    private void prepareOwner(){
        if(preparing)return;preparing=true;prepareError="";publish();
        io.submit(()->{try{PersonalBootstrap.install(this);main.post(()->{if(isDestroyed())return;preparing=false;refreshProfileSummary();syncShortcut();publish();showPendingProfile();if(visible&&homeVisible){AccessPreparation.warm(this);primeDoor();}if(visible)continuePending();else pending="";});}catch(Exception e){main.post(()->{if(isDestroyed())return;preparing=false;pending="";prepareError="本人房间准备未完成，请重试";publish();});}});
    }
    private void publish(){
        if(web==null||isDestroyed())return;
        try{JSONObject s=new JSONObject().put("theme",appearance()).put("room",SecureStore.prefs(this).getString("room_label","")).put("ready",!SecureStore.prefs(this).getString("vault","").isEmpty()).put("preparing",preparing).put("warmReady",WarmDoor.ready()).put("warmState",WarmDoor.state()).put("connectionRunning",ConnectionService.running).put("accessReady",AccessPreparation.ready(this)).put("opening",opening).put("warmSeconds",WarmDoor.readySeconds()).put("switching",importing).put("account",accountLabel).put("accountCount",savedAccounts).put("roomCount",savedRooms).put("error",prepareError).put("version",AppInfo.version(this)).put("auto",AutoService.running&&!AutoService.observation&&!AutoService.arrivalMode).put("arrival",AutoService.running&&AutoService.arrivalMode).put("observing",AutoService.running&&AutoService.observation).put("autoStatus",AutoService.liveStatus).put("autoDetail",AutoService.progress()).put("last",SecureStore.prefs(this).getString("last_result","还没有开门记录")).put("personal",personal).put("stageCurrent",stageCurrent).put("stageTotal",stageTotal).put("stageLabel",stageLabel);web.evaluateJavascript("window.updateState&&window.updateState("+s.toString()+")",null);}catch(Exception ignored){}
    }
    private void handle(String action){
        if(opening&&action.equals("open"))return;
        if(preparing||importing){if(!Arrays.asList("stop","help","records","system","diagnostics","pageHome","pageSettings","importHelp").contains(action)){toast("正在准备或核对房间，请稍等");return;}}
        if(Arrays.asList("advanced").contains(action)){ConnectionService.stop(this,false);WarmDoor.close();}
        switch(action){
            case "pageHome":homeVisible=true;AccessPreparation.warm(this);primeDoor();publish();break;
            case "pageSettings":homeVisible=false;publish();break;
            case "warm":if(ConnectionService.running){ConnectionService.stop(this,true);publish();break;}manualCompleted=false;SecureStore.prefs(this).edit().putBoolean("auto_connect_enabled",true).apply();pendingTarget=SecureStore.capability(this);pending="warm";continuePending();break;
            case "importHelp":importHelp();break;
            case "open":if(AutoService.opening()){toast("自动开锁正在进行，请先等待结果");return;}if(AutoService.running)AutoService.stop(this);pendingTarget=SecureStore.capability(this);pending="open";continuePending();break;
            case "auto":if(AutoService.running){AutoService.stop(this);publish();}else{pendingTarget=SecureStore.capability(this);pending="auto";continuePending();}break;
            case "arrival":arrivalPrompt();break;
            case "observe":if(AutoService.running){toast("请先暂停当前检测，再开启观察模式");return;}pendingTarget=SecureStore.capability(this);pending="observe";continuePending();break;
            case "stop":pending="";ConnectionService.stop(this,false);WarmDoor.close();AutoService.stop(this);publish();break;
            case "tile":tile();break;case "shortcut":pin();break;
            case "advanced":startActivity(new Intent(this,AutoActivity.class));break;
            case "diagnostics":diagnostics();break;
            case "records":new AlertDialog.Builder(this).setTitle("最近运行记录").setMessage(EventLog.describe(this)).setPositiveButton("关闭",null).show();break;
            case "system":startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));break;
            case "help":help();break;
            case "retry":SecureStore.prefs(this).edit().putBoolean("personal_disabled",false).apply();prepareOwner();break;
            case "accounts":showAccounts();break;
            case "rooms":showCurrentRooms();break;
            case "import":importFile();break;
            case "disable":clearProfiles();break;
        }
    }
    private void arrivalPrompt(){
        if(AutoService.running){toast("请先暂停当前检测，再开启回宿舍模式");return;}
        final String target=SecureStore.capability(this);
        new AlertDialog.Builder(this).setTitle("开启本次回宿舍模式？")
            .setMessage("从现在起 10 分钟有效，15 秒后开始判断接近；连续接近信号后先连接，再读取连接信号判断是否尝试开门一次。成功后请立即推门，开门窗口约 5 秒；震动提醒受系统通知设置控制。随后自动关闭。无需先检测离开。\n\n请在离开宿舍、准备返回时开启。蓝牙不能区分门内外，隔墙或室内接近也可能触发；门锁必须能广播。此模式短时提高扫描频率，有额外耗电。通知里随时可暂停，失败不会自动重发。")
            .setNegativeButton("取消",null).setPositiveButton("开启 10 分钟",(d,w)->{
                if(!visible||!SecureStore.matchesCapability(this,target))return;
                pendingTarget=target;pending="arrival";continuePending();
            }).show();
    }
    private void continuePending(){
        if(pending.isEmpty())return;
        if(!SecureStore.matchesCapability(this,pendingTarget)){pending="";toast("宿舍已切换，请重新点击");return;}
        if(preparing){toast("正在准备房间…");return;}
        if(SecureStore.prefs(this).getString("vault","").isEmpty()){toast(personal?"请先恢复本人房间":"请先导入自己的宿舍授权");pending="";return;}
        ArrayList<String> required=new ArrayList<String>();
        if(Build.VERSION.SDK_INT>=31){required.add(Manifest.permission.BLUETOOTH_CONNECT);required.add(Manifest.permission.BLUETOOTH_SCAN);}
        if(!pending.equals("open")&&Build.VERSION.SDK_INT>=33)required.add(Manifest.permission.POST_NOTIFICATIONS);
        ArrayList<String> missing=new ArrayList<String>();for(String p:required)if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),71);return;}
        try{BluetoothManager bm=getSystemService(BluetoothManager.class);BluetoothAdapter a=bm==null?null:bm.getAdapter();if(a==null){pending="";toast("手机不支持蓝牙");return;}if(!a.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),72);return;}}catch(RuntimeException e){pending="";toast("请检查附近设备权限和蓝牙开关");return;}
        String action=pending;pending="";
        if(action.equals("open")){opening=true;WarmDoor.reserve(this);publish();try{startActivityForResult(new Intent(this,UnlockActivity.class).putExtra("capability",SecureStore.capability(this)),73);}catch(RuntimeException e){opening=false;toast("未能打开开门面板，请重试");publish();}}
        else if(action.equals("warm")){AccessPreparation.warm(this);primeDoor();publish();}
        else try{startForegroundService(new Intent(this,AutoService.class).setAction(action.equals("arrival")?"arrival":action.equals("observe")?"observe":"start").putExtra("capability",SecureStore.capability(this)));}catch(RuntimeException e){toast("系统未允许后台检测，请检查应用权限");}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==71){for(int g:grants)if(g!=PackageManager.PERMISSION_GRANTED){pending="";toast("需要允许附近设备或通知权限后使用");return;}if(grants.length==0){pending="";return;}continuePending();}}

    private void refreshProfileSummary(){
        accountLabel="未选择账号";savedAccounts=0;savedRooms=0;
        try{
            ProfileCatalog catalog=SecureStore.catalog(this);
            List<JSONObject> accounts=catalog.accounts();savedAccounts=accounts.size();
            String selected=SecureStore.prefs(this).getString("profile_id","");
            if(selected.isEmpty())return;
            String account=catalog.find(selected).getString("account");
            for(JSONObject a:accounts)if(account.equals(a.getString("id"))){accountLabel=a.getString("label");savedRooms=a.getInt("count");}
        }catch(Exception ignored){accountLabel="账号资料待检查";}
    }
    private void profileError(String title,Exception e){
        if(visible&&!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage()==null?"请重试，原有授权未主动切换":e.getMessage()).setPositiveButton("知道了",null).show();
    }
    private void showAccounts(){
        pending="";
        try{
            SecureStore.ensureProfiles(this);
            List<JSONObject> accounts=SecureStore.catalog(this).accounts();
            if(accounts.isEmpty()){
                new AlertDialog.Builder(this).setTitle("添加账号与宿舍").setMessage("每个新账号或新宿舍首次需要一份已核对的签名授权文件。保存后可以直接切换。当前未接入微信一键登录。").setNegativeButton("取消",null).setPositiveButton("选择授权文件",(d,w)->importFile()).show();return;
            }
            String[] labels=new String[accounts.size()];
            for(int i=0;i<labels.length;i++)labels[i]=accounts.get(i).getString("label")+"  ·  "+accounts.get(i).getInt("count")+" 间";
            new AlertDialog.Builder(this).setTitle("切换账号").setItems(labels,(d,which)->{
                try{showRooms(accounts.get(which).getString("id"));}catch(Exception e){profileError("无法读取宿舍",e);}
            }).setNegativeButton("取消",null).setPositiveButton("添加账号 / 宿舍",(d,w)->importFile()).show();
        }catch(Exception e){profileError("无法读取账号",e);}
    }
    private void showCurrentRooms(){
        pending="";
        try{
            SecureStore.ensureProfiles(this);String id=SecureStore.prefs(this).getString("profile_id","");
            if(id.isEmpty()){showAccounts();return;}showRooms(SecureStore.catalog(this).find(id).getString("account"));
        }catch(Exception e){profileError("无法读取宿舍",e);}
    }
    private void showRooms(String account)throws Exception{
        List<JSONObject> rooms=SecureStore.catalog(this).rooms(account);
        if(rooms.isEmpty()){showAccounts();return;}
        String current=SecureStore.prefs(this).getString("profile_id","");
        String[] labels=new String[rooms.size()];
        for(int i=0;i<labels.length;i++)labels[i]=(current.equals(rooms.get(i).getString("id"))?"当前 · ":"")+rooms.get(i).getString("room");
        new AlertDialog.Builder(this).setTitle("选择已保存的宿舍").setItems(labels,(d,which)->{
            try{JSONObject p=rooms.get(which);if(p.getString("id").equals(SecureStore.prefs(this).getString("profile_id",""))){toast("已经是当前宿舍");return;}
                confirmProfile(SecureStore.catalog(this).find(p.getString("id")).getJSONObject("envelope"));
            }catch(Exception e){profileError("无法切换宿舍",e);}
        }).setNegativeButton("取消",null).setPositiveButton("添加宿舍",(d,w)->importFile()).setNeutralButton("移除宿舍",(d,w)->{
            new AlertDialog.Builder(this).setTitle("选择要移除的宿舍").setItems(labels,(x,index)->{
                try{JSONObject p=rooms.get(index);new AlertDialog.Builder(this).setTitle("移除此宿舍？").setMessage(p.getString("room")+"\n仅删除本机保存。移除当前宿舍会暂停检测，之后需重新选择或导入。")
                    .setNegativeButton("取消",null).setPositiveButton("移除",(y,z)->removeProfile(p.optString("id"))).show();
                }catch(Exception e){profileError("无法移除宿舍",e);}
            }).setNegativeButton("取消",null).show();
        }).show();
    }
    private void showPendingProfile(){
        if(pendingProfile!=null&&visible&&!preparing&&!importing){
            JSONObject envelope=pendingProfile;pendingProfile=null;activateProfile(envelope);
        }
    }
    private void confirmProfile(JSONObject envelope){
        if(importing||preparing)return;
        activateProfile(envelope);
    }
    private void activateProfile(JSONObject envelope){
        if(importing||preparing||!visible)return;
        pending="";ConnectionService.stop(this,false);WarmDoor.close();AutoService.stop(this);
        final Object lease=OperationGuard.acquire();
        if(lease==null){toast("门锁操作尚未结束，请结束后再切换");return;}
        profileLease=lease;final String original=SecureStore.capability(this);
        importing=true;publish();toast("正在绑定宿舍…");
        io.submit(()->{
            try{
                NativeApi.Grant room=new NativeApi(envelope).grants().get(0);
                main.post(()->{
                    try{
                        if(!visible||isDestroyed()||isFinishing()||!SecureStore.matchesCapability(this,original))return;
                        AutoService.stop(this);
                        SecureStore.activate(this,envelope,room.roomLabel);
                        updateShortcut();AccessPreparation.warm(this);toast("已切换宿舍，自动检测保持关闭");
                    }catch(Exception e){profileError("切换未完成",e);}
                    finally{OperationGuard.release(lease);profileLease=null;importing=false;refreshProfileSummary();publish();if(visible&&homeVisible)primeDoor();}
                });
            }catch(Exception e){main.post(()->{
                OperationGuard.release(lease);profileLease=null;importing=false;refreshProfileSummary();publish();profileError("切换未完成",e);
            });}
        });
    }
    private void removeProfile(String id){
        if(importing||preparing)return;
        pending="";ConnectionService.stop(this,false);WarmDoor.close();AutoService.stop(this);Object lease=OperationGuard.acquire();
        if(lease==null){toast("门锁操作尚未结束，请稍后移除");return;}
        try{SecureStore.removeProfile(this,id);refreshProfileSummary();syncShortcut();publish();toast("已移除本机保存的宿舍");}
        catch(Exception e){profileError("移除未完成",e);}
        finally{OperationGuard.release(lease);refreshProfileSummary();syncShortcut();publish();}
    }
    private void clearProfiles(){
        new AlertDialog.Builder(this).setTitle("清除所有本机授权？").setMessage("将停止检测并移除全部已保存账号和宿舍。清除后如需使用，请重新导入本人授权。")
            .setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{
                if(importing||preparing)return;pending="";ConnectionService.stop(this,false);WarmDoor.close();AutoService.stop(this);Object lease=OperationGuard.acquire();
                if(lease==null){toast("门锁操作尚未结束，请稍后清除");return;}
                try{SecureStore.clear(this);refreshProfileSummary();syncShortcut();publish();}catch(Exception e){profileError("清除未完成",e);}finally{OperationGuard.release(lease);refreshProfileSummary();syncShortcut();publish();}
            }).show();
    }
    private void syncShortcut(){
        if(!SecureStore.prefs(this).getString("vault","").isEmpty()){updateShortcut();return;}
        ShortcutManager sm=getSystemService(ShortcutManager.class);
        if(sm!=null)try{sm.removeAllDynamicShortcuts();sm.disableShortcuts(Arrays.asList("open"),"请先选择宿舍");}catch(RuntimeException ignored){}
    }


    private void importFile() {
        if(importing){toast("正在核对，请稍等");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try{startActivityForResult(i,70);}catch(RuntimeException e){toast("无法打开系统文件选择器");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==73){manualCompleted=result==RESULT_OK;opening=false;publish();return;}
        if(request==72){if(result==RESULT_OK)continuePending();else pending="";return;}
        if(request!=70 || result!=RESULT_OK || data==null || data.getData()==null)return;
        pending="";importing=true;publish();
        io.submit(()->{
            try{
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new Exception("文件无法读取");byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new Exception("授权文件过大");out.write(b,0,n);}}
                JSONObject envelope=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
                MigrationTrust.verify(envelope);
                runOnUiThread(()->{importing=false;if(isFinishing()||isDestroyed())return;publish();pendingProfile=envelope;showPendingProfile();});
            }catch(Exception e){runOnUiThread(()->{importing=false;if(isFinishing()||isDestroyed())return;publish();new AlertDialog.Builder(this).setTitle("导入未完成").setMessage(e.getMessage()==null?"无法核对本人授权":e.getMessage()).setPositiveButton("知道了",null).show();});}
        });
    }
    private ShortcutInfo shortcut() { return new ShortcutInfo.Builder(this,"open").setShortLabel("宿舍开锁").setLongLabel("开门 · "+SecureStore.prefs(this).getString("room_label","当前宿舍")).setIcon(Icon.createWithResource(this,getResources().getIdentifier("ic_launcher","mipmap",getPackageName())))
        .setIntent(new Intent(this,UnlockActivity.class).setAction("cn.dormnative.OPEN").putExtra("capability",SecureStore.capability(this))).build(); }
    private void updateShortcut(){ShortcutManager sm=getSystemService(ShortcutManager.class);if(sm!=null)try{sm.enableShortcuts(Arrays.asList("open"));sm.setDynamicShortcuts(Arrays.asList(shortcut()));sm.updateShortcuts(Arrays.asList(shortcut()));}catch(RuntimeException ignored){}}
    private void pin(){if(SecureStore.prefs(this).getString("room_label","").isEmpty()){toast("请先导入授权");return;}updateShortcut();ShortcutManager sm=getSystemService(ShortcutManager.class);try{if(sm!=null&&sm.isRequestPinShortcutSupported())sm.requestPinShortcut(shortcut(),null);else toast("请长按应用图标使用宿舍开锁快捷项");}catch(RuntimeException e){toast("请检查桌面快捷方式权限");}}
    private void tile(){if(Build.VERSION.SDK_INT>=33){StatusBarManager sm=getSystemService(StatusBarManager.class);if(sm!=null)try{sm.requestAddTileService(new ComponentName(this,QuickTileService.class),"宿舍开锁",Icon.createWithResource(this,getResources().getIdentifier("ic_tile","drawable",getPackageName())),getMainExecutor(),r->toast("可在控制中心编辑页面查看“宿舍开锁”"));return;}catch(RuntimeException ignored){}}new AlertDialog.Builder(this).setTitle("添加系统入口").setMessage("下拉控制中心 → 编辑快捷开关 → 添加“宿舍开锁”。").setPositiveButton("知道了",null).show();}
    private void diagnostics(){String s="公寓原生快开 "+AppInfo.version(this)+"\n耗时："+SecureStore.prefs(this).getString("last_timing","尚无计时")+"\n设备："+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid："+Build.VERSION.RELEASE+"\n已导入："+!SecureStore.prefs(this).getString("room_label","").isEmpty()+"\n最近结果："+SecureStore.prefs(this).getString("last_result","无")+"\n靠近检测运行："+AutoService.running+"\n观察模式："+AutoService.observation+"\n检测状态："+AutoService.liveStatus+"\n检测详情："+AutoService.diagnostics(this)+"\n最近自动结果："+SecureStore.prefs(this).getString("auto_last","无")+"\n\n本机运行记录：\n"+EventLog.describe(this)+"\n诊断记录本机软件状态；实体门是否打开请由本人确认";ClipboardManager c=getSystemService(ClipboardManager.class);if(c!=null)c.setPrimaryClip(ClipData.newPlainText("原生快开调试信息",s));toast("已复制，不含房间号、人员标识和导入文件");}
    private void importHelp(){new AlertDialog.Builder(this).setTitle("导入自己的宿舍")
        .setMessage("① 自己登录电脑微信“公寓智锁”。\n② 把发布包中的“AI首次授权导出工具包”交给能操作电脑的 AI。它读取本人账号与宿舍资料，生成待签发文件；维护者的工具会自动签出 .dormkey。\n③ 收到自己的 .dormkey 后，点“选择授权文件”，选中即自动绑定，无需再确认。\n\n已拿到文件可直接从第③步开始。待签发 .dormrequest 不能导入。首次绑定需要维护者返回签名文件，尚非完全自助登录；日常开门不用微信。")
        .setNegativeButton("关闭",null).setPositiveButton("选择授权文件",(d,w)->importFile()).show();}
    private void help(){new AlertDialog.Builder(this).setTitle("宿舍快开 "+AppInfo.version(this))
        .setMessage((personal?"请确认已获得本人门锁的有效授权。\n\n":"公开测试版不包含预置宿舍。先导入属于你本人的 .dormkey 文件；尚未接入微信一键登录。\n\n")+"手动开门：在本人门锁旁保持蓝牙和网络开启，点“开门”。\n\n自动预连接：允许附近设备和通知后，打开开门页会主动尝试，退到后台也会继续。连上后保持供按钮接续，开门成功后结束本次预连接。可在首页或运行通知中停止。系统结束进程后需重新打开应用。\n\n接近自动开门仍是试验功能，在设置中。蓝牙无法分辨门内外，请先验证手动开门。\n\nNFC 发卡和门锁硬件设置尚未接入。预连接只建立通信，不会自动发开锁命令；实际开锁须有本次有效授权。")
        .setNegativeButton("关闭",null).setPositiveButton("导入教程",(d,w)->importHelp()).show();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
