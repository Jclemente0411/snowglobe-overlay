package com.snowglobe.overlay;

import java.util.Set;

/** Product boundaries only; purchase proof is owned by HolidayPlusEntitlement. */
public final class HolidayAccess {
    private static final Set<String> PLUS_SWITCHES=Set.of(
        "candy","gifts","trees","snowmen","bulbs","stars","confetti","screenSparkles");
    private static final Set<String> PLUS_NUMBERS=Set.of("particleDirection","particleRotation","sparkleDensity");
    private HolidayAccess() {}
    public static boolean plusSwitch(String key){return PLUS_SWITCHES.contains(key);}
    public static boolean plusNumber(String key){return PLUS_NUMBERS.contains(key);}
    public static boolean plusScene(String name){
        return "Snowy Village".equals(name)||"Vintage Christmas".equals(name)||"North Pole Party".equals(name);
    }
    public static boolean plusWallpaper(int design){return design>=2;}
}
