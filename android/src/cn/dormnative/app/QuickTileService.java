package cn.dormnative.app;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
public final class QuickTileService extends TileService {
 @Override public void onStartListening(){Tile t=getQsTile();if(t==null)return;t.setLabel("宿舍开锁");t.setState(Tile.STATE_INACTIVE);if(Build.VERSION.SDK_INT>=29)t.setSubtitle(SecureStore.prefs(this).getString("room_label","").isEmpty()?"请先选择宿舍":"当前宿舍 · 蓝牙直连");t.updateTile();}
 @Override public void onClick(){super.onClick();final String target=SecureStore.capability(this);Runnable r=()->{Intent i=new Intent(this,UnlockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("capability",target);if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,12,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));else startActivityAndCollapse(i);};if(isLocked())unlockAndRun(r);else r.run();}
}
