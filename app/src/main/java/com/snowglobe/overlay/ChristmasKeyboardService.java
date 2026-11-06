package com.snowglobe.overlay;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Locale;

/**
 * SnowGlobe's explicitly opt-in native IME. Input is committed to the focused editor
 * through InputConnection only. Never log, persist, transmit or share typed text.
 * The Gemini action only opens a separate application, without prompt contents.
 */
public final class ChristmasKeyboardService extends InputMethodService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root,lightStrip,toolbar,rows;
    private TextView[] lightViews;
    private LinearLayout candidateStrip;
    private TextView candidatePlaceholder;
    private TextView[] candidateButtons;
    private int selectionStart=-1,selectionEnd=-1;
    private EditorInfo editor;
    private KeyboardPolicy.Layout layout=KeyboardPolicy.Layout.LETTERS;
    private boolean visible,shifted,capsLock,symbols,emoji,sensitive,repeatFired;
    private boolean customImeSwitcherVisible=true;
    private long animationEpoch;
    private long lastShiftTap,suppressSelectionUntil;
    private float spaceDownX;
    private boolean spaceMoved;
    private final StringBuilder typedWord=new StringBuilder(24);

    private static final int[][] PALETTES={
        {0xffff5e68,0xff5fe09b,0xffffd16b,0xff6cbcff,0xffff95cc},
        {0xff8fe3ff,0xffd8f2ff,0xff7ebaff,0xffbcaaff},
        {0xffffd781,0xffffaa6f,0xfffff2b9,0xffffc55f}
    };
    private static final String[] EMOJI={
        "❄","⛄","🎄","🎁","🎅","✨","⭐","🎉","❤️","😊","😂","😍",
        "🥰","👍","🙏","🔥","🎊","☃","🧣","🍪","☕","🦌","🌟","💚"
    };
    /** Smooth capped 30 fps wave/twinkle; never on password fields or Battery Saver. */
    private final Runnable rgbTick=new Runnable(){
        @Override public void run(){
            if(!visible||lightViews==null||!rgbAllowed())return;
            int[] colors=palette();
            int level=KeyboardPolicy.bound(prefs.getInt("keyboardRgbBrightness",70),0,100);
            int speed=KeyboardPolicy.bound(prefs.getInt("keyboardRgbSpeed",45),0,100);
            int pattern=KeyboardPolicy.bound(prefs.getInt("keyboardRgbPattern",0),0,2);
            float time=(SystemClock.uptimeMillis()-animationEpoch)/1000f*(.28f+speed*.012f);
            for(int i=0;i<lightViews.length;i++){
                float point=(i*1.3f+time)%colors.length;
                int index=(int)point;
                float f=point-index;
                // A smoothstep avoids hard color transitions at each bulb.
                f=f*f*(3f-2f*f);
                int color=mix(colors[index],colors[(index+1)%colors.length],f);
                float energy=pattern==1?(float)(.70+.30*Math.sin(time*2.5+i*1.8)):1f;
                int alpha=KeyboardPolicy.bound(Math.round(level*255f/100f*energy),0,255);
                lightViews[i].setTextColor(Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color)));
            }
            handler.postDelayed(this,33);
        }
    };
    private static int mix(int left,int right,float t){
        int r=Math.round(Color.red(left)+(Color.red(right)-Color.red(left))*t);
        int g=Math.round(Color.green(left)+(Color.green(right)-Color.green(left))*t);
        int b=Math.round(Color.blue(left)+(Color.blue(right)-Color.blue(left))*t);
        return Color.rgb(r,g,b);
    }
    private final Runnable deleteRepeat=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            repeatFired=true;
            deleteOne();
            handler.postDelayed(this,66);
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);
    }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public View onCreateInputView(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5),dp(5),dp(5),dp(8));
        build();
        return root;
    }
    @Override public void onStartInputView(EditorInfo attribute,boolean restarting){
        super.onStartInputView(attribute,restarting);
        editor=attribute;layout=KeyboardPolicy.layout(attribute==null?0:attribute.inputType);
        selectionStart=attribute==null?-1:attribute.initialSelStart;
        selectionEnd=attribute==null?-1:attribute.initialSelEnd;
        sensitive=attribute!=null&&KeyboardPolicy.sensitive(attribute.inputType);
        shifted=!sensitive&&(layout==KeyboardPolicy.Layout.LETTERS||layout==KeyboardPolicy.Layout.EMAIL);
        capsLock=false;symbols=false;emoji=false;typedWord.setLength(0);
        visible=true;lastShiftTap=0;animationEpoch=SystemClock.uptimeMillis();
        if(root!=null)build();
        scheduleRgb();
    }
    @Override public void onFinishInputView(boolean finishingInput){
        visible=false;stopTasks();typedWord.setLength(0);
        editor=null;super.onFinishInputView(finishingInput);
    }
    @Override public void onDestroy(){
        visible=false;stopTasks();typedWord.setLength(0);
        super.onDestroy();
    }
    @Override public void onCustomImeSwitcherButtonRequestedVisible(boolean isVisible){
        super.onCustomImeSwitcherButtonRequestedVisible(isVisible);
        customImeSwitcherVisible=isVisible;
        if(root!=null&&visible)buildToolbar();
    }
    private void stopTasks(){handler.removeCallbacks(rgbTick);handler.removeCallbacks(deleteRepeat);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private int keyHeight(){return dp(getResources().getConfiguration().orientation==
        Configuration.ORIENTATION_LANDSCAPE?39:48);}
    private int theme(){return KeyboardPolicy.bound(prefs.getInt("keyboardTheme",0),0,2);}
    private int[] palette(){return PALETTES[theme()];}
    private boolean rgbAllowed(){
        if(sensitive||!prefs.getBoolean("keyboardRgb",true)||prefs.getBoolean("reduceMotion",false)
            ||prefs.getInt("keyboardRgbBrightness",70)<=0||prefs.getInt("keyboardRgbPattern",0)==2)return false;
        PowerManager pm=getSystemService(PowerManager.class);
        return pm==null||!pm.isPowerSaveMode();
    }
    private void scheduleRgb(){
        handler.removeCallbacks(rgbTick);
        if(visible&&rgbAllowed())handler.post(rgbTick);
    }
    private GradientDrawable shape(int fill,int border,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);
        d.setCornerRadius(dp(radius));d.setStroke(dp(1),border);return d;
    }
    private int backgroundColor(){
        return theme()==1?0xff0b2338:theme()==2?0xff291d23:0xff0d2030;
    }
    private int keyColor(){return theme()==1?0xff213f59:theme()==2?0xff4b3337:0xff213b4d;}
    private int borderColor(){return theme()==1?0xff466a87:theme()==2?0xff926b53:0xff476b75;}
    private int accent(){return theme()==1?0xff9cdef6:theme()==2?0xffffd888:0xffabedc9;}

    private void build(){
        if(root==null)return;
        handler.removeCallbacks(rgbTick);
        root.removeAllViews();root.setBackgroundColor(backgroundColor());
        lightStrip=new LinearLayout(this);lightStrip.setGravity(Gravity.CENTER);
        lightViews=new TextView[14];
        int[] colors=palette();
        for(int i=0;i<lightViews.length;i++){
            TextView t=new TextView(this);t.setGravity(Gravity.CENTER);
            t.setText("●");t.setTextSize(13);
            int shade=colors[i%colors.length];int strength=KeyboardPolicy.bound(prefs.getInt("keyboardRgbBrightness",70),0,100);
            t.setTextColor(Color.argb(strength*255/100,Color.red(shade),Color.green(shade),Color.blue(shade)));
            lightStrip.addView(t,new LinearLayout.LayoutParams(0,dp(19),1));lightViews[i]=t;
        }
        root.addView(lightStrip);
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(41)));
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);
        buildToolbar();buildRows();scheduleRgb();
    }
    private void buildToolbar(){
        if(toolbar==null)return;
        toolbar.removeAllViews();
        if(KeyboardPolicy.assistantAllowed(editor)&&prefs.getBoolean("keyboardGeminiShortcut",true)){
            toolbarButton("✦ Gemini",1.2f,()->openGemini(),"Open Gemini without sharing typed text");
        }else{
            TextView snow=new TextView(this);snow.setText("❄");snow.setTextColor(accent());
            snow.setTextSize(19);snow.setGravity(Gravity.CENTER);
            toolbar.addView(snow,new LinearLayout.LayoutParams(dp(34),-1));
        }
        candidateStrip=new LinearLayout(this);candidateStrip.setGravity(Gravity.CENTER);
        toolbar.addView(candidateStrip,new LinearLayout.LayoutParams(0,-1,3));
        candidatePlaceholder=new TextView(this);
        candidatePlaceholder.setTextColor(0xffadc4ca);candidatePlaceholder.setTextSize(12);
        candidatePlaceholder.setGravity(Gravity.CENTER);
        candidateStrip.addView(candidatePlaceholder,new LinearLayout.LayoutParams(-1,-1));
        candidateButtons=new TextView[3];
        for(int i=0;i<candidateButtons.length;i++){
            TextView choice=new TextView(this);choice.setTextColor(0xfff8f5ed);
            choice.setTextSize(14);choice.setGravity(Gravity.CENTER);
            choice.setBackground(new RippleDrawable(ColorStateList.valueOf(0x5575d7b8),
                shape(0x00000000,0x00333333,10),null));
            candidateStrip.addView(choice,new LinearLayout.LayoutParams(0,-1,1));
            choice.setOnClickListener(v->complete(choice.getText().toString()));
            candidateButtons[i]=choice;
        }
        updateCandidates();
        if(customImeSwitcherVisible)toolbarButton("🌐",.45f,this::switchKeyboard,"Choose another keyboard");
    }
    private void updateCandidates(){
        if(candidateButtons==null||candidatePlaceholder==null)return;
        List<String> candidates=(!sensitive&&prefs.getBoolean("keyboardSuggestions",true)
            &&KeyboardPolicy.suggestionsAllowed(editor))
            ?KeyboardPolicy.complete(typedWord.toString()):java.util.Collections.emptyList();
        candidatePlaceholder.setText(sensitive?"Private input":"❄  SnowGlobe Keys");
        candidatePlaceholder.setVisibility(candidates.isEmpty()?View.VISIBLE:View.GONE);
        for(int i=0;i<candidateButtons.length;i++){
            TextView view=candidateButtons[i];
            if(i<candidates.size()){
                view.setText(candidates.get(i));
                view.setContentDescription("Complete word "+candidates.get(i));
                view.setVisibility(View.VISIBLE);
            }else view.setVisibility(View.GONE);
        }
    }
    private void toolbarButton(String label,float weight,Runnable action,String description){
        TextView text=new TextView(this);text.setText(label);text.setTextSize(label.length()>2?12:17);
        text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);text.setGravity(Gravity.CENTER);
        text.setTextColor(accent());text.setContentDescription(description);
        text.setBackground(new RippleDrawable(ColorStateList.valueOf(0x5569b9ff),
            shape(0x442b5265,0x224e8b9e,11),null));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(35),weight);
        lp.setMargins(dp(2),dp(2),dp(2),dp(2));toolbar.addView(text,lp);
        text.setOnClickListener(v->action.run());
    }
    private void buildRows(){
        rows.removeAllViews();
        if(emoji){
            for(int start=0;start<EMOJI.length;start+=8){
                LinearLayout row=new LinearLayout(this);rows.addView(row);
                for(int i=start;i<Math.min(EMOJI.length,start+8);i++){
                    final String item=EMOJI[i];key(row,item,1f,false,()->type(item));
                }
            }
        }else if(layout==KeyboardPolicy.Layout.NUMBER||layout==KeyboardPolicy.Layout.PHONE){
            row("123");row("456");row("789");
            LinearLayout number=new LinearLayout(this);rows.addView(number);
            key(number,layout==KeyboardPolicy.Layout.PHONE?"*":"-",1f,false,()->type(layout==KeyboardPolicy.Layout.PHONE?"*":"-"));
            key(number,"0",1f,false,()->type("0"));
            key(number,layout==KeyboardPolicy.Layout.PHONE?"#":".",1f,false,()->type(layout==KeyboardPolicy.Layout.PHONE?"#":"."));
        }else if(symbols){
            row("1234567890");row("@#$%&-+()/");row("*\"':;!?.,");
        }else{
            row("qwertyuiop");row("asdfghjkl");row("zxcvbnm");
        }
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);rows.addView(bottom);
        boolean numeric=layout==KeyboardPolicy.Layout.NUMBER||layout==KeyboardPolicy.Layout.PHONE;
        if(!numeric){
            key(bottom,emoji?"ABC":symbols?"ABC":"?123",1.10f,true,()->{
                if(emoji){emoji=false;symbols=false;}else if(symbols){symbols=false;}
                else{symbols=true;emoji=false;}build();
            });
            key(bottom,emoji?"?123":"☺",.72f,true,()->{emoji=!emoji;symbols=false;build();});
            if(!emoji)key(bottom,capsLock?"⇪":shifted?"⇧":"⇧",.7f,true,this::toggleShift);
            if(layout==KeyboardPolicy.Layout.EMAIL&&!emoji)key(bottom,"@",.75f,false,()->type("@"));
            else if(layout==KeyboardPolicy.Layout.URL&&!emoji)key(bottom,"/",.7f,false,()->type("/"));
        }
        key(bottom,"space",numeric?.55f:3.05f,false,()->type(" "),KeyType.SPACE);
        if(!numeric&&!emoji)key(bottom,".",.7f,false,()->type("."));
        key(bottom,"⌫",.82f,true,this::deleteOne,KeyType.DELETE);
        key(bottom,actionLabel(),1.1f,true,this::enter);
    }
    private String actionLabel(){
        if(editor==null)return "↵";
        switch(editor.imeOptions&EditorInfo.IME_MASK_ACTION){
            case EditorInfo.IME_ACTION_GO:return "Go";
            case EditorInfo.IME_ACTION_SEARCH:return "Search";
            case EditorInfo.IME_ACTION_SEND:return "Send";
            case EditorInfo.IME_ACTION_NEXT:return "Next";
            case EditorInfo.IME_ACTION_DONE:return "Done";
            default:return "↵";
        }
    }
    private void row(String characters){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        if(characters.length()==9)row.setPadding(dp(10),0,dp(10),0);
        rows.addView(row);
        for(int i=0;i<characters.length();i++){
            String letter=String.valueOf(characters.charAt(i));
            String label=(!symbols&&shifted&&!emoji)?letter.toUpperCase(Locale.ROOT):letter;
            key(row,label,1f,false,()->{
                String out=(!symbols&&(shifted||capsLock))?letter.toUpperCase(Locale.ROOT):letter;
                type(out);
                if(shifted&&!capsLock&&!symbols){shifted=false;buildRows();}
            });
        }
    }
    private enum KeyType { REGULAR, DELETE, SPACE }
    private void key(LinearLayout row,String label,float weight,boolean special,Runnable action){
        key(row,label,weight,special,action,KeyType.REGULAR);
    }
    private void key(LinearLayout row,String label,float weight,boolean special,Runnable action,KeyType kind){
        Button button=new Button(this);button.setAllCaps(false);button.setText(label);
        button.setTextSize(label.length()>3?12:label.length()>1?15:20);
        button.setTypeface(Typeface.DEFAULT,special?Typeface.BOLD:Typeface.NORMAL);
        button.setTextColor(special?accent():Color.WHITE);
        button.setContentDescription(label.equals("⌫")?"Delete":label.equals("⇧")?"Shift":label);
        button.setMinHeight(0);button.setMinimumHeight(0);
        button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(0,0,0,0);
        int fill=special?backgroundColor():keyColor();
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x8857b8a8),
            shape(fill,borderColor(),12),null));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,keyHeight(),weight);
        params.setMargins(dp(2),dp(3),dp(2),dp(3));row.addView(button,params);
        button.setOnClickListener(v->{haptic(v);action.run();});
        if(kind==KeyType.DELETE)installRepeatingDelete(button);
        if(kind==KeyType.SPACE)installCursorSpace(button);
    }
    private void haptic(View v){
        if(prefs.getBoolean("keyboardHaptics",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }
    private void installRepeatingDelete(Button button){
        button.setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    repeatFired=false;handler.removeCallbacks(deleteRepeat);
                    view.setPressed(true);haptic(view);handler.postDelayed(deleteRepeat,360);return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(deleteRepeat);view.setPressed(false);
                    if(!repeatFired)view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(deleteRepeat);view.setPressed(false);return true;
                default:return true;
            }
        });
    }
    private void installCursorSpace(Button button){
        button.setOnTouchListener((view,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    spaceDownX=event.getX();spaceMoved=false;return true;
                case MotionEvent.ACTION_MOVE:
                    float delta=event.getX()-spaceDownX;
                    if(Math.abs(delta)>=dp(24)){
                        sendDownUpKeyEvents(delta>0?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT);
                        spaceDownX=event.getX();spaceMoved=true;typedWord.setLength(0);updateCandidates();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!spaceMoved)view.performClick();return true;
                case MotionEvent.ACTION_CANCEL:return true;
                default:return true;
            }
        });
    }
    private void toggleShift(){
        long now=SystemClock.uptimeMillis();
        if(now-lastShiftTap<400){capsLock=!capsLock;shifted=capsLock;lastShiftTap=0;}
        else{capsLock=false;shifted=!shifted;lastShiftTap=now;}
        if(!emoji)buildRows();
    }
    private void type(String text){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        suppressSelectionUntil=SystemClock.uptimeMillis()+180;
        if(!ic.commitText(text,1))return;
        if(!sensitive&&KeyboardPolicy.suggestionsAllowed(editor)&&text.length()==1&&Character.isLetter(text.charAt(0))){
            if(typedWord.length()<24)typedWord.append(text.toLowerCase(Locale.ROOT));
        }else typedWord.setLength(0);
        updateCandidates();
    }
    private void deleteOne(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        suppressSelectionUntil=SystemClock.uptimeMillis()+180;
        if(selectionStart>=0&&selectionEnd>selectionStart)ic.commitText("",1);
        else if(typedWord.length()>0){
            int codePoints=Character.charCount(typedWord.codePointBefore(typedWord.length()));
            typedWord.delete(typedWord.length()-codePoints,typedWord.length());
            ic.deleteSurroundingTextInCodePoints(1,0);
        }else ic.deleteSurroundingTextInCodePoints(1,0);
        updateCandidates();
    }
    private void complete(String candidate){
        if(sensitive||!KeyboardPolicy.suggestionsAllowed(editor)||typedWord.length()==0)return;
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        suppressSelectionUntil=SystemClock.uptimeMillis()+180;
        ic.beginBatchEdit();
        try{
            ic.deleteSurroundingTextInCodePoints(typedWord.length(),0);
            ic.commitText(candidate+" ",1);
        }finally{ic.endBatchEdit();}
        typedWord.setLength(0);updateCandidates();
    }
    @Override public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,
            int candidatesStart,int candidatesEnd){
        super.onUpdateSelection(oldStart,oldEnd,newStart,newEnd,candidatesStart,candidatesEnd);
        selectionStart=newStart;selectionEnd=newEnd;
        if(SystemClock.uptimeMillis()>suppressSelectionUntil&&(oldStart!=newStart||oldEnd!=newEnd)){
            typedWord.setLength(0);if(visible)updateCandidates();
        }
    }
    private void enter(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        typedWord.setLength(0);updateCandidates();
        int action=editor==null?EditorInfo.IME_ACTION_NONE:editor.imeOptions&EditorInfo.IME_MASK_ACTION;
        if(editor!=null&&(editor.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0&&
           action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED){
            if(ic.performEditorAction(action))return;
        }
        ic.commitText("\n",1);
    }
    private void switchKeyboard(){
        InputMethodManager imm=getSystemService(InputMethodManager.class);
        if(imm!=null)imm.showInputMethodPicker();
    }
    private void openGemini(){
        if(!KeyboardPolicy.assistantAllowed(editor))return;
        // No clipboard, selected text, editor contents or typedWord is attached to either intent.
        try{
            Intent installed=getPackageManager().getLaunchIntentForPackage("com.google.android.apps.bard");
            if(installed!=null){installed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(installed);return;}
            Intent web=new Intent(Intent.ACTION_VIEW,Uri.parse("https://gemini.google.com/app"));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(web);
        }catch(ActivityNotFoundException error){
            Toast.makeText(this,"Gemini is not available on this device",Toast.LENGTH_SHORT).show();
        }catch(SecurityException error){
            Toast.makeText(this,"Android blocked opening Gemini",Toast.LENGTH_SHORT).show();
        }
    }
}
