package com.snowglobe.overlay;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Verifies the installed package, including disabled components, after removing the IME. */
@RunWith(AndroidJUnit4.class)
public final class EffectsOnlyPackageTest {
    @Test public void installedAppDoesNotProvideAnyInputMethod() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        PackageManager manager=context.getPackageManager();
        int flags=PackageManager.GET_SERVICES|PackageManager.GET_META_DATA|PackageManager.MATCH_DISABLED_COMPONENTS;
        PackageInfo info=manager.getPackageInfo(context.getPackageName(),flags);
        assertNotNull(info.services);
        for(ServiceInfo service:info.services){
            assertNotEquals("Removed keyboard class must not remain declared",
                context.getPackageName()+".ChristmasKeyboardService",service.name);
            assertNotEquals("No enabled or disabled IME may remain",
                "android.permission.BIND_INPUT_METHOD",service.permission);
            assertTrue("No keyboard metadata may remain",
                service.metaData==null||!service.metaData.containsKey("android.view.im"));
        }
        assertTrue("Android must not discover SnowGlobe as a keyboard",
            manager.queryIntentServices(new Intent("android.view.InputMethod")
                .setPackage(context.getPackageName()),flags).isEmpty());
    }

    @Test public void visualServicesKeepTheirSystemPermissionBoundaries() throws Exception {
        Context context=ApplicationProvider.getApplicationContext();
        PackageManager manager=context.getPackageManager();
        ServiceInfo wallpaper=manager.getServiceInfo(new ComponentName(context,SnowWallpaperService.class),0);
        assertTrue(wallpaper.enabled);assertTrue(wallpaper.exported);
        assertEquals("android.permission.BIND_WALLPAPER",wallpaper.permission);
        ServiceInfo tile=manager.getServiceInfo(new ComponentName(context,SnowGlobeTileService.class),0);
        assertTrue(tile.enabled);assertTrue(tile.exported);
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE",tile.permission);
        ServiceInfo overlay=manager.getServiceInfo(new ComponentName(context,EffectsService.class),0);
        assertTrue(overlay.enabled);assertFalse("External apps must not control the overlay service",overlay.exported);
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,overlay.getForegroundServiceType());
        String[] permissions=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS).requestedPermissions;
        assertNotNull(permissions);boolean billing=false;
        for(String permission:permissions){
            assertNotEquals("SnowGlobe must not acquire network access from an SDK",
                "android.permission.INTERNET",permission);
            assertNotEquals("SnowGlobe must not acquire network-state access from an SDK",
                "android.permission.ACCESS_NETWORK_STATE",permission);
            if("com.android.vending.BILLING".equals(permission))billing=true;
        }
        assertTrue("Purchases must retain the Play Billing permission",billing);
    }
}
