package com.snowglobe.overlay;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Native Android IME. Typing stays on-device and is never recorded by SnowGlobe. */
public final class ChristmasKeyboardService extends InputMethodService {
    private SharedPreferences preferences;
    private FastKeyboardView keyboard;
    private KeyboardTextEngine engine;
    private final Handler capsHandler=new Handler(Looper.getMainLooper());
    private ThreadPoolExecutor capsWorker;
    private volatile long capsGeneration;
    private boolean inputViewActive;
    private int cachedSelectionStart=-1,cachedSelectionEnd=-1;

    @Override public void onCreate(){
        super.onCreate();
        preferences=getSharedPreferences("snowglobe",MODE_PRIVATE);
        capsWorker=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),task->{
            Thread thread=new Thread(task,"SnowGlobe-capitalization");
            thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;
        },new ThreadPoolExecutor.DiscardOldestPolicy());
        capsWorker.allowCoreThreadTimeOut(true);
    }
    @Override public View onCreateInputView(){
        cancelCapsUpdates();
        if(engine!=null)engine.finish();
        if(keyboard!=null)keyboard.stop();
        engine=new KeyboardTextEngine(new KeyboardTextEngine.ConnectionProvider(){
            @Override public InputConnection current(){return getCurrentInputConnection();}
            @Override public EditorInfo editor(){return getCurrentInputEditorInfo();}
            @Override public void candidates(java.util.List<String> options){
                if(keyboard!=null)keyboard.setSuggestions(options);
            }
        },preferences);
        keyboard=new FastKeyboardView(this,preferences,new FastKeyboardView.InputActions(){
            @Override public void commit(String value){
                invalidateCapsRequest();engine.input(value);
                if(!value.isEmpty()&&!Character.isLetter(value.codePointBefore(value.length())))requestCapsUpdate();
            }
            @Override public void delete(){invalidateCapsRequest();engine.backspace();requestCapsUpdate();}
            @Override public void enter(){invalidateCapsRequest();engine.enter();requestCapsUpdate();}
            @Override public void choose(String suggestion){invalidateCapsRequest();engine.choose(suggestion);requestCapsUpdate();}
            @Override public void moveCursor(int codePoints){invalidateCapsRequest();engine.moveCursor(codePoints);requestCapsUpdate();}
            @Override public void switchKeyboard(){
                InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if(imm!=null){
                    try{imm.showInputMethodPicker();}
                    catch(RuntimeException unavailable){/* OEM picker support must not take down the IME. */}
                }
            }
        });
        return keyboard;
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public void onStartInput(EditorInfo info,boolean restarting){
        super.onStartInput(info,restarting);
        inputViewActive=false;cancelCapsUpdates();
        cachedSelectionStart=info==null?-1:info.initialSelStart;
        cachedSelectionEnd=info==null?-1:info.initialSelEnd;
        if(keyboard!=null)keyboard.stop(); // A held key must never repeat into the next editor.
        if(engine!=null)engine.begin(info,cachedSelectionStart,cachedSelectionEnd);
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        Window window=getWindow()==null?null:getWindow().getWindow();
        if(window!=null){
            window.setNavigationBarColor(Color.rgb(16,37,58));
            if(Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
            window.setBackgroundDrawable(new ColorDrawable(Color.rgb(16,37,58)));
        }
        inputViewActive=true;EffectsService.setKeyboardTyping(true);
        if(engine!=null)engine.begin(info,cachedSelectionStart,cachedSelectionEnd);
        if(keyboard!=null){
            keyboard.configureEditor(info);keyboard.start();
            keyboard.updateAutoShift(initialCapitalization(info,cachedSelectionStart,cachedSelectionEnd));
        }
        requestCapsUpdate();
    }
    @Override public void onUpdateSelection(int oldSelStart,int oldSelEnd,int newSelStart,int newSelEnd,
            int candidatesStart,int candidatesEnd){
        super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd);
        cachedSelectionStart=newSelStart;cachedSelectionEnd=newSelEnd;
        if(engine!=null&&engine.selectionChanged(newSelStart,newSelEnd))requestCapsUpdate();
    }
    private void invalidateCapsRequest(){
        capsGeneration++;capsHandler.removeCallbacks(refreshCaps);
    }
    private void requestCapsUpdate(){
        invalidateCapsRequest();
        if(inputViewActive)capsHandler.postDelayed(refreshCaps,24);
    }
    private void cancelCapsUpdates(){
        capsGeneration++;capsHandler.removeCallbacksAndMessages(null);
        if(capsWorker!=null)capsWorker.getQueue().clear();
    }
    private static int capitalizationModes(EditorInfo info){
        if(info==null||(info.inputType&InputType.TYPE_MASK_CLASS)!=InputType.TYPE_CLASS_TEXT)return 0;
        int variation=info.inputType&InputType.TYPE_MASK_VARIATION;
        if(variation==InputType.TYPE_TEXT_VARIATION_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||
            variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS||
            variation==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS||variation==InputType.TYPE_TEXT_VARIATION_URI)return 0;
        return info.inputType&(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_CAP_WORDS|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }
    /** EditorInfo supplies an initial caps hint without a blocking query; reused views use the latest caret. */
    static boolean initialCapitalization(EditorInfo info,int selectionStart,int selectionEnd){
        int modes=capitalizationModes(info);
        if(modes==0)return false;
        if((modes&InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)!=0)return true;
        if(selectionStart<0||selectionStart!=selectionEnd)return false;
        if(selectionStart==0)return true;
        // Android reports CAP_WORDS at paragraph starts even for a CAP_SENTENCES editor.
        return info!=null&&selectionStart==info.initialSelStart&&selectionEnd==info.initialSelEnd&&
            info.initialCapsMode!=0;
    }
    private final Runnable refreshCaps=()->{
        if(!inputViewActive||keyboard==null)return;
        final EditorInfo info=getCurrentInputEditorInfo();
        final int modes=capitalizationModes(info);
        if(modes==0){keyboard.updateAutoShift(false);return;}
        if((modes&InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)!=0){keyboard.updateAutoShift(true);return;}
        final InputConnection connection=getCurrentInputConnection();
        if(connection==null||capsWorker==null||capsWorker.isShutdown())return;
        final long generation=capsGeneration;
        // This query can block on a slow editor. It never runs on the key-dispatch thread.
        capsWorker.getQueue().clear();
        capsWorker.execute(()->{
            if(generation!=capsGeneration)return;
            final boolean enabled;
            // The returned recommendation can differ from the requested mode at paragraph starts.
            try{enabled=connection.getCursorCapsMode(modes)!=0;}
            catch(RuntimeException unavailable){return;}
            if(generation!=capsGeneration)return;
            capsHandler.post(()->{
                if(inputViewActive&&keyboard!=null&&generation==capsGeneration&&connection==getCurrentInputConnection())
                    keyboard.updateAutoShift(enabled);
            });
        });
    };
    private void finishKeyboard(){
        inputViewActive=false;cancelCapsUpdates();EffectsService.setKeyboardTyping(false);
        if(engine!=null)engine.finish();
        if(keyboard!=null)keyboard.stop();
    }
    @Override public void onFinishInputView(boolean finishingInput){
        finishKeyboard();super.onFinishInputView(finishingInput);
    }
    @Override public void onFinishInput(){
        finishKeyboard();cachedSelectionStart=cachedSelectionEnd=-1;super.onFinishInput();
    }
    @Override public void onDestroy(){
        finishKeyboard();if(capsWorker!=null)capsWorker.shutdownNow();super.onDestroy();
    }
}
