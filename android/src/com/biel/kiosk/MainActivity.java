package com.biel.kiosk;
import android.app.*;
import android.app.admin.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.net.http.SslError;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
 static volatile long lastInteraction=SystemClock.elapsedRealtime();
 static volatile int pageLoads=0;
 static volatile String screenState="active";
 static volatile String webState="loading";
 static volatile String webError="";
 static volatile String webErrorUrl="";
 static volatile long webErrorAt=0;
 static volatile long webRetryAt=0;
 boolean timersPaused=false,resumed=false,loadFailed=false;
 static boolean manualBlack=false;
 View curtain;FrameLayout root;boolean consumingWake=false;
 final Runnable idleTick=()->updateIdle();
 WebView web;String loaded="";boolean locked=false;Handler handler=new Handler();int retryAttempt=0;
 static final String KEYBOARD_GUARD="(function(enabled){var g=window.__infcraftKeyboardGuard;if(!g){g={enabled:false};window.__infcraftKeyboardGuard=g;g.editable=function(el){return el&&(el.isContentEditable||el.tagName==='TEXTAREA'||(el.tagName==='INPUT'&&['text','search','password','email','number','tel','url'].indexOf(el.type)>=0));};g.block=function(e){if(g.enabled&&g.editable(e.target)){if(e.cancelable)e.preventDefault();e.target.blur();}};document.addEventListener('touchstart',g.block,{capture:true,passive:false});document.addEventListener('focusin',g.block,true);}g.enabled=enabled;if(enabled&&g.editable(document.activeElement))document.activeElement.blur();})";
 final Runnable retryLoad=()->{webRetryAt=0;if(web==null||isFinishing()||isDestroyed())return;String target=Config.prefs(this).getString("url",Config.DEFAULT_URL);loaded=target;web.stopLoading();web.loadUrl(target);};
 final Runnable lockTaskSync=()->syncLockTask(Config.prefs(this).getBoolean("kiosk",true));

 void syncKeyboardPolicy(){if(web!=null)web.evaluateJavascript(KEYBOARD_GUARD+"("+Config.prefs(this).getBoolean("kiosk",true)+")",null);}
 WebView createWebView(){
  WebView view=new KioskWebView(this);view.setBackgroundColor(0xff101827);
  WebSettings s=view.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);
  view.setWebViewClient(new WebViewClient(){
   public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){String scheme=r.getUrl().getScheme();return !"https".equals(scheme)&&!"http".equals(scheme);}
   public void onPageStarted(WebView v,String u,android.graphics.Bitmap icon){pageLoads++;loadFailed=false;webState="loading";webRetryAt=0;updateIdle();}
   public void onPageFinished(WebView v,String u){if(!loadFailed){handler.removeCallbacks(retryLoad);retryAttempt=0;webState="ok";webError="";webErrorUrl="";webErrorAt=0;webRetryAt=0;}syncKeyboardPolicy();updateIdle();}
   public void onPageCommitVisible(WebView v,String u){syncKeyboardPolicy();}
   public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())recordLoadError("Erro "+error.getErrorCode()+": "+error.getDescription(),request.getUrl().toString());}
   public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&response.getStatusCode()>=400)recordLoadError("HTTP "+response.getStatusCode()+" ao carregar o site",request.getUrl().toString());}
   public void onReceivedSslError(WebView v,SslErrorHandler sslHandler,SslError error){recordLoadError("Erro de certificado TLS "+error.getPrimaryError(),error.getUrl());sslHandler.cancel();}
   public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){
    String reason=detail.didCrash()?"O processo de exibição do site travou":"O Android encerrou o processo de exibição do site";android.util.Log.e("Kiosk",reason);
    webState="restarting";webError=reason;webErrorUrl=loaded;webErrorAt=System.currentTimeMillis();webRetryAt=0;handler.removeCallbacks(retryLoad);
    root.removeView(v);v.destroy();if(web==v)web=null;timersPaused=false;loaded="";
    handler.postDelayed(()->{if(isFinishing()||isDestroyed())return;web=createWebView();root.addView(web,0,new FrameLayout.LayoutParams(-1,-1));apply();},1000);return true;
   }
  });
  return view;
 }
 void recordLoadError(String message,String url){
  android.util.Log.e("Kiosk","Falha no WebView: "+message+" URL="+url);webState="error";webError=message;webErrorUrl=url;webErrorAt=System.currentTimeMillis();
  if(loadFailed)return;loadFailed=true;handler.removeCallbacks(retryLoad);long delay=Math.min(60000L,5000L*(1L<<Math.min(retryAttempt,3)));retryAttempt++;webRetryAt=System.currentTimeMillis()+delay;handler.postDelayed(retryLoad,delay);
 }
 public void onCreate(Bundle b){super.onCreate(b);restoreRestState();Config.token(this);startForegroundService(new Intent(this,AdminService.class));
  root=new FrameLayout(this);web=createWebView();root.addView(web,new FrameLayout.LayoutParams(-1,-1));curtain=new View(this);curtain.setBackgroundColor(0xff000000);curtain.setVisibility(View.GONE);curtain.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(curtain,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
  Config.prefs(this).registerOnSharedPreferenceChangeListener(this);apply();
  if(!Config.prefs(this).getBoolean("onboarded",false))new AlertDialog.Builder(this).setTitle("Seu Kiosk está pronto").setMessage("Painel: "+Config.address()+"\n\nChave de administração:\n"+Config.token(this)+"\n\nGuarde esta chave. O painel usa HTTPS após instalar o certificado local pelo USB.").setCancelable(false).setPositiveButton("Anotei, abrir dashboard",(d,w)->Config.prefs(this).edit().putBoolean("onboarded",true).apply()).show();
 }
 void apply(){SharedPreferences p=Config.prefs(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);updateIdle();
  String o=p.getString("orientation","landscape");setRequestedOrientation(o.equals("landscape")?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:o.equals("portrait")?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
  getWindow().getDecorView().setSystemUiVisibility(5894);boolean k=p.getBoolean("kiosk",true);
  boolean keyboardWasBlocked=(getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)!=0;
  android.view.inputmethod.InputMethodManager input=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
  if(k){if(web!=null){input.hideSoftInputFromWindow(web.getWindowToken(),0);if(!keyboardWasBlocked){web.evaluateJavascript("(function(){var el=document.activeElement;if(el&&el.blur)el.blur();})()",null);web.clearFocus();}}getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);}else{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);if(keyboardWasBlocked&&web!=null)input.restartInput(web);}
  syncKeyboardPolicy();syncLockTask(k);
  String u=p.getString("url",Config.DEFAULT_URL);if(web!=null&&!u.equals(loaded)){loaded=u;web.loadUrl(u);}
 }
 void syncLockTask(boolean kiosk){
  DevicePolicyManager d=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);if(!Config.owner(this))return;ComponentName admin=new ComponentName(this,AdminReceiver.class);
  try{d.setLockTaskPackages(admin,new String[]{getPackageName()});d.setLockTaskFeatures(admin,0);d.setKeyguardDisabled(admin,kiosk);}catch(RuntimeException e){android.util.Log.e("Kiosk","Falha ao aplicar política kiosk",e);}
  locked=((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED;
  try{
   if(kiosk&&!locked){if(!resumed||!hasWindowFocus())return;startLockTask();locked=true;}
   else if(!kiosk&&locked){stopLockTask();locked=false;}
  }catch(IllegalArgumentException|IllegalStateException|SecurityException e){locked=((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED;android.util.Log.w("Kiosk","Modo kiosk aguardando Activity em primeiro plano",e);}
 }
 void restoreRestState(){SharedPreferences p=getSharedPreferences("screen-runtime",0);int boot=android.provider.Settings.Global.getInt(getContentResolver(),"boot_count",-1);long saved=p.getLong("lastInteraction",-1);if(boot>=0&&p.getInt("boot",-2)==boot&&saved>=0&&saved<=SystemClock.elapsedRealtime()){lastInteraction=saved;manualBlack=p.getBoolean("manualBlack",false);}else{lastInteraction=SystemClock.elapsedRealtime();manualBlack=false;}persistRestState();}
 void persistRestState(){getSharedPreferences("screen-runtime",0).edit().putInt("boot",android.provider.Settings.Global.getInt(getContentResolver(),"boot_count",-1)).putLong("lastInteraction",lastInteraction).putBoolean("manualBlack",manualBlack).apply();}
 void updateIdle(){
  if(web==null||curtain==null)return;handler.removeCallbacks(idleTick);SharedPreferences p=Config.prefs(this);
  long idle=Math.max(0,SystemClock.elapsedRealtime()-lastInteraction);long dim=p.getInt("idleDimSeconds",120)*1000L,black=p.getInt("idleBlackSeconds",300)*1000L;
  boolean dark=manualBlack||(black>0&&idle>=black);boolean low=!dark&&dim>0&&idle>=dim;screenState=dark?"black":low?"dimmed":"active";
  if(dark&&!timersPaused){web.onPause();web.pauseTimers();timersPaused=true;}else if(!dark&&timersPaused){web.resumeTimers();web.onResume();timersPaused=false;}
  curtain.setVisibility(dark?View.VISIBLE:View.GONE);web.setImportantForAccessibility(dark?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
  WindowManager.LayoutParams a=getWindow().getAttributes();int normal=p.getInt("brightness",70);a.screenBrightness=dark?0f:(low?Math.min(normal,p.getInt("idleBrightness",10)):normal)/100f;getWindow().setAttributes(a);
  if(!dark){long next=Long.MAX_VALUE;if(dim>idle)next=Math.min(next,dim-idle);if(black>idle)next=Math.min(next,black-idle);if(next!=Long.MAX_VALUE)handler.postDelayed(idleTick,next);}
 }
 void wakeScreen(){manualBlack=false;lastInteraction=SystemClock.elapsedRealtime();persistRestState();updateIdle();}
 public boolean dispatchTouchEvent(android.view.MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)consumingWake=!"active".equals(screenState);if(consumingWake){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)wakeScreen();if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)consumingWake=false;return true;}lastInteraction=SystemClock.elapsedRealtime();persistRestState();updateIdle();return super.dispatchTouchEvent(event);}
 public void onSharedPreferenceChanged(SharedPreferences p,String key){runOnUiThread(()->{if(key.equals("reload")){handler.removeCallbacks(retryLoad);retryAttempt=0;webRetryAt=0;if(web!=null){web.stopLoading();loaded=p.getString("url",Config.DEFAULT_URL);web.loadUrl(loaded);}updateIdle();}else if(key.equals("screenCommandSerial")){if("black".equals(p.getString("screenCommand","wake"))){manualBlack=true;persistRestState();updateIdle();}else wakeScreen();}else if(!key.equals("screenCommand"))apply();});}
 protected void onResume(){super.onResume();resumed=true;handler.post(lockTaskSync);}
 protected void onPause(){resumed=false;super.onPause();}
 public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f){getWindow().getDecorView().setSystemUiVisibility(5894);handler.post(lockTaskSync);}}
 public void onBackPressed(){if(!Config.prefs(this).getBoolean("kiosk",true)&&web!=null&&web.canGoBack())web.goBack();}
 protected void onDestroy(){Config.prefs(this).unregisterOnSharedPreferenceChangeListener(this);handler.removeCallbacksAndMessages(null);persistRestState();if(web!=null){web.resumeTimers();web.destroy();}super.onDestroy();}
}
