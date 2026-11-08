package com.biel.kiosk;
import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;
/** Keep normal touch navigation while denying soft-keyboard input in kiosk. */
public class KioskWebView extends WebView {
 public KioskWebView(Context context){super(context);}
 public boolean onCheckIsTextEditor(){return !Config.prefs(getContext()).getBoolean("kiosk",true)&&super.onCheckIsTextEditor();}
 public InputConnection onCreateInputConnection(EditorInfo info){return Config.prefs(getContext()).getBoolean("kiosk",true)?null:super.onCreateInputConnection(info);}
}
