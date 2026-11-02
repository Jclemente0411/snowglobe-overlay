package com.snowglobe.overlay;

import android.content.*;
import android.os.*;

/** Event-driven power/thermal snapshot. No wake locks, alarms or continuous polling. */
public final class RenderPowerMonitor {
    private final Context context;
    private final Runnable changed;
    private boolean attached;
    public boolean interactive=true,powerSaver=false;
    public int thermal=0;
    private Object thermalListener;
    public RenderPowerMonitor(Context context,Runnable changed){this.context=context;this.changed=changed;}
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){read();changed.run();}
    };
    public void read(){
        PowerManager pm=context.getSystemService(PowerManager.class);
        if(pm==null)return;
        interactive=pm.isInteractive();powerSaver=pm.isPowerSaveMode();
        if(Build.VERSION.SDK_INT>=29)thermal=pm.getCurrentThermalStatus();
    }
    public void attach(){
        if(attached)return;attached=true;read();
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver,filter);
        if(Build.VERSION.SDK_INT>=29)attachThermal();
    }
    @android.annotation.TargetApi(29)
    private void attachThermal(){
        PowerManager pm=context.getSystemService(PowerManager.class);if(pm==null)return;
        PowerManager.OnThermalStatusChangedListener listener=status->{thermal=status;changed.run();};
        thermalListener=listener;pm.addThermalStatusListener(context.getMainExecutor(),listener);
    }
    public void close(){
        if(!attached)return;attached=false;
        try{context.unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}
        if(Build.VERSION.SDK_INT>=29&&thermalListener!=null){
            PowerManager pm=context.getSystemService(PowerManager.class);
            if(pm!=null)pm.removeThermalStatusListener((PowerManager.OnThermalStatusChangedListener)thermalListener);
            thermalListener=null;
        }
    }
}
