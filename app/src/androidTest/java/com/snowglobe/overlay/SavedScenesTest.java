package com.snowglobe.overlay;
import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class SavedScenesTest {
    @Test public void saveLoadAndUndoDoNotResumeOrChangePowerChoices(){
        Context context=ApplicationProvider.getApplicationContext();SharedPreferences prefs=context.getSharedPreferences("snowglobe",0);
        SavedScenesStore store=new SavedScenesStore(context);
        try{
            prefs.edit().putBoolean("effectsEnabled",false).putInt("qualityMode",0).putBoolean("reduceMotion",true).putInt("speed",22).commit();
            SceneSnapshot previous=store.capture();store.save(0,"My winter");
            prefs.edit().putInt("speed",88).commit();store.apply(store.read(0),store.name(0));
            assertEquals(22,prefs.getInt("speed",0));assertFalse(prefs.getBoolean("effectsEnabled",true));assertEquals(0,prefs.getInt("qualityMode",9));assertTrue(prefs.getBoolean("reduceMotion",false));
            store.apply(previous,"Before");assertEquals(22,prefs.getInt("speed",0));store.delete(0);assertFalse(store.exists(0));
        }finally{store.delete(0);prefs.edit().putBoolean("reduceMotion",false).putInt("qualityMode",1).putBoolean("effectsEnabled",false).commit();}
    }
    @Test public void commerceIsDormantAndNoBillingPermissionRequested() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();assertFalse(PremiumPolicy.mayLaunchCheckout());assertFalse(PremiumPolicy.PAYWALL_VISIBLE);assertTrue(PremiumPolicy.allFeaturesAvailable());
        String[] permissions=c.getPackageManager().getPackageInfo(c.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        if(permissions!=null)for(String p:permissions){assertNotEquals("com.android.vending.BILLING",p);assertNotEquals("android.permission.INTERNET",p);}
    }
}
