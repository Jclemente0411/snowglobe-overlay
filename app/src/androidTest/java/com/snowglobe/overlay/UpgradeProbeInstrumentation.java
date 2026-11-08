package com.snowglobe.overlay;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;

/** Two independent invocations around an actual signed adb install -r. Never grants Plus. */
public final class UpgradeProbeInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context context=getTargetContext();
            SharedPreferences prefs=context.getSharedPreferences("snowglobe",Context.MODE_PRIVATE);
            boolean enabled=Boolean.parseBoolean(arguments.getString("enabled","false"));
            String phase=arguments.getString("phase","");
            long version=context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode();
            if("seed".equals(phase)){
                require(version==100031,"Baseline must be the delivered 0.22.2 bundle");
                require(prefs.edit().putBoolean("effectsEnabled",enabled)
                    .putBoolean("_coverageRestored0215",true).putBoolean("edgeOnly",false)
                    .putBoolean("snow",true).putBoolean("lights",true)
                    .putInt("density",73).putInt("snowVisibility",61).putInt("qualityMode",0)
                    .putString("selectedScene","Upgrade continuity").putString("_upgradeProbe","0222-to-0223").commit(),"Could not seed preferences");
                SavedScenesStore scenes=new SavedScenesStore(context);scenes.save(2,"Upgrade continuity");
                // Force the asynchronous scene write to disk before ending this process.
                require(context.getSharedPreferences("snowglobe_scenes",0).edit().putBoolean("_upgradeProbe",true).commit(),"Scene did not persist");
            }else if("verify".equals(phase)){
                require(version==100032,"New bundle was not installed");
                require("0222-to-0223".equals(prefs.getString("_upgradeProbe","")),"Update lost app data");
                require(prefs.getBoolean("effectsEnabled",!enabled)==enabled,"Update changed explicit playback state");
                require(prefs.getInt("density",-1)==73&&prefs.getInt("snowVisibility",-1)==61,"Update lost visual settings");
                require(prefs.getInt("qualityMode",-1)==0&&!prefs.getBoolean("edgeOnly",true),"Update changed quality or coverage");
                SavedScenesStore scenes=new SavedScenesStore(context);
                require(scenes.exists(2)&&"Upgrade continuity".equals(scenes.name(2)),"Update lost saved scene");
                require(Integer.valueOf(73).equals(scenes.read(2).values().get("density")),"Saved scene contents changed");
                require(Settings.canDrawOverlays(context),"Update lost overlay permission");
            }else throw new AssertionError("Unknown probe phase");
            Activity activity=startActivitySync(new Intent(context,StudioActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(activity::finish);
            context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until=SystemClock.uptimeMillis()+10000;
            boolean[] attached={false};
            do{
                runOnMainSync(()->attached[0]=EffectsService.isOverlayAttached());
                if(attached[0]==enabled)break;
                SystemClock.sleep(100);
            }while(SystemClock.uptimeMillis()<until);
            require(attached[0]==enabled,"Overlay did not respect playback state after launch and Home");
            require(prefs.getBoolean("effectsEnabled",!enabled)==enabled,"Opening Studio changed playback state");
            result.putString("upgrade_result","PASS "+phase+" enabled="+enabled+" version="+version);
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            result.putString("upgrade_result","FAIL "+error.getClass().getSimpleName()+": "+error.getMessage());
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
