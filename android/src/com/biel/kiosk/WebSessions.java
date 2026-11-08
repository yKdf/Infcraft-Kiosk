package com.biel.kiosk;
import android.content.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
class WebSessions {
 static String hash(String value)throws Exception {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format("%02x",v));return b.toString();}
 static String cookie(Map<String,String> headers){String value=headers.getOrDefault("cookie","");for(String item:value.split(";")){String[] pair=item.trim().split("=",2);if(pair.length==2&&pair[0].equals("kiosk_session"))return pair[1];}return "";}
 static boolean valid(Context c,Map<String,String> headers)throws Exception {String value=cookie(headers);return value.matches("[0-9a-f]{64}")&&c.getSharedPreferences("sessions",0).getLong(hash(value),0)>System.currentTimeMillis();}
 static String create(Context c,boolean remember)throws Exception {SharedPreferences p=c.getSharedPreferences("sessions",0);SharedPreferences.Editor edit=p.edit();long now=System.currentTimeMillis();for(Map.Entry<String,?> e:p.getAll().entrySet())if(!(e.getValue() instanceof Long)||((Long)e.getValue())<=now)edit.remove(e.getKey());edit.apply();if(p.getAll().size()>=64)throw new Exception("Limite de sessões atingido.");byte[] random=new byte[32];new SecureRandom().nextBytes(random);StringBuilder out=new StringBuilder();for(byte b:random)out.append(String.format("%02x",b));String value=out.toString();p.edit().putLong(hash(value),now+(remember?2592000000L:43200000L)).commit();return value;}
 static void revoke(Context c,Map<String,String> headers)throws Exception {c.getSharedPreferences("sessions",0).edit().remove(hash(cookie(headers))).commit();}
 static String header(String value,boolean remember,boolean secure){return "Set-Cookie: kiosk_session="+value+"; Path=/; HttpOnly; SameSite=Strict"+(secure?"; Secure":"")+(remember?"; Max-Age=2592000":"")+"\r\n";}
}
