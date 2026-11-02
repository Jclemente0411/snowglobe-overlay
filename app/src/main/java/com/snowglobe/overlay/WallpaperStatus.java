package com.snowglobe.overlay;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.Context;
import android.os.Build;

/** Read-only selection check using public APIs, including shared Home/Lock engines. */
public final class WallpaperStatus {
    private WallpaperStatus() {}
    private static boolean ours(Context context,WallpaperInfo info) {
        return info!=null&&context.getPackageName().equals(info.getPackageName())
            &&SnowWallpaperService.class.getName().equals(info.getServiceName());
    }
    @android.annotation.TargetApi(34)
    public static boolean isAppliedToLockScreen(Context context) {
        if(Build.VERSION.SDK_INT<34)return false;
        WallpaperManager manager=WallpaperManager.getInstance(context);
        WallpaperInfo lock=manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK);
        if(lock!=null)return ours(context,lock);
        // Android removes the separate lock record when both screens use one engine.
        // A null WallpaperInfo alone is not proof: a separate static lock image must win.
        if(manager.getWallpaperId(WallpaperManager.FLAG_LOCK)>=0)return false;
        return ours(context,manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM));
    }
}
