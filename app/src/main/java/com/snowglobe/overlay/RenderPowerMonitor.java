package com.snowglobe.overlay;

import android.content.*;
import android.os.*;

/** Event-driven power/thermal snapshot. No wake locks, alarms or continuous polling. */
public final class RenderPowerMonitor {
    private final Context context;
    private final Runnable changed;
    private boolean attached,receiverRegistered,thermalEventsUnavailable;
    public boolean interactive=true,powerSaver=false;
    public int thermal=0;
    private Object thermalListener;
    public RenderPowerMonitor(Context context,Runnable changed){this.context=context;this.changed=changed;}
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){read();changed.run();}
    };
    public void read(){
        PowerManager pm=context.getSystemService(PowerManager.class);
        if(pm==null){powerSaver=true;thermal=2;return;}
        try{interactive=pm.isInteractive();powerSaver=pm.isPowerSaveMode();}
        catch(RuntimeException unavailable){powerSaver=true;}
        if(Build.VERSION.SDK_INT>=29){
            try{thermal=pm.getCurrentThermalStatus();}
            catch(RuntimeException unavailable){thermal=2;}
            if(thermalEventsUnavailable)thermal=Math.max(thermal,2);
        }
    }
    public void attach(){
        if(attached)return;attached=true;thermalEventsUnavailable=false;read();
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        try{
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(receiver,filter);
            receiverRegistered=true;
        }catch(RuntimeException unavailable){powerSaver=true;}
        if(Build.VERSION.SDK_INT>=29)attachThermal();
    }
    @android.annotation.TargetApi(29)
    private void attachThermal(){
        PowerManager pm=context.getSystemService(PowerManager.class);if(pm==null)return;
        PowerManager.OnThermalStatusChangedListener listener=status->{thermal=status;changed.run();};
        try{
            pm.addThermalStatusListener(context.getMainExecutor(),listener);
            thermalListener=listener;
        }catch(RuntimeException unavailable){
            // Optional system monitoring must not prevent the keyboard from opening.
            // Keep decoration conservative while heat changes cannot be observed.
            thermalEventsUnavailable=true;thermal=Math.max(thermal,2);
        }
    }
    public void close(){
        if(!attached)return;attached=false;
        if(receiverRegistered){
            try{context.unregisterReceiver(receiver);}catch(RuntimeException ignored){}
            receiverRegistered=false;
        }
        if(Build.VERSION.SDK_INT>=29&&thermalListener!=null){
            PowerManager pm=context.getSystemService(PowerManager.class);
            try{if(pm!=null)pm.removeThermalStatusListener((PowerManager.OnThermalStatusChangedListener)thermalListener);}
            catch(RuntimeException unavailable){/* The system service may disappear during teardown. */}
            thermalListener=null;
        }
    }
}
