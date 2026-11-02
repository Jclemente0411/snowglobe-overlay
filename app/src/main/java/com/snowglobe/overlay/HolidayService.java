package com.snowglobe.overlay;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

public class HolidayService extends Service {
 public static final String START="com.snowglobe.overlay.START", STOP="com.snowglobe.overlay.STOP";
 private WindowManager wm; private SnowView view;
 private final BroadcastReceiver screen=new BroadcastReceiver(){
  @Override public void onReceive(Context c,Intent i){
   if(view==null)return;
   if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))view.setAnimating(false);
   if(Intent.ACTION_SCREEN_ON.equals(i.getAction())){view.setAnimating(true);view.burst();}
  }
 };
 @Override public void onCreate(){super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);
  IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_SCREEN_OFF);registerReceiver(screen,f);
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
  if(view==null){view=new SnowView(this);
   WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
   p.gravity=Gravity.TOP|Gravity.START;try{wm.addView(view,p);}catch(RuntimeException e){view=null;stopSelf();return START_NOT_STICKY;}
  }
  view.setAnimating(true);return START_STICKY;
 }
 @Override public void onDestroy(){unregisterReceiver(screen);if(view!=null){wm.removeView(view);view=null;}super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}