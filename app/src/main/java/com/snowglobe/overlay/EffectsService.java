package com.snowglobe.overlay;
import android.app.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;

public class EffectsService extends Service {
 public static final String START="com.snowglobe.overlay.START",STOP="com.snowglobe.overlay.STOP",HIDE="com.snowglobe.overlay.HIDE",SHOW="com.snowglobe.overlay.SHOW";
 private static final String CHANNEL="snowglobe_effects";
 private WindowManager manager;
 private SnowView view;
 @Override public void onCreate(){super.onCreate();NotificationChannel channel=new NotificationChannel(CHANNEL,"Christmas effects",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(channel);}
 private Notification notification(){Intent open=new Intent(this,StudioActivity.class);PendingIntent tap=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_gallery).setContentTitle("SnowGlobe effects running").setContentText("Tap to customize or pause Christmas magic").setContentIntent(tap).setOngoing(true).build();}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  String action=intent==null?START:intent.getAction();
  if(STOP.equals(action)){stopSelf();return START_NOT_STICKY;}
  if(!getSharedPreferences("snowglobe",MODE_PRIVATE).getBoolean("effectsEnabled",true)||!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
  if(Build.VERSION.SDK_INT>=34)startForeground(101,notification(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(101,notification());
  if(view==null)createOverlay();
  if(view!=null){if(HIDE.equals(action))view.setVisibility(View.INVISIBLE);else if(SHOW.equals(action))view.setVisibility(View.VISIBLE);}
  return START_STICKY;
 }
 private void createOverlay(){try{manager=(WindowManager)getSystemService(WINDOW_SERVICE);view=new SnowView(getApplicationContext(),getSharedPreferences("snowglobe",MODE_PRIVATE));WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;manager.addView(view,p);}catch(Exception e){view=null;stopSelf();}}
 @Override public void onDestroy(){if(view!=null){try{manager.removeView(view);}catch(Exception ignored){}view=null;}super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
