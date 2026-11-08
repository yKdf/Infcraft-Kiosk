package com.biel.kiosk;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context c, Intent i) { if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {c.startForegroundService(new Intent(c,AdminService.class));c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));} }
}
