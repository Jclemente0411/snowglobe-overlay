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
    private KeyboardTextEngine engine;

    @Override public void onCreate(){
        super.onCreate();
        preferences=getSharedPreferences("snowglobe",MODE_PRIVATE);
    }
    @Override public View onCreateInputView(){
        engine=new KeyboardTextEngine(new KeyboardTextEngine.ConnectionProvider(){
            @Override public InputConnection current(){return getCurrentInputConnection();}
            @Override public EditorInfo editor(){return getCurrentInputEditorInfo();}
            @Override public void candidates(java.util.List<String> options){
                if(keyboard!=null)keyboard.setSuggestions(options);
            }
        },preferences);
        keyboard=new FastKeyboardView(this,preferences,new FastKeyboardView.InputActions(){
            @Override public void commit(String value){engine.input(value);}
            @Override public void delete(){engine.backspace();}
            @Override public void enter(){engine.enter();}
            @Override public void choose(String suggestion){engine.choose(suggestion);}
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
        EffectsService.setKeyboardTyping(true);
        if(keyboard!=null)keyboard.start();
        if(engine!=null)engine.begin(info);
    }
    @Override public void onFinishInputView(boolean finishingInput){
        EffectsService.setKeyboardTyping(false);
        if(engine!=null)engine.finish();
        if(keyboard!=null)keyboard.stop();
        super.onFinishInputView(finishingInput);
    }
    @Override public void onDestroy(){
        EffectsService.setKeyboardTyping(false);
        if(keyboard!=null)keyboard.stop();
        super.onDestroy();
    }
}
