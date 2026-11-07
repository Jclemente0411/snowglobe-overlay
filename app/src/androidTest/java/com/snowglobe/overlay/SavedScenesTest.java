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
    @Test public void holidayPlusUsesPlayBillingAndFailsClosedWithoutAVerificationKey() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        assertEquals("holiday_plus_lifetime",PremiumPolicy.PRODUCT_ID);
        assertEquals(299,PremiumPolicy.PROPOSED_USD_PRICE_CENTS);
        assertTrue("The upgrade and Restore entry points are part of this release",PremiumPolicy.PAYWALL_VISIBLE);
        assertFalse("No beta entitlement bypass may remain",PremiumPolicy.allFeaturesAvailable());
        assertFalse(HolidayPlusEntitlement.isVerificationConfigured(""));
        assertFalse(HolidayPlusEntitlement.isVerificationConfigured("not-a-Play-RSA-key"));
        assertFalse(HolidayPlusEntitlement.isOwned(c,""));
        assertFalse("A product response alone cannot enable checkout without verification",
            PremiumPolicy.mayLaunchCheckout(false,true,true,false,false,false));
        String[] permissions=c.getPackageManager().getPackageInfo(c.getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertNotNull(permissions);boolean billing=false;
        for(String p:permissions){if("com.android.vending.BILLING".equals(p))billing=true;assertNotEquals("android.permission.INTERNET",p);}
        assertTrue("The paid upgrade must use Google's billing permission",billing);
    }
    @Test public void savedVisualSettingsCannotGrantPaidEffects(){
        Context c=ApplicationProvider.getApplicationContext();
        SharedPreferences prefs=c.getSharedPreferences("scene-entitlement-test",0);
        prefs.edit().clear().putBoolean("candy",true).putBoolean("gifts",true)
            .putBoolean("screenSparkles",true).putInt("particleDirection",7).commit();
        try{
            SceneSnapshot restored=SceneSnapshot.decode(SceneSnapshot.capture(prefs.getAll()).encode());
            assertEquals(Boolean.TRUE,restored.values().get("candy"));
            SceneSettings free=new SceneSettings(prefs,false);
            assertEquals(0,free.decorations.length);assertFalse(free.sparks);assertEquals(0,free.direction);
            // Only this renderer fixture supplies verified ownership; no preference toggles it.
            SceneSettings paid=new SceneSettings(prefs,true);
            assertEquals(2,paid.decorations.length);assertTrue(paid.sparks);assertEquals(7,paid.direction);
            assertFalse(HolidayPlusEntitlement.isOwned(c,""));
        }finally{prefs.edit().clear().commit();}
    }
}
