package com.snowglobe.overlay;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import android.content.SharedPreferences;
public class MainActivity extends Activity {
 private WindowManager wm; private SnowView overlay;
 private SharedPreferences prefs;
 private LinearLayout root;
 @Override public void onCreate(Bundle b){super.onCreate(b);wm=(WindowManager)getSystemService(WINDOW_SERVICE);prefs=getSharedPreferences("snowglobe",MODE_PRIVATE);render();}
 private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,14,0,12);root.addView(t);return t;}
 private void option(String title,String key,boolean initial){Switch sw=new Switch(this);sw.setText(title);sw.setTextColor(Color.WHITE);sw.setTextSize(17);sw.setPadding(0,12,0,12);sw.setChecked(prefs.getBoolean(key,initial));root.addView(sw);sw.setOnCheckedChangeListener((v,checked)->{prefs.edit().putBoolean(key,checked).apply();refreshOverlay();});}
 private void colorOption(){String[] names={"Classic Christmas","Winter Blue","Warm Gold","Rainbow"};Spinner spinner=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);spinner.setAdapter(a);spinner.setSelection(prefs.getInt("palette",0));root.addView(spinner);spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){prefs.edit().putInt("palette",pos).apply();refreshOverlay();}});}
 private void render(){ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(36,48,36,50);root.setBackgroundColor(0xff102238);scroll.addView(root);
 label("❄ SnowGlobe",28);label("Bring holiday magic over your screen. Your wallpaper stays unchanged.",15);
 label("FREE EFFECTS",20);option("Falling snow","snow",true);option("Twinkling lights","lights",true);label("Holiday light colors",17);colorOption();
 label("HOLIDAY PLUS • COMING SOON",20);label("Planned $2.99 one-time unlock: falling presents, candy canes, stars, and festive confetti. Premium effects are not available for purchase yet.",15);
 Button start=new Button(this);start.setText("Start effects");root.addView(start);start.setOnClickListener(v->startOverlay());
 Button stop=new Button(this);stop.setText("Stop effects");root.addView(stop);stop.setOnClickListener(v->stopOverlay());
 label("SnowGlobe needs Display over other apps permission. Effects are visual only and do not capture your screen or touches. They stop when this app closes.",13);
 setContentView(scroll);}
 private void refreshOverlay(){if(overlay!=null)overlay.invalidate();}
 private void startOverlay(){if(!Settings.canDrawOverlays(this)){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));Toast.makeText(this,"Allow display over other apps, then return and tap Start",Toast.LENGTH_LONG).show();return;}if(overlay!=null)return;overlay=new SnowView(this,prefs);WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;try{wm.addView(overlay,p);}catch(Exception e){overlay=null;Toast.makeText(this,"Unable to start overlay",Toast.LENGTH_LONG).show();}}
 private void stopOverlay(){if(overlay!=null){wm.removeView(overlay);overlay=null;}}
 @Override protected void onDestroy(){stopOverlay();super.onDestroy();}
}