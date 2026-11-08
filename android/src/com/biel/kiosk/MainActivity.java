package com.biel.kiosk;
import android.app.*;
import android.app.admin.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
 static volatile long lastInteraction=SystemClock.elapsedRealtime();
 static volatile int pageLoads=0;
 boolean timersPaused=false;
 static volatile String screenState="active";
 static boolean manualBlack=false;
 View curtain; boolean consumingWake=false;
 final Runnable idleTick=()->updateIdle();
 WebView web; String loaded=""; boolean locked=false; Handler handler=new Handler();
 static final String KEYBOARD_GUARD="(function(enabled){var g=window.__infcraftKeyboardGuard;if(!g){g={enabled:false};window.__infcraftKeyboardGuard=g;g.editable=function(el){return el&&(el.isContentEditable||el.tagName==='TEXTAREA'||(el.tagName==='INPUT'&&['text','search','password','email','number','tel','url'].indexOf(el.type)>=0));};g.block=function(e){if(g.enabled&&g.editable(e.target)){if(e.cancelable)e.preventDefault();e.target.blur();}};document.addEventListener('touchstart',g.block,{capture:true,passive:false});document.addEventListener('focusin',g.block,true);}g.enabled=enabled;if(enabled&&g.editable(document.activeElement))document.activeElement.blur();})";
 void syncKeyboardPolicy(){web.evaluateJavascript(KEYBOARD_GUARD+"("+Config.prefs(this).getBoolean("kiosk",true)+")",null);}
 public void onCreate(Bundle b){super.onCreate(b);restoreRestState();Config.token(this);startForegroundService(new Intent(this,AdminService.class));
  web=new KioskWebView(this);FrameLayout root=new FrameLayout(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));curtain=new View(this);curtain.setBackgroundColor(0xff000000);curtain.setVisibility(View.GONE);curtain.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(curtain,new FrameLayout.LayoutParams(-1,-1));setContentView(root);web.setBackgroundColor(0xff101827);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);
  web.setWebViewClient(new WebViewClient(){
   public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){String scheme=r.getUrl().getScheme();return !"https".equals(scheme)&&!"http".equals(scheme);}
   public void onPageStarted(WebView v,String u,android.graphics.Bitmap icon){pageLoads++;updateIdle();}
   public void onPageFinished(WebView v,String u){syncKeyboardPolicy();updateIdle();}
   public void onPageCommitVisible(WebView v,String u){syncKeyboardPolicy();}
  });
  Config.prefs(this).registerOnSharedPreferenceChangeListener(this);apply();
  if(!Config.prefs(this).getBoolean("onboarded",false))new AlertDialog.Builder(this).setTitle("Seu Kiosk está pronto").setMessage("Painel: "+Config.address()+"\n\nChave de administração:\n"+Config.token(this)+"\n\nGuarde esta chave. O painel usa HTTPS após instalar o certificado local pelo USB.").setCancelable(false).setPositiveButton("Anotei, abrir dashboard",(d,w)->Config.prefs(this).edit().putBoolean("onboarded",true).apply()).show();
 }
 void apply(){SharedPreferences p=Config.prefs(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);updateIdle();
  String o=p.getString("orientation","landscape");setRequestedOrientation(o.equals("landscape")?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:o.equals("portrait")?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
  getWindow().getDecorView().setSystemUiVisibility(5894);
  DevicePolicyManager d=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);boolean k=p.getBoolean("kiosk",true);locked=((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getLockTaskModeState()==ActivityManager.LOCK_TASK_MODE_LOCKED;
  boolean keyboardWasBlocked=(getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)!=0;
  android.view.inputmethod.InputMethodManager input=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
  if(k){input.hideSoftInputFromWindow(web.getWindowToken(),0);if(!keyboardWasBlocked){web.evaluateJavascript("(function(){var el=document.activeElement;if(el&&el.blur)el.blur();})()",null);web.clearFocus();}getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);}else{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);if(keyboardWasBlocked)input.restartInput(web);}

  syncKeyboardPolicy();
  if(Config.owner(this)){ComponentName admin=new ComponentName(this,AdminReceiver.class);d.setLockTaskPackages(admin,new String[]{getPackageName()});d.setLockTaskFeatures(admin,0);d.setKeyguardDisabled(admin,k);if(k&&!locked){startLockTask();locked=true;}if(!k&&locked){stopLockTask();locked=false;}}
  String u=p.getString("url",Config.DEFAULT_URL);if(!u.equals(loaded)){loaded=u;web.loadUrl(u);}
 }
 void restoreRestState(){SharedPreferences p=getSharedPreferences("screen-runtime",0);int boot=android.provider.Settings.Global.getInt(getContentResolver(),"boot_count",-1);long saved=p.getLong("lastInteraction",-1);if(boot>=0&&p.getInt("boot",-2)==boot&&saved>=0&&saved<=SystemClock.elapsedRealtime()){lastInteraction=saved;manualBlack=p.getBoolean("manualBlack",false);}else{lastInteraction=SystemClock.elapsedRealtime();manualBlack=false;}persistRestState();}
 void persistRestState(){getSharedPreferences("screen-runtime",0).edit().putInt("boot",android.provider.Settings.Global.getInt(getContentResolver(),"boot_count",-1)).putLong("lastInteraction",lastInteraction).putBoolean("manualBlack",manualBlack).apply();}
 void updateIdle(){
  handler.removeCallbacks(idleTick);SharedPreferences p=Config.prefs(this);
  long idle=Math.max(0,SystemClock.elapsedRealtime()-lastInteraction);
  long dim=p.getInt("idleDimSeconds",120)*1000L,black=p.getInt("idleBlackSeconds",300)*1000L;
  boolean dark=manualBlack||(black>0&&idle>=black);boolean low=!dark&&dim>0&&idle>=dim;
  screenState=dark?"black":low?"dimmed":"active";
  if(dark&&!timersPaused){web.onPause();web.pauseTimers();timersPaused=true;}else if(!dark&&timersPaused){web.resumeTimers();web.onResume();timersPaused=false;}
  curtain.setVisibility(dark?View.VISIBLE:View.GONE);
  web.setImportantForAccessibility(dark?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
  WindowManager.LayoutParams a=getWindow().getAttributes();int normal=p.getInt("brightness",70);
  a.screenBrightness=dark?0f:(low?Math.min(normal,p.getInt("idleBrightness",10)):normal)/100f;
  getWindow().setAttributes(a);
  if(!dark){long next=Long.MAX_VALUE;if(dim>idle)next=Math.min(next,dim-idle);if(black>idle)next=Math.min(next,black-idle);if(next!=Long.MAX_VALUE)handler.postDelayed(idleTick,next);}
 }
 void wakeScreen(){manualBlack=false;lastInteraction=SystemClock.elapsedRealtime();persistRestState();updateIdle();}
 public boolean dispatchTouchEvent(android.view.MotionEvent event){
  if(event.getActionMasked()==MotionEvent.ACTION_DOWN)consumingWake=!"active".equals(screenState);
  if(consumingWake){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)wakeScreen();if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)consumingWake=false;return true;}
  lastInteraction=SystemClock.elapsedRealtime();persistRestState();updateIdle();return super.dispatchTouchEvent(event);
 }
 public void onSharedPreferenceChanged(SharedPreferences p,String key){runOnUiThread(()->{if(key.equals("reload")){web.stopLoading();loaded=p.getString("url",Config.DEFAULT_URL);web.loadUrl(loaded);updateIdle();}else if(key.equals("screenCommandSerial")){if("black".equals(p.getString("screenCommand","wake"))){manualBlack=true;persistRestState();updateIdle();}else wakeScreen();}else if(!key.equals("screenCommand"))apply();});}
 public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)getWindow().getDecorView().setSystemUiVisibility(5894);}
 public void onBackPressed(){if(!Config.prefs(this).getBoolean("kiosk",true)&&web.canGoBack())web.goBack();}
 protected void onDestroy(){Config.prefs(this).unregisterOnSharedPreferenceChangeListener(this);handler.removeCallbacksAndMessages(null);persistRestState();web.resumeTimers();web.destroy();super.onDestroy();}
}
