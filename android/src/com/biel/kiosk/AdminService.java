package com.biel.kiosk;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
public class AdminService extends Service {
 ServerSocket server;
 final ThreadPoolExecutor pool=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
 public void onCreate(){super.onCreate();NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);n.createNotificationChannel(new NotificationChannel("admin","Administração Kiosk",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  startForeground(1,new Notification.Builder(this,"admin").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Infcraft Kiosk ativo").setContentText(Config.address()).setContentIntent(pi).build());
  Config.token(this);startServer();
 }
 void startServer(){new Thread(()->{try{ServerSocket listener=TlsConfig.ready(this)?TlsConfig.listen(this):new ServerSocket(8080,16,InetAddress.getLoopbackAddress());server=listener;while(!listener.isClosed()){Socket client=listener.accept();client.setSoTimeout(5000);try{pool.execute(()->handle(client));}catch(RejectedExecutionException e){client.close();}}}catch(Exception e){if(server==null||!server.isClosed())android.util.Log.e("Kiosk","Servidor encerrado",e);}},"kiosk-https").start();}
 void restartServer(){try{if(server!=null)server.close();}catch(IOException e){}startServer();}
 public int onStartCommand(Intent i,int f,int id){return START_STICKY;}
 public IBinder onBind(Intent i){return null;}
 public void onDestroy(){try{if(server!=null)server.close();}catch(IOException e){}pool.shutdownNow();super.onDestroy();}
 String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int v;while((v=in.read())!=-1){if(v==10)return new String(b.toByteArray(),StandardCharsets.US_ASCII).replace("\r","");if(b.size()>=4096)throw new IOException("Header too long");b.write(v);}throw new EOFException();}
 void send(Socket s,int code,String type,byte[] body)throws IOException{send(s,code,type,body,"");}
 void send(Socket s,int code,String type,byte[] body,String extra)throws IOException{String reason=code==200?"OK":code==401?"Unauthorized":code==404?"Not Found":code==403?"Forbidden":"Bad Request";OutputStream o=s.getOutputStream();o.write(("HTTP/1.1 "+code+" "+reason+"\r\n"+extra+"Content-Type: "+type+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'\r\n\r\n").getBytes(StandardCharsets.US_ASCII));o.write(body);o.flush();}
 void json(Socket s,int code,JSONObject j)throws IOException{send(s,code,"application/json; charset=utf-8",j.toString().getBytes(StandardCharsets.UTF_8));}
 JSONObject body(InputStream in,Map<String,String> headers,int max)throws Exception {int len=Integer.parseInt(headers.getOrDefault("content-length","0"));if(len<2||len>max)throw new Exception("Tamanho inválido.");byte[] bytes=new byte[len];int pos=0;while(pos<len){int n=in.read(bytes,pos,len-pos);if(n<0)throw new EOFException();pos+=n;}return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
 void handle(Socket socket){try(Socket s=socket){try{
  InputStream in=new BufferedInputStream(s.getInputStream());String[] request=line(in).split(" ");if(request.length!=3)throw new Exception("Requisição inválida.");String method=request[0],path=request[1];Map<String,String> headers=new HashMap<>();int total=0;String l;while(!(l=line(in)).isEmpty()){total+=l.length();if(total>16384)throw new Exception("Cabeçalho muito grande.");int colon=l.indexOf(':');if(colon<1)throw new Exception("Cabeçalho inválido.");String name=l.substring(0,colon).toLowerCase(Locale.ROOT);if(headers.containsKey(name))throw new Exception("Cabeçalho repetido.");headers.put(name,l.substring(colon+1).trim());}
  if(headers.containsKey("transfer-encoding"))throw new Exception("Transferência não suportada.");
  int incomingLength=Integer.parseInt(headers.getOrDefault("content-length","0"));if(incomingLength<0||incomingLength>65536)throw new Exception("Tamanho inválido.");byte[] incoming=new byte[incomingLength];int received=0;while(received<incomingLength){int count=in.read(incoming,received,incomingLength-received);if(count<0)throw new EOFException();received+=count;}in=new ByteArrayInputStream(incoming);

  if(method.equals("GET")&&(path.equals("/")||path.equals("/app.js")||path.equals("/style.css")||path.equals("/infcraft-logo.svg")||path.equals("/infcraft-wallpaper.webp"))){String file=path.equals("/")?"index.html":path.substring(1);ByteArrayOutputStream data=new ByteArrayOutputStream();try(InputStream asset=getAssets().open(file)){byte[] b=new byte[4096];int count;while((count=asset.read(b))!=-1)data.write(b,0,count);}String type=file.endsWith("js")?"application/javascript":file.endsWith("css")?"text/css":file.endsWith("svg")?"image/svg+xml":file.endsWith("webp")?"image/webp":"text/html; charset=utf-8";send(s,200,type,data.toByteArray());return;}
  boolean secure=s instanceof javax.net.ssl.SSLSocket;
  String origin=headers.get("origin");
  if(method.equals("POST")&&origin!=null&&!origin.equals((secure?"https://":"http://")+headers.getOrDefault("host",""))){json(s,403,new JSONObject().put("error","Origem não permitida."));return;}
  if(method.equals("POST")&&!headers.getOrDefault("content-type","").split(";",2)[0].trim().equals("application/json"))throw new Exception("Use JSON.");
  if(method.equals("POST")&&path.equals("/api/logout")){WebSessions.revoke(this,headers);send(s,200,"application/json", "{}".getBytes(StandardCharsets.UTF_8),"Set-Cookie: kiosk_session=; Path=/; HttpOnly; SameSite=Strict; Secure; Max-Age=0\r\n");return;}
  String auth=headers.getOrDefault("authorization","");boolean bearer=MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),("Bearer "+Config.token(this)).getBytes(StandardCharsets.UTF_8));if(!bearer&&!WebSessions.valid(this,headers)){json(s,401,new JSONObject().put("error","Chave de administração inválida."));return;}
  if(method.equals("POST")&&path.equals("/api/login")){
   if(!secure||!bearer){json(s,401,new JSONObject().put("error","Entre com a chave pelo HTTPS."));return;}
   JSONObject body=body(in,headers,8192);boolean remember=body.optBoolean("remember",false);WebSessions.revoke(this,headers);String value=WebSessions.create(this,remember);
   send(s,200,"application/json; charset=utf-8",Config.state(this).toString().getBytes(StandardCharsets.UTF_8),WebSessions.header(value,remember,true));return;
  }
  if(method.equals("POST")&&path.equals("/api/tls")){
   if(!s.getInetAddress().isLoopbackAddress()||!bearer){json(s,403,new JSONObject().put("error","Provisionamento somente por USB com chave."));return;}
   TlsConfig.install(this,body(in,headers,65536));json(s,200,new JSONObject().put("restartRequired",false));new Handler(getMainLooper()).postDelayed(()->restartServer(),500);return;
  }
  if(method.equals("GET")&&path.equals("/api/state")){json(s,200,Config.state(this));return;}
  if(method.equals("POST")&&(path.equals("/api/config")||path.equals("/api/reload")||path.equals("/api/screen"))){if(!headers.getOrDefault("content-type","").startsWith("application/json"))throw new Exception("Use JSON.");int len=Integer.parseInt(headers.getOrDefault("content-length","0"));if(len<2||len>8192)throw new Exception("Tamanho inválido.");byte[] bytes=new byte[len];int pos=0,n;while(pos<len){n=in.read(bytes,pos,len-pos);if(n<0)throw new EOFException();pos+=n;}JSONObject body=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(path.equals("/api/config"))Config.save(this,body);else if(path.equals("/api/screen")){String action=body.getString("action");if(action.equals("off")){if(!ScreenPower.turnOff(this))throw new Exception("O desligamento real exige que o app seja administrador principal.");}else if(action.equals("powerWake")){MainActivity.resetIdleClock(this);ScreenPower.wakeManually(this);}else{if(!action.equals("black")&&!action.equals("wake"))throw new Exception("Ação inválida.");Config.prefs(this).edit().putString("screenCommand",action).putLong("screenCommandSerial",System.nanoTime()).apply();}}else Config.prefs(this).edit().putLong("reload",System.currentTimeMillis()).apply();json(s,200,Config.state(this));return;}
  json(s,404,new JSONObject().put("error","Rota inexistente."));
 }catch(Exception e){try{json(s,400,new JSONObject().put("error",e.getMessage()==null?"Requisição inválida.":e.getMessage()));}catch(Exception ignored){}}}catch(IOException ignored){} }
}
