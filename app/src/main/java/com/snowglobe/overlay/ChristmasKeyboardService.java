package com.snowglobe.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Explicitly user-selected keyboard. Never draws a SYSTEM_ALERT_WINDOW,
 * reads keystrokes into storage, sends text to a network, or collects analytics.
 * A basic beta keyboard, not a replacement for Gboard's language engine.
 */
public final class ChristmasKeyboardService extends InputMethodService {
    private boolean shifted = true;
    private boolean symbols = false;
    private LinearLayout root;
    private final int[] bulbs = {0xffff5555,0xff61df91,0xffffd26a,0xff69b9ff,0xffff83cf};
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        d.setStroke(dp(1),stroke);return d;
    }
    @Override public View onCreateInputView(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4),dp(6),dp(4),dp(8));root.setBackgroundColor(0xff112338);
        render();return root;
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting){
        super.onStartInputView(info,restarting);
        shifted=true;symbols=false;if(root!=null)render();
    }
    private void render(){
        root.removeAllViews();
        LinearLayout lights=new LinearLayout(this);lights.setGravity(Gravity.CENTER);
        for(int i=0;i<14;i++){
            TextView bulb=new TextView(this);bulb.setText("●");bulb.setTextColor(bulbs[i%bulbs.length]);
            bulb.setTextSize(13);bulb.setGravity(Gravity.CENTER);
            lights.addView(bulb,new LinearLayout.LayoutParams(0,dp(23),1));
        }
        root.addView(lights);
        TextView label=new TextView(this);
        label.setText("❄ SnowGlobe  •  Christmas Keys");label.setTextColor(0xffd8f5ff);
        label.setTextSize(12);label.setGravity(Gravity.CENTER);label.setPadding(0,0,0,dp(5));
        root.addView(label);
        if(symbols){
            row("1234567890",0);row("@#$%&-+()/",1);row("*\"':;!?.,",2);
        }else{
            row("qwertyuiop",0);row("asdfghjkl",1);row("zxcvbnm",2);
        }
        LinearLayout bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);
        addKey(bottom,symbols?"ABC":"?123",1.2f,()->{symbols=!symbols;shifted=false;render();},true);
        addKey(bottom,"⇧",.8f,()->{shifted=!shifted;render();},true);
        addKey(bottom,",",.7f,()->type(","),false);
        addKey(bottom,"space",3.6f,()->type(" "),false);
        addKey(bottom,".",.7f,()->type("."),false);
        addKey(bottom,"⌫",1f,()->{InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.deleteSurroundingText(1,0);},true);
        addKey(bottom,"↵",1f,this::enter,true);
        root.addView(bottom);
    }
    private void row(String chars,int index){
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);
        if(index==1){line.setPadding(dp(12),0,dp(12),0);}
        for(int i=0;i<chars.length();i++){
            String character=String.valueOf(chars.charAt(i));
            String shown=shifted?character.toUpperCase(java.util.Locale.ROOT):character;
            addKey(line,shown,1f,()->{
                type(shown);
                if(shifted&&!symbols){shifted=false;render();}
            },false);
        }
        root.addView(line);
    }
    private void addKey(LinearLayout line,String text,float weight,Runnable action,boolean special){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(text.length()>2?12:17);
        b.setTypeface(Typeface.DEFAULT,special?Typeface.BOLD:Typeface.NORMAL);
        b.setTextColor(special?0xfff3e8bf:Color.WHITE);
        b.setBackground(background(special?0xff31536b:0xff203b53,special?0xff8cc7ae:0xff476981,9));
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(0,0,0,0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(47),weight);
        p.setMargins(dp(2),dp(3),dp(2),dp(3));line.addView(b,p);
        b.setOnClickListener(v->action.run());
    }
    private void type(String value){
        InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.commitText(value,1);
    }
    private void enter(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        EditorInfo info=getCurrentInputEditorInfo();
        int action=info==null?EditorInfo.IME_ACTION_NONE:(info.imeOptions&EditorInfo.IME_MASK_ACTION);
        if(action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED){
            if(ic.performEditorAction(action))return;
        }
        ic.commitText("\n",1);
    }
}
