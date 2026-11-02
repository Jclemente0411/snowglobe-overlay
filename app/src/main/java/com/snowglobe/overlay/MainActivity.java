package com.snowglobe.overlay;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
public class MainActivity extends Activity {
 private LinearLayout root;
 private void label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(0,12,0,18);root.addView(t);}
 private void action(String text,Runnable r){Button b=new Button(this);b.setText(text);root.addView(b);b.setOnClickListener(v->r.run());}
 @Override public void onCreate(Bundle b){super.onCreate(b);
  ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(35,65,35,35);root.setBackgroundColor(0xff0b192e);scroll.addView(root);setContentView(scroll);
  label("SnowGlobe ❄",30);label("Your personal Christmas studio",18);
  label("Christmas magic",24);label("Holiday effects appear over supported apps. Return Home to enjoy them. Android may hide overlays on the secure lock screen.",16);
  action("Start Christmas magic",()->{if(!Settings.canDrawOverlays(this)){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));return;}Intent i=new Intent(this,HolidayService.class);i.setAction(HolidayService.START);startService(i);});
  action("Pause effects",()->{Intent i=new Intent(this,HolidayService.class);i.setAction(HolidayService.STOP);startService(i);});
  action("Go Home to enjoy",()->{Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_HOME);startActivity(i);});
  label("Christmas scenes",24);
  action("Classic Christmas",()->preset(0));action("Winter Wonderland",()->preset(1));action("North Pole Party",()->preset(2));
  label("Light placement",24);
  action("Top garland",()->placement(0));action("Both sides",()->placement(1));action("Bottom garland",()->placement(2));action("Full frame",()->placement(3));action("Hanging icicles",()->placement(4));
  label("Effects use a touch-through overlay. Lock-screen support depends on Android and your device.",14);
 }
 private void preset(int n){getSharedPreferences("holiday",0).edit().putInt("scene",n).apply();restart();}
 private void placement(int n){getSharedPreferences("holiday",0).edit().putInt("lights",n).apply();restart();}
 private void restart(){if(Settings.canDrawOverlays(this)){stopService(new Intent(this,HolidayService.class));startService(new Intent(this,HolidayService.class));}}
}