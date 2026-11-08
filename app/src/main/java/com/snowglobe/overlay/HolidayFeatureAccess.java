package com.snowglobe.overlay;

import android.content.Context;

/** The common feature gate. Complimentary access never changes verified purchase ownership. */
public final class HolidayFeatureAccess {
    private HolidayFeatureAccess() {}

    public static boolean hasAccess(Context context) {
        return HolidayPlusEntitlement.isOwned(context, BuildConfig.PLAY_BILLING_PUBLIC_KEY)
                || HolidayTrialAccess.snapshot(context).active;
    }
}
