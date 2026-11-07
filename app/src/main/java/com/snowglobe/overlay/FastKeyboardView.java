package com.snowglobe.overlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Native IME surface. Decorative lights never change the layout or delay a key commit. */
final class FastKeyboardView extends View {
    interface InputActions {
        void commit(String text);
        void delete();
        void enter();
        void choose(String suggestion);
        default void switchKeyboard() {}
        default void moveCursor(int codePoints) {}
    }

    static final int LETTER=0,STATIC=1,SHIFT=2,DELETE=3,MODE=4,ENTER=5,SPACE=6,SUGGEST=7,
        SWITCH=8,PAGE=9,EMOJI=10,EMOJI_TEXT=11,LEFT=12,RIGHT=13;
    private static final int BACKGROUND=0xff10253a,NO_VIRTUAL=Integer.MIN_VALUE,SUGGESTION_ID=1000;
    private static final int[] XMAS={0xffec6b6b,0xff5bc997,0xffffc46d,0xff76c6f8,0xffd28be0};
    private static final int[] ICE={0xff8dd4ff,0xffd9eeff,0xff8caaff};
    private static final int[] GOLD={0xfff6bb66,0xffffe4a7,0xffff996a};
    private static final String[][] EMOJIS={
        {"😀","😃","😁","😂","😊","🥰","😍","😎","🤔","😢"},
        {"👍","👎","👏","🙏","💪","❤️","💚","✨","🎉","❄️"},
        {"🎄","🎅","🤶","⛄","🎁","🦌","🌟","🔔","🕯️"},
        {"😊","😂","❤️","🎄","🎁","❄️","🎉"}
    };

    static final class Key {
        final String legend,output,upperLegend,upperOutput;
        final int kind;
        final RectF bounds=new RectF(),hitBox=new RectF();
        float fontSize,baseline;
        Key(String legend,String output,int kind){
            this.legend=legend;this.output=output;this.kind=kind;
            upperLegend=legend.toUpperCase(Locale.ROOT);
            upperOutput=output==null?null:output.toUpperCase(Locale.ROOT);
        }
        boolean isInside(float x,float y){return hitBox.contains(x,y);}
    }
    private static final class Press {
        Key key;
        final Key origin;
        final float downX,downY;
        final long order;
        final boolean upper;
        boolean committed,usedAsModifier,cursorDrag,cancelled;
        float cursorX;
        Press(Key key,float x,float y,long order,boolean upper){
            this.key=key;origin=key;downX=cursorX=x;downY=y;this.order=order;this.upper=upper;
        }
    }

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect drawClip=new Rect();
    private final ArrayList<Key> keys=new ArrayList<>(60),suggestionKeys=new ArrayList<>(3);
    private final SparseArray<Press> pressed=new SparseArray<>();
    private List<String> suggestions=Collections.emptyList();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final InputActions actions;
    private final AccessibilityManager accessibilityManager;
    private final RenderPowerMonitor power;
    private final int touchSlop;

    private boolean running,preferencesRegistered,geometryDirty=true;
    private boolean symbols,secondSymbols,emojiPage,numeric,phone,decimal,signed,privateInput;
    private boolean autoShift,autoCapsCharacters,sentenceCaps,wordCaps,sentenceBoundary;
    private boolean manualShift,manualShiftOverride,capsLock;
    private String enterLabel="↵",headerLabel="❄ SnowGlobe  •  Christmas Keys",commaLabel=",";
    private long pressOrder,lastShiftTap=-1,recentTap,pressEffectStarted;
    private Key recentKey;
    private boolean pressFramePosted;
    private int repeatPointer=-1,accessibilityFocused=NO_VIRTUAL,hoveredVirtual=NO_VIRTUAL;
    private float rgbPhase;
    private long lastRgbTime;
    private int[] keyAccents=new int[0],keyBorders=new int[0];
    private final int[] lampColors=new int[18];
    private int heightPreference,theme,brightness,pattern,speed;
    private boolean rgb,haptics,reduceMotion,systemMotion=true,compact;
    private int[] colors=XMAS;
    private int baseColor,raisedColor,borderColor;
    private float density,scaledDensity,systemBottomInset,systemLeftInset,systemRightInset;
    private float layoutNav,contentTop,stripeHeight,headerTop,headerSize,numberSize,rowSize,footerSize;
    private float contentLeft,contentRight;

    private final Runnable accessibilityRefresh=this::refreshAccessibility;
    private final Choreographer.FrameCallback pressEffectFrame=frameTimeNanos->{
        pressFramePosted=false;
        if(recentKey==null)return;
        if(!animateRgb()||SystemClock.uptimeMillis()-pressEffectStarted>=230){clearPressEffect();return;}
        // Animate only the short key highlight; idle palette updates remain throttled.
        invalidateKey(recentKey);schedulePressEffectFrame();
    };
    private final Runnable reloadPreferences=()->{
        int oldHeight=heightPreference;
        readPreferences();
        if(oldHeight!=heightPreference){geometryDirty=true;requestLayout();}
        restartRgb();invalidate();
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChanges=(p,key)->{
        if(key!=null&&(key.startsWith("keyboard")||key.equals("reduceMotion"))){
            handler.removeCallbacks(reloadPreferences);handler.post(reloadPreferences);
        }
    };
    private final Runnable rgbTick=new Runnable(){
        @Override public void run(){
            if(!animateRgb())return;
            long now=SystemClock.uptimeMillis();
            long elapsed=lastRgbTime==0?0:Math.min(250,Math.max(0,now-lastRgbTime));
            lastRgbTime=now;
            // Input wins over full-surface decoration, including a key held before release.
            if(pressed.size()==0&&now-recentTap>=125){
                rgbPhase=(rgbPhase+elapsed*(.00008f+speed*.000012f))%colors.length;
                cacheLighting();postInvalidateOnAnimation();
            }
            handler.postDelayed(this,50+(100-speed)*2/5);
        }
    };
    private final Runnable deleteRepeat=new Runnable(){
        @Override public void run(){
            if(!running||!hasHeldDelete())return;
            actions.delete();recentTap=SystemClock.uptimeMillis();
            handler.postDelayed(this,78);
        }
    };
    // A named starter can be removed on cancel, detach, editor change and a new press.
    private final Runnable beginDeleteRepeat=()->{if(running&&hasHeldDelete())deleteRepeat.run();};

    FastKeyboardView(Context context,SharedPreferences prefs,InputActions actions){
        super(context);preferences=prefs;this.actions=actions;
        accessibilityManager=context.getSystemService(AccessibilityManager.class);
        power=new RenderPowerMonitor(context,this::powerChanged);
        touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();
        readDimensions();readPreferences();
        setBackgroundColor(BACKGROUND);setHapticFeedbackEnabled(true);setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        // A forced hardware layer would repeatedly upload the whole keyboard texture.
    }
    private float dp(float value){return value*density;}
    private float sp(float value){return value*scaledDensity;}
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    private void readDimensions(){
        density=getResources().getDisplayMetrics().density;
        scaledDensity=getResources().getDisplayMetrics().scaledDensity;
        compact=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
    }
    private void readPreferences(){
        heightPreference=clamp(preferences.getInt("keyboardHeight",60),0,100);
        theme=clamp(preferences.getInt("keyboardTheme",0),0,2);
        brightness=clamp(preferences.getInt("keyboardRgbBrightness",75),0,100);
        pattern=clamp(preferences.getInt("keyboardRgbPattern",0),0,2);
        speed=clamp(preferences.getInt("keyboardRgbSpeed",45),0,100);
        rgb=preferences.getBoolean("keyboardRgb",true);
        haptics=preferences.getBoolean("keyboardHaptics",true);
        reduceMotion=preferences.getBoolean("reduceMotion",false);
        systemMotion=ValueAnimator.areAnimatorsEnabled();
        colors=theme==1?ICE:theme==2?GOLD:XMAS;
        baseColor=theme==1?0xff224056:theme==2?0xff4b3b30:0xff263f53;
        raisedColor=theme==1?0xff39627e:theme==2?0xff72533d:0xff3b617b;
        borderColor=theme==1?0xff6fa4c5:theme==2?0xffb89965:0xff65899f;
    }
    private boolean lighting(){return rgb&&brightness>0;}
    private boolean animateRgb(){
        return running&&lighting()&&pattern!=2&&!reduceMotion&&systemMotion&&
            power.interactive&&!power.powerSaver&&power.thermal<2;
    }
    private void restartRgb(){
        handler.removeCallbacks(rgbTick);lastRgbTime=SystemClock.uptimeMillis();cacheLighting();
        if(animateRgb())handler.postDelayed(rgbTick,80);
        else clearPressEffect();
    }
    private void schedulePressEffectFrame(){
        if(!pressFramePosted){pressFramePosted=true;Choreographer.getInstance().postFrameCallback(pressEffectFrame);}
    }
    private void clearPressEffect(){
        if(pressFramePosted)Choreographer.getInstance().removeFrameCallback(pressEffectFrame);
        pressFramePosted=false;Key old=recentKey;recentKey=null;pressEffectStarted=0;invalidateKey(old);
    }
    private int blendedColor(float position){
        float wrapped=position%colors.length;if(wrapped<0)wrapped+=colors.length;
        int first=(int)wrapped,from=colors[first],to=colors[(first+1)%colors.length];float fraction=wrapped-first;
        return Color.rgb(Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*fraction),
            Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*fraction),
            Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*fraction));
    }
    private void cacheLighting(){
        // Arrays grow only on a layout change; frame updates interpolate into existing storage.
        if(keyAccents.length<keys.size()){keyAccents=new int[keys.size()];keyBorders=new int[keys.size()];}
        boolean motion=animateRgb();float phase=pattern==2?0:rgbPhase;
        for(int i=0;i<keys.size();i++){
            int accent=blendedColor(i*.19f+phase);keyAccents[i]=accent;
            float intensity=pattern==1?.25f+.75f*(float)((Math.sin(phase*2*Math.PI+i*1.7)+1)*.5):1;
            int alpha=Math.round(brightness*intensity);
            if(pattern==2)alpha=Math.min(alpha,52);if(!motion&&pattern!=2)alpha=Math.min(alpha,55);
            keyBorders[i]=Color.argb(Math.min(205,Math.max(0,alpha*2)),Color.red(accent),Color.green(accent),Color.blue(accent));
        }
        for(int i=0;i<lampColors.length;i++)lampColors[i]=blendedColor(i*.35f+(lighting()?phase:0));
    }
    private void powerChanged(){systemMotion=ValueAnimator.areAnimatorsEnabled();restartRgb();invalidate();}
    private void scheduleAccessibilityRefresh(){
        handler.removeCallbacks(accessibilityRefresh);handler.postDelayed(accessibilityRefresh,110);
    }
    private void refreshAccessibility(){
        if(running&&accessibilityManager!=null&&accessibilityManager.isEnabled())
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    void configureEditor(EditorInfo info){
        cancelTouches();clearPressEffect();lastShiftTap=-1;capsLock=false;manualShift=false;manualShiftOverride=false;
        int input=info==null?InputType.TYPE_CLASS_TEXT:info.inputType;
        int kind=input&InputType.TYPE_MASK_CLASS,variation=input&InputType.TYPE_MASK_VARIATION;
        phone=kind==InputType.TYPE_CLASS_PHONE;
        numeric=phone||kind==InputType.TYPE_CLASS_NUMBER;
        decimal=(input&InputType.TYPE_NUMBER_FLAG_DECIMAL)!=0;
        signed=(input&InputType.TYPE_NUMBER_FLAG_SIGNED)!=0;
        privateInput=KeyboardTextEngine.privateEditor(info);
        boolean ordinary=kind==InputType.TYPE_CLASS_TEXT&&!privateInput&&
            variation!=InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS&&
            variation!=InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS&&variation!=InputType.TYPE_TEXT_VARIATION_URI;
        autoCapsCharacters=ordinary&&(input&InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)!=0;
        sentenceCaps=ordinary&&(input&InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)!=0;
        wordCaps=ordinary&&(input&InputType.TYPE_TEXT_FLAG_CAP_WORDS)!=0;
        sentenceBoundary=false;
        autoShift=autoCapsCharacters||(ordinary&&info!=null&&info.initialSelStart==0&&
            (input&(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_CAP_WORDS))!=0);
        symbols=kind==InputType.TYPE_CLASS_DATETIME;secondSymbols=false;emojiPage=false;
        commaLabel=variation==InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS||variation==InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS?"@":
            variation==InputType.TYPE_TEXT_VARIATION_URI?"/":",";
        enterLabel="↵";
        if(info!=null&&(info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0){
            if(info.actionLabel!=null&&info.actionLabel.length()>0)enterLabel=info.actionLabel.toString();
            else switch(info.imeOptions&EditorInfo.IME_MASK_ACTION){
                case EditorInfo.IME_ACTION_DONE:enterLabel="Done";break;
                case EditorInfo.IME_ACTION_GO:enterLabel="Go";break;
                case EditorInfo.IME_ACTION_NEXT:enterLabel="Next";break;
                case EditorInfo.IME_ACTION_PREVIOUS:enterLabel="Previous";break;
                case EditorInfo.IME_ACTION_SEARCH:enterLabel="Search";break;
                case EditorInfo.IME_ACTION_SEND:enterLabel="Send";break;
                default:break;
            }
        }
        headerLabel=phone?"Phone keypad":numeric?"Number keypad":"❄ SnowGlobe  •  Christmas Keys";
        suggestions=Collections.emptyList();geometryDirty=true;requestLayout();buildKeys();
    }
    void updateAutoShift(boolean enabled){
        boolean before=upperCase();
        autoShift=!numeric&&!privateInput&&(autoCapsCharacters||enabled);
        if(before!=upperCase()){invalidate();scheduleAccessibilityRefresh();}
    }
    private boolean heldShift(){
        for(int i=0;i<pressed.size();i++){
            Press p=pressed.valueAt(i);
            if(!p.cancelled&&p.origin.kind==SHIFT)return true;
        }
        return false;
    }
    private boolean upperCase(){return capsLock||heldShift()||(manualShiftOverride?manualShift:autoShift);}
    private float baseStripe(){return dp(compact?8:12);}
    private float baseHeader(){return dp(compact?30:45);}
    private float baseNumber(){return dp(compact?34:52);}
    // The supplied Gboard reference uses shorter rows than the former 67.8/69 dp defaults.
    private float baseLetter(){return dp(compact?40:52+heightPreference*.10f);}
    private float baseFooter(){return dp(compact?42:56+heightPreference*.10f);}
    private float requestedNav(){return Math.max(dp(compact?16:48),systemBottomInset);}
    private float bodyHeight(){return dp(4)+baseStripe()+baseHeader()+
        (numeric?4*baseLetter():baseNumber()+3*baseLetter()+baseFooter());}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        readDimensions();
        int width=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?
            getResources().getDisplayMetrics().widthPixels:MeasureSpec.getSize(widthSpec);
        int desired=Math.round(bodyHeight()+requestedNav());
        int height=resolveSize(desired,heightSpec);
        setMeasuredDimension(width,height);geometryDirty=true;
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);geometryDirty=true;}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);if(geometryDirty)buildKeys();
    }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        float bottom,left,right;
        if(Build.VERSION.SDK_INT>=30){
            android.graphics.Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
            bottom=bars.bottom;left=bars.left;right=bars.right;
        }else{bottom=insets.getStableInsetBottom();left=insets.getStableInsetLeft();right=insets.getStableInsetRight();}
        if(bottom!=systemBottomInset||left!=systemLeftInset||right!=systemRightInset){
            systemBottomInset=bottom;systemLeftInset=left;systemRightInset=right;geometryDirty=true;requestLayout();
        }
        return super.onApplyWindowInsets(insets);
    }
    void start(){
        running=true;readDimensions();readPreferences();power.attach();cancelTouches();lastShiftTap=-1;
        if(!preferencesRegistered){preferences.registerOnSharedPreferenceChangeListener(preferenceChanges);preferencesRegistered=true;}
        rgbPhase=0;geometryDirty=true;requestLayout();buildKeys();clearPressEffect();restartRgb();invalidate();
    }
    void stop(){
        running=false;cancelTouches();power.close();
        if(preferencesRegistered){preferences.unregisterOnSharedPreferenceChangeListener(preferenceChanges);preferencesRegistered=false;}
        handler.removeCallbacks(rgbTick);handler.removeCallbacks(reloadPreferences);
        handler.removeCallbacks(accessibilityRefresh);clearPressEffect();
        suggestions=Collections.emptyList();suggestionKeys.clear();recentKey=null;
        clearAccessibilityFocus();updateHovered(NO_VIRTUAL);
        manualShiftOverride=false;manualShift=false;capsLock=false;autoShift=false;sentenceBoundary=false;lastShiftTap=-1;invalidate();
    }
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    void setSuggestions(List<String> options){
        ArrayList<String> copy=new ArrayList<>(3);
        if(options!=null)for(String option:options){
            if(option!=null&&!option.isEmpty())copy.add(option);
            if(copy.size()==3)break;
        }
        if(suggestions.equals(copy))return;
        if(accessibilityFocused>=SUGGESTION_ID)clearAccessibilityFocus();
        suggestions=copy;buildSuggestions();invalidateHeader();scheduleAccessibilityRefresh();
    }
    private void buildSuggestions(){
        suggestionKeys.clear();if(getWidth()<=0||numeric||emojiPage||symbols)return;
        int count=suggestions.size();if(count==0)return;
        float x=contentLeft+dp(3),width=contentRight-contentLeft-dp(6),cell=width/count;
        for(int i=0;i<count;i++){
            Key key=new Key(suggestions.get(i),suggestions.get(i),SUGGEST);
            key.hitBox.set(x+i*cell,headerTop,x+(i+1)*cell,headerTop+headerSize);
            key.bounds.set(key.hitBox);key.bounds.inset(Math.min(dp(2),cell*.08f),Math.min(dp(2),headerSize*.08f));
            fitLabel(key);suggestionKeys.add(key);
        }
    }
    private void invalidateKey(Key key){
        if(key==null)return;RectF b=key.bounds;int margin=(int)Math.ceil(dp(4));
        invalidate(Math.max(0,(int)b.left-margin),Math.max(0,(int)b.top-margin),
            Math.min(getWidth(),(int)Math.ceil(b.right)+margin),Math.min(getHeight(),(int)Math.ceil(b.bottom)+margin));
    }
    private void invalidateHeader(){invalidate(0,0,getWidth(),(int)Math.ceil(headerTop+headerSize+dp(3)));}
    private Key add(String legend,String output,int kind,float x,float y,float width,float height){
        Key key=new Key(legend,output,kind);
        float gx=Math.min(dp(2),width*.1f),gy=Math.min(dp(3),height*.1f);
        key.hitBox.set(x,y,x+width,y+height);key.bounds.set(x+gx,y+gy,x+width-gx,y+height-gy);
        fitLabel(key);keys.add(key);return key;
    }
    private void fitLabel(Key key){
        float nominal=key.kind==EMOJI_TEXT?24:key.kind==LETTER||key.kind==STATIC?22:
            key.kind==SUGGEST?15:key.kind==SPACE?14:key.legend.length()>2?12:22;
        paint.setTypeface(android.graphics.Typeface.DEFAULT);paint.setTextSize(sp(nominal));
        float width=Math.max(paint.measureText(key.legend),paint.measureText(key.upperLegend));
        float scale=Math.min(1,Math.min(key.bounds.height()*.78f/Math.max(1,paint.descent()-paint.ascent()),
            key.bounds.width()*.88f/Math.max(1,width)));
        key.fontSize=Math.max(1,sp(nominal)*scale);paint.setTextSize(key.fontSize);
        key.baseline=key.bounds.centerY()-(paint.ascent()+paint.descent())*.5f;
    }
    private void row(String text,int kind,float x,float y,float width,float height){
        float cell=width/text.length();int start=keys.size();
        for(int i=0;i<text.length();i++){String value=String.valueOf(text.charAt(i));add(value,value,kind,x+i*cell,y,cell,height);}
        extendRowEdges(start);
    }
    private void emojiRow(String[] values,float x,float y,float width,float height){
        float cell=width/values.length;int start=keys.size();
        for(int i=0;i<values.length;i++)add(values[i],values[i],EMOJI_TEXT,x+i*cell,y,cell,height);
        extendRowEdges(start);
    }
    private void extendRowEdges(int first){
        if(keys.size()>first){keys.get(first).hitBox.left=systemLeftInset;keys.get(keys.size()-1).hitBox.right=getWidth()-systemRightInset;}
    }
    private void buildKeys(){
        if(getWidth()<=0||getHeight()<=0)return;
        cancelTouches();clearAccessibilityFocus();updateHovered(NO_VIRTUAL);keys.clear();
        layoutNav=Math.min(requestedNav(),getHeight()*.25f);
        float body=bodyHeight(),scale=Math.min(1,Math.max(0,getHeight()-layoutNav)/Math.max(1,body));
        contentTop=Math.max(0,getHeight()-layoutNav-body*scale);
        stripeHeight=baseStripe()*scale;headerSize=baseHeader()*scale;
        numberSize=baseNumber()*scale;rowSize=baseLetter()*scale;footerSize=baseFooter()*scale;
        headerTop=contentTop+dp(2)*scale+stripeHeight;
        contentLeft=Math.max(dp(3),systemLeftInset);contentRight=Math.max(contentLeft+1,getWidth()-Math.max(dp(3),systemRightInset));
        float w=contentRight-contentLeft,x=contentLeft,y=headerTop+headerSize;
        if(numeric){buildNumbers(x,y,w);}
        else{
            if(emojiPage)emojiRow(EMOJIS[0],x,y,w,numberSize);else row("1234567890",STATIC,x,y,w,numberSize);
            y+=numberSize;
            if(emojiPage)emojiRow(EMOJIS[1],x,y,w,rowSize);
            else row(symbols?(secondSymbols?"[]{}<>^~%=":"@#$%&-+()/"):"qwertyuiop",symbols?STATIC:LETTER,x,y,w,rowSize);
            y+=rowSize;
            float indent=w/20f;
            if(emojiPage)emojiRow(EMOJIS[2],x+indent,y,w-2*indent,rowSize);
            else row(symbols?(secondSymbols?"€£¥₩¢©®™°":"*\"':;!?=_"):"asdfghjkl",symbols?STATIC:LETTER,x+indent,y,w-2*indent,rowSize);
            y+=rowSize;
            float side=w*1.2f/9.4f,middle=w-2*side;int start=keys.size();
            add(emojiPage?"ABC":symbols?(secondSymbols?"?123":"=\\<"):"⇧",null,emojiPage?MODE:symbols?PAGE:SHIFT,x,y,side,rowSize);
            if(emojiPage){
                float cell=middle/EMOJIS[3].length;
                for(int i=0;i<EMOJIS[3].length;i++)add(EMOJIS[3][i],EMOJIS[3][i],EMOJI_TEXT,x+side+i*cell,y,cell,rowSize);
            }else{
                String letters=symbols?(secondSymbols?"•√π÷×§¶":"\\|~`<>^"):"zxcvbnm";
                float cell=middle/letters.length();
                for(int i=0;i<letters.length();i++){String value=String.valueOf(letters.charAt(i));add(value,value,symbols?STATIC:LETTER,x+side+i*cell,y,cell,rowSize);}
            }
            add("⌫",null,DELETE,x+w-side,y,side,rowSize);extendRowEdges(start);
            y+=rowSize;buildFooter(x,y,w);
        }
        buildSuggestions();cacheLighting();geometryDirty=false;invalidate();scheduleAccessibilityRefresh();
    }
    private void buildFooter(float x,float y,float width){
        String[] labels={symbols||emojiPage?"ABC":"?123",commaLabel,emojiPage?"ABC":"☺","🌐","space",".",enterLabel};
        String[] outputs={null,commaLabel,null,null," ",".",null};
        int[] kinds={MODE,STATIC,EMOJI,SWITCH,SPACE,STATIC,ENTER};
        float[] weights={1.12f,.70f,.80f,.80f,3.85f,.70f,1.22f};
        float total=0;for(float weight:weights)total+=weight;int first=keys.size();
        for(int i=0;i<labels.length;i++){float cell=width*weights[i]/total;add(labels[i],outputs[i],kinds[i],x,y,cell,footerSize);x+=cell;}
        extendRowEdges(first);
    }
    private void buildNumbers(float x,float y,float width){
        float cell=width/4f;
        for(int row=0;row<3;row++){
            int first=keys.size();
            for(int col=0;col<3;col++){String value=String.valueOf(row*3+col+1);add(value,value,STATIC,x+col*cell,y,cell,rowSize);}
            if(row==0)add("⌫",null,DELETE,x+3*cell,y,cell,rowSize);
            else if(row==1)add(phone?"+":signed?"-":"←",phone?"+":signed?"-":null,phone||signed?STATIC:LEFT,x+3*cell,y,cell,rowSize);
            else add(phone?"*":decimal?".":"→",phone?"*":decimal?".":null,phone||decimal?STATIC:RIGHT,x+3*cell,y,cell,rowSize);
            extendRowEdges(first);y+=rowSize;
        }
        int first=keys.size();add("🌐",null,SWITCH,x,y,cell,rowSize);
        add("0","0",STATIC,x+cell,y,cell*(phone?1:2),rowSize);
        if(phone)add("#","#",STATIC,x+2*cell,y,cell,rowSize);
        add(enterLabel,null,ENTER,x+3*cell,y,cell,rowSize);extendRowEdges(first);
    }

    private String visibleLabel(Key key,boolean upper){return key.kind==LETTER&&upper?key.upperLegend:key.kind==SHIFT&&capsLock?"⇪":key.legend;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(BACKGROUND);
        final boolean upper=upperCase(),lit=lighting(),motion=animateRgb();
        final long now=SystemClock.uptimeMillis();
        paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<18;i++){
            paint.setColor(lampColors[i]);
            canvas.drawCircle((i+.5f)*getWidth()/18f,contentTop+stripeHeight*.5f,
                Math.min(dp(3),stripeHeight*.3f),paint);
        }
        paint.setTypeface(android.graphics.Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);
        if(suggestionKeys.isEmpty()){
            paint.setColor(0xffd9eaff);paint.setTextSize(Math.min(sp(12),headerSize*.5f));
            canvas.drawText(emojiPage?"Emoji  •  Christmas":headerLabel,getWidth()*.5f,
                headerTop+headerSize*.5f-(paint.ascent()+paint.descent())*.5f,paint);
        }else for(Key choice:suggestionKeys){
            paint.setColor(isPressed(choice)?0xff36596c:0xff1c3b4f);canvas.drawRoundRect(choice.bounds,dp(9),dp(9),paint);
            paint.setColor(0xffe0f8ea);paint.setTextSize(choice.fontSize);
            canvas.drawText(choice.legend,choice.bounds.centerX(),choice.baseline,paint);
        }
        canvas.getClipBounds(drawClip);
        for(int i=0;i<keys.size();i++){
            Key key=keys.get(i);
            if(key.bounds.right<drawClip.left||key.bounds.left>drawClip.right||key.bounds.bottom<drawClip.top||key.bounds.top>drawClip.bottom)continue;
            int accent=keyAccents[i];
            boolean control=key.kind!=LETTER&&key.kind!=STATIC&&key.kind!=SPACE&&key.kind!=EMOJI_TEXT;
            paint.setStyle(Paint.Style.FILL);paint.setColor(isPressed(key)?0xff528a95:key.kind==SHIFT&&upper?0xff4d706c:control?raisedColor:baseColor);
            float radius=Math.min(dp(10),key.bounds.height()*.24f);canvas.drawRoundRect(key.bounds,radius,radius,paint);
            paint.setColor(lit?keyBorders[i]:borderColor);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(lit?1.8f:1.2f));canvas.drawRoundRect(key.bounds,radius,radius,paint);
            if(recentKey==key&&now-pressEffectStarted<230&&motion){
                float progress=(now-pressEffectStarted)/230f;paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(Math.round((1-progress)*Math.min(200,brightness*2)),Color.red(accent),Color.green(accent),Color.blue(accent)));
                float size=dp(2+progress*4);canvas.drawCircle(key.bounds.centerX()-key.bounds.width()*.32f,key.bounds.centerY()-key.bounds.height()*.28f,size,paint);
                canvas.drawCircle(key.bounds.centerX()+key.bounds.width()*.32f,key.bounds.centerY()+key.bounds.height()*.26f,size*.65f,paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setColor(control?0xffdfefc0:Color.WHITE);paint.setTextSize(key.fontSize);
            canvas.drawText(visibleLabel(key,upper),key.bounds.centerX(),key.baseline,paint);
            if(accessibilityFocused==i){paint.setColor(0xffffe79b);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawRoundRect(key.bounds,radius,radius,paint);}
        }
        paint.setStyle(Paint.Style.FILL);
        // The empty bottom region is real clearance. Android draws its own navigation controls.
    }

    private Key keyForVirtual(int id){
        if(id>=SUGGESTION_ID&&id<SUGGESTION_ID+suggestionKeys.size())return suggestionKeys.get(id-SUGGESTION_ID);
        return id>=0&&id<keys.size()?keys.get(id):null;
    }
    private String accessibilityLabel(Key key){
        switch(key.kind){
            case SHIFT:return capsLock?"Caps lock on":upperCase()?"Shift on":"Shift off";
            case DELETE:return "Delete";
            case MODE:return symbols||emojiPage?"Letters":"Numbers and symbols";
            case PAGE:return secondSymbols?"First symbols page":"More symbols";
            case SWITCH:return "Switch keyboard";
            case EMOJI:return emojiPage?"Letters":"Emoji";
            case SPACE:return "Space";
            case ENTER:return "↵".equals(enterLabel)?"New line":enterLabel;
            case LEFT:return "Move cursor left";
            case RIGHT:return "Move cursor right";
            case SUGGEST:return "Suggestion "+key.legend;
            default:return visibleLabel(key,upperCase());
        }
    }
    private final AccessibilityNodeProvider accessibility=new AccessibilityNodeProvider(){
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            if(id==HOST_VIEW_ID){
                AccessibilityNodeInfo host=AccessibilityNodeInfo.obtain(FastKeyboardView.this);
                onInitializeAccessibilityNodeInfo(host);
                host.setClassName("android.inputmethodservice.KeyboardView");
                host.setContentDescription("SnowGlobe keyboard");
                for(int i=0;i<keys.size();i++)host.addChild(FastKeyboardView.this,i);
                for(int i=0;i<suggestionKeys.size();i++)host.addChild(FastKeyboardView.this,SUGGESTION_ID+i);
                return host;
            }
            Key key=keyForVirtual(id);if(key==null)return null;
            AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain(FastKeyboardView.this,id);
            info.setPackageName(getContext().getPackageName());info.setClassName("android.widget.Button");
            info.setSource(FastKeyboardView.this,id);info.setParent(FastKeyboardView.this);
            info.setText(visibleLabel(key,upperCase()));info.setContentDescription(accessibilityLabel(key));
            info.setClickable(true);info.setEnabled(isEnabled());info.setFocusable(true);
            info.setVisibleToUser(getVisibility()==VISIBLE&&getWindowVisibility()==VISIBLE);
            info.setAccessibilityFocused(accessibilityFocused==id);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            info.addAction(accessibilityFocused==id?AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS:
                AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS);
            Rect bounds=new Rect((int)Math.floor(key.hitBox.left),(int)Math.floor(key.hitBox.top),
                (int)Math.ceil(key.hitBox.right),(int)Math.ceil(key.hitBox.bottom));
            info.setBoundsInParent(bounds);int[] location=new int[2];getLocationOnScreen(location);
            bounds.offset(location[0],location[1]);info.setBoundsInScreen(bounds);return info;
        }
        @Override public AccessibilityNodeInfo findFocus(int focus){
            return focus==AccessibilityNodeInfo.FOCUS_ACCESSIBILITY&&accessibilityFocused!=NO_VIRTUAL?
                createAccessibilityNodeInfo(accessibilityFocused):null;
        }
        @Override public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text,int rootId){
            ArrayList<AccessibilityNodeInfo> found=new ArrayList<>();if(text==null)return found;
            String target=text.toLowerCase(Locale.ROOT);
            for(int i=0;i<keys.size()+suggestionKeys.size();i++){
                int id=i<keys.size()?i:SUGGESTION_ID+i-keys.size();
                if(rootId!=HOST_VIEW_ID&&rootId!=id)continue;
                if(accessibilityLabel(keyForVirtual(id)).toLowerCase(Locale.ROOT).contains(target))found.add(createAccessibilityNodeInfo(id));
            }
            return found;
        }
        @Override public boolean performAction(int id,int action,Bundle args){
            Key key=keyForVirtual(id);if(key==null||!isEnabled())return false;
            if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){
                if(accessibilityFocused==id)return false;clearAccessibilityFocus();accessibilityFocused=id;
                sendVirtualEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);invalidate();return true;
            }
            if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS){
                if(accessibilityFocused!=id)return false;clearAccessibilityFocus();return true;
            }
            if(action==AccessibilityNodeInfo.ACTION_CLICK){
                cancelTouches();perform(key,upperCase());sendVirtualEvent(id,AccessibilityEvent.TYPE_VIEW_CLICKED);invalidate();return true;
            }
            return false;
        }
    };
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return accessibility;}
    private void sendVirtualEvent(int id,int type){
        if(accessibilityManager==null||!accessibilityManager.isEnabled()||getParent()==null)return;
        AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setSource(this,id);
        event.setPackageName(getContext().getPackageName());event.setClassName("android.widget.Button");
        Key key=keyForVirtual(id);if(key!=null)event.setContentDescription(accessibilityLabel(key));
        getParent().requestSendAccessibilityEvent(this,event);
    }
    private void clearAccessibilityFocus(){
        if(accessibilityFocused==NO_VIRTUAL)return;int previous=accessibilityFocused;accessibilityFocused=NO_VIRTUAL;
        sendVirtualEvent(previous,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);invalidate();
    }
    private int virtualAt(float x,float y){
        for(int i=0;i<keys.size();i++)if(keys.get(i).isInside(x,y))return i;
        for(int i=0;i<suggestionKeys.size();i++)if(suggestionKeys.get(i).isInside(x,y))return SUGGESTION_ID+i;
        return NO_VIRTUAL;
    }
    private void updateHovered(int id){
        if(hoveredVirtual==id)return;int previous=hoveredVirtual;hoveredVirtual=id;
        if(id!=NO_VIRTUAL)sendVirtualEvent(id,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);
        if(previous!=NO_VIRTUAL)sendVirtualEvent(previous,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);
    }
    @Override public boolean dispatchHoverEvent(MotionEvent event){
        if(accessibilityManager!=null&&accessibilityManager.isEnabled()&&accessibilityManager.isTouchExplorationEnabled()){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_HOVER_ENTER:case MotionEvent.ACTION_HOVER_MOVE:
                    int id=virtualAt(event.getX(),event.getY());updateHovered(id);return id!=NO_VIRTUAL;
                case MotionEvent.ACTION_HOVER_EXIT:updateHovered(NO_VIRTUAL);return true;
                default:break;
            }
        }
        return super.dispatchHoverEvent(event);
    }
    @Override public boolean performClick(){super.performClick();return true;}

    private boolean isPressed(Key key){
        for(int i=0;i<pressed.size();i++){Press p=pressed.valueAt(i);if(!p.cancelled&&p.key==key)return true;}return false;
    }
    private Key hit(float x,float y){int id=virtualAt(x,y);return id==NO_VIRTUAL?null:keyForVirtual(id);}
    private boolean hasHeldDelete(){Press p=pressed.get(repeatPointer);return p!=null&&!p.cancelled&&p.key.kind==DELETE;}
    private void stopDeleteRepeat(){repeatPointer=-1;handler.removeCallbacks(beginDeleteRepeat);handler.removeCallbacks(deleteRepeat);}
    private void cancelTouches(){pressed.clear();stopDeleteRepeat();}
    private static boolean textKey(Key key){return key.kind==LETTER||key.kind==STATIC||key.kind==SPACE||key.kind==EMOJI_TEXT;}
    private void commitOlderTouches(){
        // A later thumb may lift first. Commit pending text in press order, once each.
        while(true){
            Press oldest=null;
            for(int i=0;i<pressed.size();i++){
                Press p=pressed.valueAt(i);
                if(!p.committed&&!p.cancelled&&!p.cursorDrag&&textKey(p.key)&&(oldest==null||p.order<oldest.order))oldest=p;
            }
            if(oldest==null)return;commitPress(oldest);
        }
    }
    private void commitPress(Press press){
        if(press.committed||press.cancelled||press.cursorDrag)return;
        press.committed=true;
        if(press.key.kind==LETTER)for(int i=0;i<pressed.size();i++){
            Press held=pressed.valueAt(i);if(held.origin.kind==SHIFT)held.usedAsModifier=true;
        }
        perform(press.key,press.upper||press.origin.kind==SHIFT);performClick();
    }
    private boolean spaceGesture(Press press,float x,float y){
        if(press.origin.kind!=SPACE||press.committed||press.cancelled)return false;
        float dx=x-press.downX,dy=y-press.downY,threshold=Math.max(touchSlop,dp(12));
        if(!press.cursorDrag&&Math.abs(dx)>threshold&&Math.abs(dx)>Math.abs(dy)*1.5f)press.cursorDrag=true;
        if(!press.cursorDrag)return false;
        if(x<0||x>=getWidth()||y<press.origin.hitBox.top-touchSlop||y>press.origin.hitBox.bottom+touchSlop){press.cancelled=true;return true;}
        int steps=clamp((int)((x-press.cursorX)/dp(12)),-32,32);
        if(steps!=0){
            sentenceBoundary=false;lastShiftTap=-1;
            actions.moveCursor(steps);press.cursorX+=steps*dp(12);recentTap=SystemClock.uptimeMillis();
        }
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled())return false;
        int action=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            if(action==MotionEvent.ACTION_DOWN)cancelTouches();
            commitOlderTouches();stopDeleteRepeat();
            Key key=hit(event.getX(index),event.getY(index));
            if(key!=null){
                Press press=new Press(key,event.getX(index),event.getY(index),++pressOrder,upperCase());
                if(key.kind!=SHIFT)for(int i=0;i<pressed.size();i++){
                    Press held=pressed.valueAt(i);if(held.origin.kind==SHIFT)held.usedAsModifier=true;
                }
                pressed.put(id,press);recentTap=SystemClock.uptimeMillis();
                if(key.kind==DELETE){commitPress(press);repeatPointer=id;handler.postDelayed(beginDeleteRepeat,400);}
                if(key.kind==SHIFT)invalidate();else invalidateKey(key);
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<event.getPointerCount();i++){
                int pointer=event.getPointerId(i);Press press=pressed.get(pointer);
                if(press==null||press.cancelled)continue;
                float x=event.getX(i),y=event.getY(i);
                if(pointer==repeatPointer&&!press.key.isInside(x,y)){
                    stopDeleteRepeat();press.cancelled=true;invalidateKey(press.key);continue;
                }
                if(press.committed)continue;
                if(spaceGesture(press,x,y))continue;
                Key next=hit(x,y),prior=press.key;
                if(next==null){press.cancelled=true;if(pointer==repeatPointer)stopDeleteRepeat();
                    if(press.origin.kind==SHIFT)invalidate();else invalidateKey(prior);}
                else if(next!=prior){
                    // A space gesture stays anchored to space until it is deliberate or cancelled.
                    if(press.origin.kind==SPACE){press.cancelled=true;invalidateKey(prior);continue;}
                    press.key=next;if(pointer==repeatPointer)stopDeleteRepeat();invalidateKey(prior);invalidateKey(next);
                }
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            Press press=pressed.get(id);if(id==repeatPointer)stopDeleteRepeat();
            if(press!=null){
                spaceGesture(press,event.getX(index),event.getY(index));
                pressed.remove(id);
                if(!press.cancelled&&!press.cursorDrag&&!press.committed&&press.key.isInside(event.getX(index),event.getY(index))){
                    if(press.key.kind!=SHIFT||!press.usedAsModifier)commitPress(press);
                }
                if(press.origin.kind==SHIFT)invalidate();else invalidateKey(press.key);
            }
            if(action==MotionEvent.ACTION_UP)cancelTouches();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){cancelTouches();invalidate();return true;}
        return true;
    }
    private void feedback(Key key){
        if(haptics)performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        clearPressEffect();recentTap=SystemClock.uptimeMillis();
        if(animateRgb()){recentKey=key;pressEffectStarted=recentTap;schedulePressEffectFrame();}
    }
    private void updateBoundaryHint(String value){
        if(value==null||numeric||privateInput)return;
        // This hint is local and immediate; a slow editor's caps reply must not gate typing.
        if("\n".equals(value)){sentenceBoundary=false;if(sentenceCaps||wordCaps)autoShift=true;}
        else if(" ".equals(value)){if(wordCaps||(sentenceCaps&&sentenceBoundary))autoShift=true;}
        else if(".".equals(value)||"?".equals(value)||"!".equals(value))sentenceBoundary=true;
        else if(!"\"".equals(value)&&!"'".equals(value)&&!")".equals(value))sentenceBoundary=false;
    }
    private void perform(Key key,boolean upper){
        boolean priorCase=upperCase();feedback(key);
        if(key.kind!=SHIFT)lastShiftTap=-1;
        switch(key.kind){
            case LETTER:
                actions.commit(upper?key.upperOutput:key.output);
                sentenceBoundary=false;
                if(!capsLock){manualShiftOverride=false;manualShift=false;autoShift=autoCapsCharacters;}
                break;
            case STATIC:case SPACE:case EMOJI_TEXT:actions.commit(key.output);updateBoundaryHint(key.output);break;
            case SHIFT:
                long now=SystemClock.uptimeMillis();
                if(capsLock){capsLock=false;manualShiftOverride=true;manualShift=false;lastShiftTap=-1;}
                else if(lastShiftTap>=0&&now-lastShiftTap<=ViewConfiguration.getDoubleTapTimeout()){
                    capsLock=true;manualShiftOverride=false;lastShiftTap=-1;
                }else{manualShift=!priorCase;manualShiftOverride=true;lastShiftTap=now;}
                break;
            case DELETE:sentenceBoundary=false;actions.delete();break;
            case ENTER:actions.enter();if("↵".equals(enterLabel))updateBoundaryHint("\n");break;
            case SWITCH:actions.switchKeyboard();break;
            case LEFT:sentenceBoundary=false;actions.moveCursor(-1);break;
            case RIGHT:sentenceBoundary=false;actions.moveCursor(1);break;
            case MODE:
                if(symbols||emojiPage){symbols=false;emojiPage=false;}else symbols=true;
                secondSymbols=false;buildKeys();break;
            case PAGE:secondSymbols=!secondSymbols;buildKeys();break;
            case EMOJI:emojiPage=!emojiPage;symbols=false;secondSymbols=false;buildKeys();break;
            case SUGGEST:actions.choose(key.output);break;
            default:break;
        }
        if(priorCase!=upperCase()||key.kind==SHIFT){invalidate();scheduleAccessibilityRefresh();}
    }

    // In-process tests dispatch actual MotionEvents and inspect measured Android geometry.
    RectF keyBoundsForTest(String label){for(Key key:keys)if(key.legend.equals(label))return new RectF(key.bounds);return null;}
    RectF keyHitBoxForTest(String label){for(Key key:keys)if(key.legend.equals(label))return new RectF(key.hitBox);return null;}
    int keyCountForTest(){return keys.size();}
    float safeBottomForTest(){return layoutNav;}
}
