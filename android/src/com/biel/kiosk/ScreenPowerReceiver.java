package com.biel.kiosk;
import android.content.*;

public class ScreenPowerReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){
  ScreenPower.schedule(c);String action=i.getAction();
  if(ScreenPower.ACTION_ON.equals(action)){c.getSharedPreferences("screen-runtime",0).edit().putBoolean("scheduleWakeOverride",false).apply();MainActivity.resetIdleClock(c);ScreenPower.wake(c);}
  else if(ScreenPower.ACTION_OFF.equals(action)){c.getSharedPreferences("screen-runtime",0).edit().putBoolean("scheduleWakeOverride",false).apply();if(ScreenPower.scheduledOff(c))ScreenPower.turnOff(c);}
  else if(ScreenPower.scheduledOff(c))ScreenPower.turnOff(c);
  else if(ScreenPower.offRequested){MainActivity.resetIdleClock(c);ScreenPower.wake(c);}
 }
}
