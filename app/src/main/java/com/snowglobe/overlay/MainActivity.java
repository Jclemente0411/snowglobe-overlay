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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
public class MainActivity extends Activity {
 private WindowManager wm; private SnowView snow;
 @Override public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(40,70,40,30);root.setBackgroundColor(0xff102238);
 TextView title=new TextView(this);title.setText("❄ SnowGlobe Christmas Overlay");title.setTextColor(Color.WHITE);title.setTextSize(24);root.addView(title);
 TextView info=new TextView(this);info.setText("Your normal wallpaper and icons stay untouched. Snow and blinking lights appear over regular apps. Android may restrict some screens.");info.setTextColor(Color.WHITE);info.setPadding(0,25,0,25);root.addView(info);
 Button start=new Button(this);start.setText("Start snow");root.addView(start);start.setOnClickListener(v->startSnow());
 Button stop=new Button(this);stop.setText("Stop snow");root.addView(stop);stop.setOnClickListener(v->stopSnow());
 setContentView(root);wm=(WindowManager)getSystemService(WINDOW_SERVICE);
 }
 private void startSnow(){if(!Settings.canDrawOverlays(this)){Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));startActivity(i);return;} if(snow!=null)return;
 snow=new SnowView(this);WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;wm.addView(snow,p);
 }
 private void stopSnow(){if(snow!=null){wm.removeView(snow);snow=null;}}
 @Override protected void onDestroy(){stopSnow();super.onDestroy();}
}