package com.biel.kiosk;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.PowerManager;
import java.util.Calendar;

final class ScreenPower {
 static final String ACTION_OFF="com.biel.kiosk.SCREEN_OFF";
 static final String ACTION_ON="com.biel.kiosk.SCREEN_ON";
 static final int OFF_ALARM=2101,ON_ALARM=2102;
 static volatile boolean offRequested=false;

 static boolean interactive(Context c){return ((PowerManager)c.getSystemService(Context.POWER_SERVICE)).isInteractive();}
 static int minute(String time){String[] p=time.split(":",-1);return Integer.parseInt(p[0])*60+Integer.parseInt(p[1]);}
 static boolean scheduledOff(Context c){
  android.content.SharedPreferences p=Config.prefs(c);if(!p.getBoolean("screenOffScheduleEnabled",false))return false;
  if(c.getSharedPreferences("screen-runtime",0).getBoolean("scheduleWakeOverride",false))return false;
  Calendar now=Calendar.getInstance();int current=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE),off=minute(p.getString("screenOffTime","22:00")),on=minute(p.getString("screenOnTime","06:00"));
  return off<on?current>=off&&current<on:current>=off||current<on;
 }
 static PendingIntent alarmIntent(Context c,String action,int code){return PendingIntent.getBroadcast(c,code,new Intent(c,ScreenPowerReceiver.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static long nextTime(String value){Calendar now=Calendar.getInstance(),next=(Calendar)now.clone();int m=minute(value);next.set(Calendar.HOUR_OF_DAY,m/60);next.set(Calendar.MINUTE,m%60);next.set(Calendar.SECOND,0);next.set(Calendar.MILLISECOND,0);if(!next.after(now))next.add(Calendar.DAY_OF_YEAR,1);return next.getTimeInMillis();}
 static void schedule(Context c){
  AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);PendingIntent off=alarmIntent(c,ACTION_OFF,OFF_ALARM),on=alarmIntent(c,ACTION_ON,ON_ALARM);alarms.cancel(off);alarms.cancel(on);
  android.content.SharedPreferences p=Config.prefs(c);if(!p.getBoolean("screenOffScheduleEnabled",false))return;
  alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,nextTime(p.getString("screenOffTime","22:00")),off);
  alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,nextTime(p.getString("screenOnTime","06:00")),on);
 }
 static boolean turnOff(Context c){
  DevicePolicyManager policy=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);if(!policy.isDeviceOwnerApp(c.getPackageName()))return false;
  offRequested=true;policy.lockNow();return true;
 }
 static void wake(Context c){
  offRequested=false;PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
  PowerManager.WakeLock wake=power.newWakeLock(PowerManager.FULL_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.ON_AFTER_RELEASE,"InfcraftKiosk:screenWake");wake.acquire(10000);
  c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP));
 }
 static void wakeManually(Context c){c.getSharedPreferences("screen-runtime",0).edit().putBoolean("scheduleWakeOverride",true).apply();wake(c);}
 private ScreenPower(){}
}
