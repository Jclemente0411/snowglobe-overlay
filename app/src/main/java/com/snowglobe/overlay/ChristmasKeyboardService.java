package com.snowglobe.overlay;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * Native Android IME. It remains a separate window, not a floating overlay.
 * Typing stays on-device and is never recorded by SnowGlobe.
 */
public final class ChristmasKeyboardService extends InputMethodService {
    private SharedPreferences preferences;
    private FastKeyboardView keyboard;

    @Override public void onCreate(){
        super.onCreate();
        preferences=getSharedPreferences("snowglobe",MODE_PRIVATE);
    }
    @Override public View onCreateInputView(){
        keyboard=new FastKeyboardView(this,preferences,new FastKeyboardView.InputActions(){
            @Override public void commit(String value){
                InputConnection ic=getCurrentInputConnection();
                if(ic!=null)ic.commitText(value,1);
            }
            @Override public void delete(){
                InputConnection ic=getCurrentInputConnection();
                if(ic!=null)ic.deleteSurroundingText(1,0);
            }
            @Override public void enter(){
                InputConnection ic=getCurrentInputConnection();
                if(ic==null)return;
                EditorInfo info=getCurrentInputEditorInfo();
                int action=info==null?EditorInfo.IME_ACTION_NONE:info.imeOptions&EditorInfo.IME_MASK_ACTION;
                if(action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED
                     &&ic.performEditorAction(action))return;
                ic.commitText("\n",1);
            }
        });
        return keyboard;
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        Window window=getWindow()==null?null:getWindow().getWindow();
        if(window!=null){
            window.setNavigationBarColor(Color.rgb(16,37,58));
            if(Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
            window.setBackgroundDrawable(new ColorDrawable(Color.rgb(16,37,58)));
        }
        if(keyboard!=null)keyboard.start();
    }
    @Override public void onFinishInputView(boolean finishingInput){
        if(keyboard!=null)keyboard.stop();
        super.onFinishInputView(finishingInput);
    }
    @Override public void onDestroy(){
        if(keyboard!=null)keyboard.stop();
        super.onDestroy();
    }
}
