package com.biel.kiosk;
import android.content.*;
import android.app.admin.DevicePolicyManager;
import java.security.SecureRandom;
import java.net.*;
import java.util.*;
import org.json.*;
class Config {
 static final String DEFAULT_URL="https://dash.infcraft.net/d/ag3m3od6wl0xse/monitor?orgId=3&from=now-3h&to=now&timezone=browser&var-PVE=$__all&var-ip=$__all&refresh=1m&kiosk";
 static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("kiosk",0);}
 static synchronized String token(Context c){String t=prefs(c).getString("token",null);if(t==null){byte[] b=new byte[16];new SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v));t=s.toString();prefs(c).edit().putString("token",t).commit();}return t;}
 static boolean owner(Context c){return ((DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE)).isDeviceOwnerApp(c.getPackageName());}
 static String address(){try {for(NetworkInterface n:Collections.list(NetworkInterface.getNetworkInterfaces()))for(InetAddress a:Collections.list(n.getInetAddresses()))if(a instanceof Inet4Address&&!a.isLoopbackAddress())return "https://"+a.getHostAddress()+":8080";}catch(Exception e){}return "Wi-Fi desconectado";}
 static JSONObject state(Context c)throws Exception {android.content.SharedPreferences p=prefs(c);long retry=MainActivity.webRetryAt==0?0:Math.max(0,(MainActivity.webRetryAt-System.currentTimeMillis()+999)/1000);return new JSONObject().put("url",p.getString("url",DEFAULT_URL)).put("brightness",p.getInt("brightness",70)).put("orientation",p.getString("orientation","landscape")).put("kiosk",p.getBoolean("kiosk",true)).put("owner",owner(c)).put("address",address()).put("model",android.os.Build.MODEL).put("version","1.6.0").put("idleDimSeconds",p.getInt("idleDimSeconds",120)).put("idleBlackSeconds",p.getInt("idleBlackSeconds",300)).put("idleBrightness",p.getInt("idleBrightness",10)).put("screenOffIdleSeconds",p.getInt("screenOffIdleSeconds",0)).put("screenOffScheduleEnabled",p.getBoolean("screenOffScheduleEnabled",false)).put("screenOffTime",p.getString("screenOffTime","22:00")).put("screenOnTime",p.getString("screenOnTime","06:00")).put("displayInteractive",ScreenPower.interactive(c)).put("scheduledScreenOff",ScreenPower.scheduledOff(c)).put("pageLoads",MainActivity.pageLoads).put("screenState",MainActivity.screenState).put("idleSeconds",Math.max(0,(android.os.SystemClock.elapsedRealtime()-MainActivity.lastInteraction)/1000)).put("uptimeSeconds",android.os.SystemClock.elapsedRealtime()/1000).put("webState",MainActivity.webState).put("webError",MainActivity.webError).put("webErrorUrl",MainActivity.webErrorUrl).put("webErrorAt",MainActivity.webErrorAt).put("webRetrySeconds",retry);}
 static int integer(JSONObject j,String key,int fallback)throws Exception {if(!j.has(key))return fallback;Object value=j.get(key);if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue())throw new Exception("Valor inteiro obrigatório: "+key);return ((Number)value).intValue();}
 static void save(Context c,JSONObject j)throws Exception {
  String url=j.getString("url");URI u=new URI(url);if(!("https".equals(u.getScheme())||"http".equals(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)throw new Exception("Informe uma URL http ou https válida.");
  int brightness=j.getInt("brightness");if(brightness<5||brightness>100)throw new Exception("Brilho deve ficar entre 5 e 100.");
  String o=j.getString("orientation");if(!Arrays.asList("landscape","portrait","auto").contains(o))throw new Exception("Orientação inválida.");
  int dim=integer(j,"idleDimSeconds",prefs(c).getInt("idleDimSeconds",120));
  int black=integer(j,"idleBlackSeconds",prefs(c).getInt("idleBlackSeconds",300));
  int idle=integer(j,"idleBrightness",prefs(c).getInt("idleBrightness",10));
  int screenOff=integer(j,"screenOffIdleSeconds",prefs(c).getInt("screenOffIdleSeconds",0));
  boolean schedule=j.has("screenOffScheduleEnabled")?j.getBoolean("screenOffScheduleEnabled"):prefs(c).getBoolean("screenOffScheduleEnabled",false);
  String offTime=j.has("screenOffTime")?j.getString("screenOffTime"):prefs(c).getString("screenOffTime","22:00"),onTime=j.has("screenOnTime")?j.getString("screenOnTime"):prefs(c).getString("screenOnTime","06:00");
  if((dim!=0&&(dim<5||dim>86400))||(black!=0&&(black<5||black>86400)))throw new Exception("Tempo deve ser 0 (desativado) ou entre 5 e 86400 segundos.");
  if(dim>0&&black>0&&black<=dim)throw new Exception("Tela preta deve iniciar depois da redução de brilho.");
  if(idle<1||idle>100)throw new Exception("Brilho de descanso deve ficar entre 1 e 100.");
  if(screenOff!=0&&(screenOff<5||screenOff>86400))throw new Exception("Desligamento real deve ser 0 ou entre 5 e 86400 segundos.");
  if(!offTime.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")||!onTime.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))throw new Exception("Use horários válidos no formato HH:mm.");
  if(schedule&&offTime.equals(onTime))throw new Exception("Os horários de desligar e ligar devem ser diferentes.");
  if((screenOff>0||schedule)&&!owner(c))throw new Exception("O desligamento real exige que o app seja administrador principal.");
  c.getSharedPreferences("screen-runtime",0).edit().putBoolean("scheduleWakeOverride",false).apply();prefs(c).edit().putInt("idleDimSeconds",dim).putInt("idleBlackSeconds",black).putInt("idleBrightness",idle).putInt("screenOffIdleSeconds",screenOff).putBoolean("screenOffScheduleEnabled",schedule).putString("screenOffTime",offTime).putString("screenOnTime",onTime).putString("url",url).putInt("brightness",brightness).putString("orientation",o).putBoolean("kiosk",j.getBoolean("kiosk")).apply();ScreenPower.schedule(c);
 }
}
