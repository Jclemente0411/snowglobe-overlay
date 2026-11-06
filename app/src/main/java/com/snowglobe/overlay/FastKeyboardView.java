package com.snowglobe.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

/**
 * Lightweight, single-canvas input surface. The layout and labels are generated
 * only when its size/mode changes: not when characters are committed.
 * RGB is decorative and never changes key hitboxes or the input connection.
 */
final class FastKeyboardView extends View {
    interface InputActions {
        void commit(String text);
        void delete();
        void enter();
        void choose(String suggestion);
    }
    private static final int BACKGROUND=0xff10253a;
    private static final int[] XMAS={0xffec6b6b,0xff5bc997,0xffffc46d,0xff76c6f8,0xffd28be0};
    private static final int[] ICE={0xff8dd4ff,0xffd9eeff,0xff8caaff};
    private static final int[] GOLD={0xfff6bb66,0xffffe4a7,0xffff996a};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Key> keys=new ArrayList<>(56);
    private final SparseArray<Key> pressed=new SparseArray<>();
    private final ArrayList<Key> suggestionKeys=new ArrayList<>(3);
    private final SparseArray<float[]> starts=new SparseArray<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final InputActions actions;
    private boolean shifted=true, symbols=false, running=false;
    private int pulse=0;
    private long recentTap=0;
    private Key recentKey;
    private boolean batterySaver(){PowerManager p=(PowerManager)getContext().getSystemService(Context.POWER_SERVICE);return p!=null&&p.isPowerSaveMode();}
    private boolean lightingEffectsEnabled(){return preferences.getBoolean("keyboardRgb",true)&&preferences.getInt("keyboardRgbBrightness",75)>0;}
    private boolean animateRgb(){return running&&preferences.getBoolean("keyboardRgb",true)&&!preferences.getBoolean("reduceMotion",false)&&!batterySaver()&&preferences.getInt("keyboardRgbBrightness",75)>0&&preferences.getInt("keyboardRgbPattern",0)!=2;}

    private final Runnable rgbTick=new Runnable(){
        @Override public void run(){
            if(!animateRgb())return;
            pulse++;
            postInvalidateOnAnimation();
            int speed=Math.max(0,Math.min(100,preferences.getInt("keyboardRgbSpeed",45)));
            handler.postDelayed(this,Math.max(55,110-speed/2));
        }
    };
    private final Runnable deleteRepeat=new Runnable(){
        @Override public void run(){
            if(!hasHeldDelete())return;
            actions.delete();
            handler.postDelayed(this,78);
        }
    };
    private boolean deleteRepeating;
    private int repeatPointer=-1;

    static final class Key {
        final String legend,output;
        final int kind;
        final RectF bounds=new RectF();
        final RectF hitBox=new RectF();
        Key(String legend,String output,int kind){this.legend=legend;this.output=output;this.kind=kind;}
        boolean isInside(float x,float y){return hitBox.contains(x,y);}
    }
    static final int LETTER=0,STATIC=1,SHIFT=2,DELETE=3,MODE=4,ENTER=5,SPACE=6,SUGGEST=7;
    FastKeyboardView(Context c,SharedPreferences prefs,InputActions actions){
        super(c);
        this.preferences=prefs;this.actions=actions;
        setBackgroundColor(BACKGROUND);
        setHapticFeedbackEnabled(true);
        setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
    }
    private float dp(float size){return size*getResources().getDisplayMetrics().density;}
    private float sp(float size){return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,size,getResources().getDisplayMetrics());}
    private boolean landscape(){return getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;}
    private float headerHeight(){return dp(landscape()?27:45);}
    private float numberHeight(){return dp(landscape()?34:48);}
    private float letterHeight(){return dp(landscape()?43:(54+Math.max(0,Math.min(100,preferences.getInt("keyboardHeight",60)))*.23f));}
    private float bottomHeight(){return dp(landscape()?44:69);}
    private float navGap(){return dp(landscape()?12:26);}
    private float topStripe(){return dp(landscape()?8:12);}
    private float desiredHeight(){
        return dp(2)+topStripe()+headerHeight()+numberHeight()+3*letterHeight()+bottomHeight()+navGap()+dp(2);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        int height=Math.round(desiredHeight());
        int mode=MeasureSpec.getMode(heightSpec);
        if(mode==MeasureSpec.EXACTLY)height=MeasureSpec.getSize(heightSpec);
        else if(mode==MeasureSpec.AT_MOST)height=Math.min(height,MeasureSpec.getSize(heightSpec));
        setMeasuredDimension(width,height);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        buildKeys();
    }
    void start(){
        running=true;shifted=true;symbols=false;
        buildKeys();pulse=0;recentKey=null;handler.removeCallbacks(rgbTick);
        if(animateRgb())handler.post(rgbTick);
        invalidate();
    }
    void stop(){
        running=false;handler.removeCallbacks(rgbTick);handler.removeCallbacks(deleteRepeat);
        pressed.clear();starts.clear();suggestionKeys.clear();repeatPointer=-1;deleteRepeating=false;invalidate();
    }
    void setSuggestions(List<String> options){
        suggestionKeys.clear();
        if(options==null||options.isEmpty()){invalidateHeader();return;}
        final int count=Math.min(options.size(),3);
        if(getWidth()<=0){invalidateHeader();return;}
        float w=getWidth()-dp(12),x=dp(6),y=dp(2)+topStripe();
        for(int i=0;i<count;i++){
            String word=options.get(i);
            if(word==null||word.isEmpty())continue;
            Key key=new Key(word,word,SUGGEST);
            float cell=w/count;
            key.bounds.set(x+i*cell+dp(2),y+dp(2),x+(i+1)*cell-dp(2),y+headerHeight()-dp(2));
            key.hitBox.set(x+i*cell,y,x+(i+1)*cell,y+headerHeight());
            suggestionKeys.add(key);
        }
        invalidateHeader();
    }
    private void invalidateHeader(){
        invalidate(0,0,getWidth(),Math.round(topStripe()+headerHeight()+dp(4)));
    }
    private Key add(String legend,String output,int kind,float x,float y,float width,float height){
        Key key=new Key(legend,output,kind);
        float gx=dp(2.0f),gy=dp(3.0f);
        key.bounds.set(x+gx,y+gy,x+width-gx,y+height-gy);
        key.hitBox.set(x,y,x+width,y+height);
        keys.add(key);
        return key;
    }
    private void row(String legends,String outputs,int type,float x,float y,float rowWidth,float rowHeight){
        int count=legends.length();
        float cell=rowWidth/count;
        for(int i=0;i<count;i++){
            String label=String.valueOf(legends.charAt(i));
            String value=String.valueOf(outputs.charAt(i));
            add(label,value,type,x+i*cell,y,cell,rowHeight);
        }
    }
    private void buildKeys(){
        if(getWidth()<=0)return;
        keys.clear();
        suggestionKeys.clear();
        float w=getWidth()-dp(6),x0=dp(3);
        float y=dp(2)+topStripe()+headerHeight();
        row("1234567890","1234567890",STATIC,x0,y,w,numberHeight());
        y+=numberHeight();
        row(symbols?"1234567890":"qwertyuiop",symbols?"1234567890":"qwertyuiop",
            symbols?STATIC:LETTER,x0,y,w,letterHeight());
        y+=letterHeight();
        float indent=w/20f;
        row(symbols?"@#$%&-+()":"asdfghjkl",symbols?"@#$%&-+()":"asdfghjkl",
            symbols?STATIC:LETTER,x0+indent,y,w-2*indent,letterHeight());
        y+=letterHeight();
        float cell=w/9.4f;
        add("⇧",null,SHIFT,x0,y,cell*1.2f,letterHeight());
        String third=symbols?"*!?:;.,":"zxcvbnm";
        float middleW=w-cell*2.4f,midCell=middleW/third.length();
        for(int i=0;i<third.length();i++){
            String ch=String.valueOf(third.charAt(i));
            add(ch,ch,symbols?STATIC:LETTER,x0+cell*1.2f+i*midCell,y,midCell,letterHeight());
        }
        add("⌫",null,DELETE,x0+w-cell*1.2f,y,cell*1.2f,letterHeight());
        y+=letterHeight();
        // Independent, centered bottom row proportions; no duplicate Delete.
        String[] labels={symbols?"ABC":"?123",",","☺","space",".","↵"};
        String[] vals={null,",","☺"," ",".",null};
        int[] types={MODE,STATIC,STATIC,SPACE,STATIC,ENTER};
        float[] weights={1.15f,.65f,.75f,4.20f,.72f,1.20f};
        float total=0;for(float f:weights)total+=f;
        float cursor=x0;
        for(int i=0;i<labels.length;i++){
            float width=w*weights[i]/total;
            add(labels[i],vals[i],types[i],cursor,y,width,bottomHeight());
            cursor+=width;
        }
        invalidate();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        c.drawColor(BACKGROUND);
        float w=getWidth(),height=getHeight(),border=dp(1.2f);
        int theme=preferences.getInt("keyboardTheme",0);
        int[] colors=theme==1?ICE:theme==2?GOLD:XMAS;
        boolean rgb=preferences.getBoolean("keyboardRgb",true);
        float size=dp(3.0f);
        float lampsY=dp(6.5f);
        for(int i=0;i<18;i++){
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(colors[(i+(rgb?pulse:0))%colors.length]);
            c.drawCircle((i+.5f)*w/18f,lampsY,size,paint);
        }
        paint.setColor(0xffd9eaff);paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(sp(11));
        paint.setTextAlign(Paint.Align.CENTER);
        float headerTop=dp(2)+topStripe();
        float centerY=headerTop+headerHeight()*.5f-(paint.ascent()+paint.descent())*.5f;
        if(suggestionKeys.isEmpty()){
            c.drawText("❄ SnowGlobe  •  Christmas Keys",w/2,centerY,paint);
        }else{
            for(Key choice:suggestionKeys){
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xff1c3b4f);
                c.drawRoundRect(choice.bounds,dp(9),dp(9),paint);
                paint.setColor(0xffe0f8ea);paint.setTextSize(sp(14));
                c.save();c.clipRect(choice.bounds);
                c.drawText(choice.legend,choice.bounds.centerX(),
                    choice.bounds.centerY()-(paint.ascent()+paint.descent())*.5f,paint);
                c.restore();
            }
        }

        int base=theme==1?0xff224056:theme==2?0xff4b3b30:0xff263f53;
        int raised=theme==1?0xff39627e:theme==2?0xff72533d:0xff3b617b;
        int borderColor=theme==1?0xff6fa4c5:theme==2?0xffb89965:0xff65899f;
        final int strength=Math.max(0,Math.min(100,preferences.getInt("keyboardRgbBrightness",75)));
        final int pattern=preferences.getInt("keyboardRgbPattern",0);
        final boolean lighting=preferences.getBoolean("keyboardRgb",true)&&strength>0;
        final boolean motionAllowed=animateRgb();
        final long now=SystemClock.uptimeMillis();
        int index=0;
        for(Key key:keys){
            boolean down=isPressed(key);
            int accent=colors[(index+(pattern==0?pulse/2:0))%colors.length];
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(down?0xff528a95:((key.kind==SHIFT||key.kind==DELETE||key.kind==ENTER||key.kind==MODE)?raised:base));
            c.drawRoundRect(key.bounds,dp(10),dp(10),paint);
            // Keys Cafe-like color pulse: entirely decorative, bounded paint operations.
            if(lighting){
                int alpha=pattern==1?((index*7+pulse)%13==0?strength*2:strength/4):strength;
                if(pattern==2)alpha=Math.min(strength,52);
                if(!motionAllowed&&pattern!=2)alpha=Math.min(alpha,55);
                paint.setColor(Color.argb(Math.min(205,Math.max(0,alpha*2)),Color.red(accent),Color.green(accent),Color.blue(accent)));
            }else paint.setColor(borderColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(lighting?dp(1.8f):border);
            c.drawRoundRect(key.bounds,dp(10),dp(10),paint);
            // A short Christmas sparkle after each key press; no per-frame allocations.
            if(recentKey==key&&now-recentTap<230&&lighting){
                float progress=(now-recentTap)/230f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(Math.round((1-progress)*Math.min(200,strength*2)),
                    Color.red(accent),Color.green(accent),Color.blue(accent)));
                float radius=dp(2.0f+progress*4.0f);
                float cx=key.bounds.centerX(),cy=key.bounds.centerY();
                c.drawCircle(cx-key.bounds.width()*.32f,cy-key.bounds.height()*.28f,radius,paint);
                c.drawCircle(cx+key.bounds.width()*.32f,cy+key.bounds.height()*.26f,radius*.65f,paint);
            }
            paint.setStyle(Paint.Style.FILL);
            String label=key.kind==LETTER&&shifted?key.legend.toUpperCase(Locale.ROOT):key.legend;
            paint.setColor(key.kind>=SHIFT&&key.kind!=SPACE?0xffdfefc0:Color.WHITE);
            paint.setTextSize(sp(key.kind==SPACE?13:(label.length()>2?12:20)));
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            float cy=key.bounds.centerY()-(paint.ascent()+paint.descent())*.5f;
            c.drawText(label,key.bounds.centerX(),cy,paint);
            index++;
        }
        // Keep the native Android gesture/navigation area clear; draw no fake pill.
    }

    private final AccessibilityNodeProvider accessibility=new AccessibilityNodeProvider(){
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualId){
            if(virtualId==HOST_VIEW_ID){
                AccessibilityNodeInfo host=AccessibilityNodeInfo.obtain(FastKeyboardView.this);
                host.setClassName(FastKeyboardView.class.getName());
                host.setPackageName(getContext().getPackageName());
                host.setVisibleToUser(true);
                for(int i=0;i<keys.size();i++)host.addChild(FastKeyboardView.this,i);
                for(int i=0;i<suggestionKeys.size();i++)host.addChild(FastKeyboardView.this,1000+i);
                return host;
            }
            Key key;
            if(virtualId>=1000&&virtualId<1000+suggestionKeys.size())
                key=suggestionKeys.get(virtualId-1000);
            else if(virtualId>=0&&virtualId<keys.size())key=keys.get(virtualId);
            else return null;
            AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain(FastKeyboardView.this,virtualId);
            info.setPackageName(getContext().getPackageName());
            info.setClassName("android.widget.Button");
            info.setSource(FastKeyboardView.this,virtualId);
            info.setParent(FastKeyboardView.this);
            String label=key.kind==LETTER&&shifted?key.legend.toUpperCase(Locale.ROOT):key.legend;
            info.setText(label);
            info.setContentDescription(label);
            info.setClickable(true);info.setEnabled(true);
            info.setFocusable(true);info.setVisibleToUser(true);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
            Rect bounds=new Rect(Math.round(key.hitBox.left),Math.round(key.hitBox.top),
                 Math.round(key.hitBox.right),Math.round(key.hitBox.bottom));
            info.setBoundsInParent(bounds);
            int[] location=new int[2];getLocationOnScreen(location);
            bounds.offset(location[0],location[1]);info.setBoundsInScreen(bounds);
            return info;
        }
        @Override public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text,int rootId){
            ArrayList<AccessibilityNodeInfo> found=new ArrayList<>();
            if(text==null)return found;
            String target=text.toLowerCase(Locale.ROOT);
            for(int i=0;i<keys.size()+suggestionKeys.size();i++){
                int virtual=i<keys.size()?i:1000+i-keys.size();
                if(rootId!=HOST_VIEW_ID&&rootId!=virtual)continue;
                Key key=virtual>=1000?suggestionKeys.get(virtual-1000):keys.get(i);
                String label=key.kind==LETTER&&shifted?key.legend.toUpperCase(Locale.ROOT):key.legend;
                if(label.toLowerCase(Locale.ROOT).contains(target))found.add(createAccessibilityNodeInfo(virtual));
            }
            return found;
        }
        @Override public boolean performAction(int id,int action,Bundle args){
            if(action==AccessibilityNodeInfo.ACTION_CLICK){
                if(id>=1000&&id<1000+suggestionKeys.size()){
                    perform(suggestionKeys.get(id-1000));invalidate();return true;
                }
                if(id>=0&&id<keys.size()){perform(keys.get(id));invalidate();return true;}
            }
            return false;
        }
    };
    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return accessibility;}
    private boolean isPressed(Key key){
        for(int i=0;i<pressed.size();i++)if(pressed.valueAt(i)==key)return true;
        return false;
    }
    private Key hit(float x,float y){
        for(Key key:keys)if(key.isInside(x,y))return key;
        for(Key key:suggestionKeys)if(key.isInside(x,y))return key;
        return null;
    }
    private boolean hasHeldDelete(){
        return repeatPointer!=-1&&pressed.get(repeatPointer)!=null&&pressed.get(repeatPointer).kind==DELETE;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked(),index=event.getActionIndex();
        int id=event.getPointerId(index);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            Key key=hit(event.getX(index),event.getY(index));
            if(key!=null){
                pressed.put(id,key);
                starts.put(id,new float[]{event.getX(index),event.getY(index)});
                if(key.kind==DELETE){
                    repeatPointer=id;deleteRepeating=false;
                    handler.removeCallbacks(deleteRepeat);
                    handler.postDelayed(()->{
                        if(hasHeldDelete()){deleteRepeating=true;deleteRepeat.run();}
                    },450);
                }
                invalidate();
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            boolean changed=false;
            for(int i=0;i<event.getPointerCount();i++){
                int pointer=event.getPointerId(i);
                Key prior=pressed.get(pointer);
                if(prior==null)continue;
                Key hovered=hit(event.getX(i),event.getY(i));
                // Follow minor finger drift to the neighboring key instead of dropping taps.
                if(hovered!=null&&hovered!=prior){
                    pressed.put(pointer,hovered);changed=true;
                    if(pointer==repeatPointer&&hovered.kind!=DELETE){
                        repeatPointer=-1;handler.removeCallbacks(deleteRepeat);
                    }
                }else if(hovered==null&&prior!=null){
                    // Moving outside the keyboard cancels (prevents accidental swipes).
                    if(pointer==repeatPointer){repeatPointer=-1;handler.removeCallbacks(deleteRepeat);}
                    pressed.remove(pointer);changed=true;
                }
            }
            if(changed)invalidate();
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){
            Key key=pressed.get(id);
            boolean repeated=deleteRepeating&&id==repeatPointer;
            if(id==repeatPointer){repeatPointer=-1;handler.removeCallbacks(deleteRepeat);deleteRepeating=false;}
            pressed.remove(id);starts.remove(id);
            if(key!=null&&key.isInside(event.getX(index),event.getY(index))&&!repeated){
                perform(key);
            }
            invalidate();
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){
            pressed.clear();starts.clear();repeatPointer=-1;deleteRepeating=false;
            handler.removeCallbacks(deleteRepeat);invalidate();return true;
        }
        return true;
    }
    private void perform(Key key){
        if(preferences.getBoolean("keyboardHaptics",true))
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        recentKey=key;recentTap=SystemClock.uptimeMillis();
        if(lightingEffectsEnabled())postInvalidateDelayed(235);
        switch(key.kind){
            case LETTER:
                actions.commit(shifted?key.output.toUpperCase(Locale.ROOT):key.output);
                if(shifted)shifted=false;
                break;
            case STATIC:case SPACE:actions.commit(key.output);break;
            case SHIFT:shifted=!shifted;break;
            case DELETE:actions.delete();break;
            case ENTER:actions.enter();break;
            case MODE:symbols=!symbols;shifted=false;buildKeys();break;
            case SUGGEST:actions.choose(key.output);break;
            default:break;
        }
    }
    // Exposed only to in-process instrumentation. Ensures rows do not overlap the bottom safe area.
    RectF keyBoundsForTest(String label){
        for(Key key:keys)if(key.legend.equals(label))return new RectF(key.bounds);
        return null;
    }
    int keyCountForTest(){return keys.size();}
    float safeBottomForTest(){return navGap();}
}
