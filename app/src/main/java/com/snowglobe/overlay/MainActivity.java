package com.snowglobe.overlay;
import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
 private static SnowView overlay;
 private static WindowManager overlayManager;
 private SharedPreferences prefs;
 private LinearLayout page, tabs;
 private TextView status;
 private int selected=0;
 private final int BACK=0xff0a1526, CARD=0xff182b43, WHITE=0xfff4f7ff, MUTED=0xffb1c2d7, ACCENT=0xff8be6d0, SOFT=0xff29445d;
 private final String[] tabNames={"Home","Snow","Lights","Decor"};
 @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);getWindow().setStatusBarColor(BACK);getWindow().setNavigationBarColor(BACK);getWindow().getDecorView().setSystemUiVisibility(0);showScreen();if(prefs.getBoolean("effectsEnabled",true)&&Settings.canDrawOverlays(this))startEffects(false);}
 private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
 private GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
 private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);t.setIncludeFontPadding(true);return t;}
 private void add(LinearLayout box,View child,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(bottom);box.addView(child,p);}
 private void copy(LinearLayout box,String s,int size,int color,boolean bold,int bottom){add(box,label(s,size,color,bold),bottom);}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private LinearLayout card(String title,String sub){LinearLayout l=column();l.setPadding(dp(20),dp(20),dp(20),dp(20));l.setBackground(shape(CARD,24));add(page,l,16);copy(l,title,20,WHITE,true,sub==null?16:6);if(sub!=null)copy(l,sub,13,MUTED,false,16);return l;}
 private TextView action(String name,boolean primary){TextView b=label(name,16,primary?BACK:WHITE,true);b.setGravity(Gravity.CENTER);b.setBackground(shape(primary?ACCENT:SOFT,18));b.setMinHeight(dp(54));b.setPadding(dp(14),dp(12),dp(14),dp(12));return b;}
 private void toggle(LinearLayout parent,String title,String sub,String key,boolean def){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(8),0,dp(14));LinearLayout left=column();copy(left,title,16,WHITE,true,sub==null?0:3);if(sub!=null)copy(left,sub,12,MUTED,false,0);line.addView(left,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(this);sw.setChecked(prefs.getBoolean(key,def));line.addView(sw);add(parent,line,4);sw.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean(key,checked).apply();refresh();});}
 private void slider(LinearLayout parent,String title,String key,int def){LinearLayout group=column();TextView value=label(title+"  ·  "+prefs.getInt(key,def)+"%",15,WHITE,true);add(group,value,2);SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(prefs.getInt(key,def));group.addView(seek);add(parent,group,14);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean from){if(from){prefs.edit().putInt(key,n).apply();value.setText(title+"  ·  "+n+"%");refresh();}}});}
 private void option(LinearLayout parent,String title,String key,String[] names,int def){copy(parent,title,15,WHITE,true,8);LinearLayout wrap=column();wrap.setBackground(shape(SOFT,16));wrap.setPadding(dp(8),dp(5),dp(8),dp(5));Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);spinner.setAdapter(adapter);spinner.setSelection(Math.max(0,Math.min(names.length-1,prefs.getInt(key,def))));wrap.addView(spinner,new LinearLayout.LayoutParams(-1,dp(44)));add(parent,wrap,18);spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> a){}public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id){prefs.edit().putInt(key,pos).apply();refresh();}});}
 private void scene(LinearLayout parent,String name,String detail,int palette,int design,int speed,int density,boolean party){TextView button=action(name+"   ›",false);button.setGravity(Gravity.CENTER_VERTICAL);add(parent,button,8);copy(parent,detail,12,MUTED,false,14);button.setOnClickListener(v->{prefs.edit().putBoolean("snow",true).putBoolean("lights",true).putInt("palette",palette).putInt("lightDesign",design).putInt("speed",speed).putInt("density",density).putBoolean("candy",party).putBoolean("gifts",party).putBoolean("trees",party).putBoolean("snowmen",party).putBoolean("bulbs",party).putBoolean("stars",party).putBoolean("confetti",party).apply();refresh();Toast.makeText(this,name+" selected",Toast.LENGTH_SHORT).show();showScreen();});}
 private void showScreen(){
 LinearLayout outer=column();outer.setBackgroundColor(BACK);outer.setFitsSystemWindows(true);
 LinearLayout header=column();header.setPadding(dp(22),dp(22),dp(22),dp(8));copy(header,"SnowGlobe  ❄",29,WHITE,true,4);copy(header,"Make every day feel like Christmas",13,MUTED,false,16);outer.addView(header);
 HorizontalScrollView nav=new HorizontalScrollView(this);nav.setHorizontalScrollBarEnabled(false);tabs=new LinearLayout(this);tabs.setPadding(dp(16),0,dp(16),dp(12));for(int i=0;i<tabNames.length;i++){final int tab=i;TextView t=label(tabNames[i],14,i==selected?BACK:WHITE,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(19),dp(12),dp(19),dp(12));t.setBackground(shape(i==selected?ACCENT:SOFT,24));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,dp(46));tp.rightMargin=dp(8);tabs.addView(t,tp);t.setOnClickListener(v->{selected=tab;showScreen();});}nav.addView(tabs);outer.addView(nav);
 ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);page=column();page.setPadding(dp(16),dp(6),dp(16),dp(30));scroll.addView(page);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
 if(selected==0)home();else if(selected==1)snow();else if(selected==2)lights();else decor();
 setContentView(outer);
 }
 private void home(){
 LinearLayout hero=card("Your Christmas, your way","Snow and festive lights over your existing wallpaper.");
 status=label("",14,ACCENT,true);add(hero,status,16);updateStatus();
 TextView start=action("Start Christmas magic",true);add(hero,start,10);start.setOnClickListener(v->{prefs.edit().putBoolean("effectsEnabled",true).apply();startEffects(true);updateStatus();});
 TextView stop=action("Pause effects",false);add(hero,stop,0);stop.setOnClickListener(v->{prefs.edit().putBoolean("effectsEnabled",false).apply();stopEffects();updateStatus();});
 LinearLayout scenes=card("Christmas scenes","Choose a look in one tap. Fine-tune it in the tabs above.");
 scene(scenes,"Classic Christmas","Traditional red, green and warm holiday glow",0,0,50,55,false);
 scene(scenes,"Winter Wonderland","Cool blue lights and peaceful snowfall",1,1,35,65,false);
 scene(scenes,"North Pole Party","Colorful lights and falling Christmas decorations",3,2,65,75,true);
 LinearLayout settings=card("Comfort & performance","Choose a gentler animation experience.");
 toggle(settings,"Gentle motion","Reduce animation frequency to save battery","gentleMotion",true);
 copy(page,"Effects appear on supported screens outside SnowGlobe. Android controls the notification shade and secure lock screen.",12,MUTED,false,10);
 }
 private void snow(){LinearLayout l=card("Snowfall studio","A peaceful flurry or a full winter wonderland.");toggle(l,"Falling snow","Show animated snowflakes","snow",true);slider(l,"Snow speed","speed",50);slider(l,"Snowflake size","size",50);slider(l,"Snow density","density",55);option(l,"Snowflake design","snowStyle",new String[]{"Soft snow","Classic six-point crystal","Minimal icy crystal"},1);}
 private void lights(){LinearLayout l=card("Christmas lights","Design the perfect strand across your screen.");toggle(l,"Show Christmas lights","Hide or show the top strand","lights",true);toggle(l,"Animated twinkle","Switch off for steady bulbs","twinkle",true);option(l,"Strand design","lightDesign",new String[]{"Festive garland","Draped Christmas lights","Retro glowing bulbs"},0);option(l,"Color palette","palette",new String[]{"Classic Christmas","Winter wonderland","Warm golden glow","Candy-colored rainbow"},0);slider(l,"Bulb size","lightSize",55);}
 private void decor(){LinearLayout l=card("Falling decorations","Mix and match your favorite holiday shapes.");toggle(l,"Candy canes","Red and white holiday treats","candy",false);toggle(l,"Wrapped presents","Tiny Christmas gifts","gifts",false);toggle(l,"Christmas trees","Little evergreens","trees",false);toggle(l,"Snowmen","Friendly frosty characters","snowmen",false);toggle(l,"Christmas bulbs","Colorful ornaments","bulbs",false);toggle(l,"Golden stars","A little sparkle","stars",false);toggle(l,"Holiday confetti","Extra celebration","confetti",false);}
 private void updateStatus(){if(status!=null)status.setText(prefs.getBoolean("effectsEnabled",true)&&overlay!=null?"●  Effects running — go Home to see them":"○  Effects paused or permission needed");}
 private void refresh(){if(overlay!=null)overlay.invalidate();}
 @Override protected void onResume(){super.onResume();if(overlay!=null)overlay.setVisibility(View.INVISIBLE);if(prefs!=null&&prefs.getBoolean("effectsEnabled",true)&&Settings.canDrawOverlays(this))startEffects(false);updateStatus();}
 @Override protected void onStop(){super.onStop();if(overlay!=null&&prefs.getBoolean("effectsEnabled",true))overlay.setVisibility(View.VISIBLE);}
 private void startEffects(boolean notify){if(!Settings.canDrawOverlays(this)){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));Toast.makeText(this,"Enable Display over other apps, then return",Toast.LENGTH_LONG).show();return;}if(overlay!=null){if(notify)Toast.makeText(this,"Effects enabled — press Home",Toast.LENGTH_SHORT).show();return;}overlay=new SnowView(getApplicationContext(),prefs);overlayManager=(WindowManager)getApplicationContext().getSystemService(WINDOW_SERVICE);WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;try{overlayManager.addView(overlay,params);overlay.setVisibility(View.INVISIBLE);if(notify)Toast.makeText(this,"Christmas magic enabled! Press Home.",Toast.LENGTH_SHORT).show();}catch(Exception e){overlay=null;Toast.makeText(this,"Could not start effects",Toast.LENGTH_LONG).show();}}
 private void stopEffects(){if(overlay!=null){try{overlayManager.removeView(overlay);}catch(Exception ignored){}overlay=null;}}
}
